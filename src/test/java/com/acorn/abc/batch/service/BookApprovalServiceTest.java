package com.acorn.abc.batch.service;

import com.acorn.abc.admin.book.dto.BookIsbnTempApproveRequest;
import com.acorn.abc.admin.book.dto.BookIsbnTempApproveResponse;
import com.acorn.abc.batch.domain.BookIsbnTemp;
import com.acorn.abc.batch.repository.BookIsbnTempRepository;
import com.acorn.abc.book.domain.Book;
import com.acorn.abc.book.repository.AuthorRepository;
import com.acorn.abc.book.repository.BookAuthorRepository;
import com.acorn.abc.book.repository.BookCategoryRepository;
import com.acorn.abc.book.repository.BookDetailRepository;
import com.acorn.abc.book.repository.BookPageRepository;
import com.acorn.abc.book.repository.BookRepository;
import com.acorn.abc.book.repository.PublisherRepository;
import com.acorn.abc.category.repository.CategoryRepository;
import com.acorn.abc.category.service.AladinCategoryImportService;
import com.acorn.abc.category.service.AladinCategoryUpsertService;
import com.acorn.abc.common.exception.BusinessException;
import com.acorn.abc.common.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@ActiveProfiles("test")
class BookApprovalServiceTest {

    @Autowired private TestEntityManager entityManager;
    @Autowired private BookIsbnTempRepository bookIsbnTempRepository;
    @Autowired private BookRepository bookRepository;
    @Autowired private PublisherRepository publisherRepository;
    @Autowired private AuthorRepository authorRepository;
    @Autowired private BookAuthorRepository bookAuthorRepository;
    @Autowired private BookDetailRepository bookDetailRepository;
    @Autowired private BookPageRepository bookPageRepository;
    @Autowired private BookCategoryRepository bookCategoryRepository;
    @Autowired private CategoryRepository categoryRepository;

    private BookApprovalService service;
    private Long categoryId;

    @BeforeEach
    void setUp() {
        AladinCategoryImportService aladinCategoryImportService =
                new AladinCategoryImportService(new AladinCategoryUpsertService(categoryRepository));
        service = new BookApprovalService(
                bookIsbnTempRepository, bookRepository, publisherRepository, authorRepository,
                bookAuthorRepository, bookDetailRepository, bookPageRepository,
                bookCategoryRepository, categoryRepository, aladinCategoryImportService);
        categoryId = seedCategory("소설");
    }

    @Test
    void approve_paidBook_setsDefaults() {
        Long tempId = seedReadyTemp("9788966260959", "2013-12-24");

        BookIsbnTempApproveResponse response = service.approve(tempId, request(
                "PAID", 5000, List.of(new BookIsbnTempApproveRequest.Page(1, "p1"),
                        new BookIsbnTempApproveRequest.Page(2, "p2"))));
        entityManager.flush();

        Book book = bookRepository.findById(response.bookId()).orElseThrow();
        assertThat(book.getRentalType()).isEqualTo("PAID");
        assertThat(book.getDefaultRentalDays()).isEqualTo(14);
        assertThat(book.getRentalPrice()).isEqualTo(5000);
        assertThat(book.getTotalPages()).isEqualTo(2);
        assertThat(book.getRentalCount()).isZero();
        assertThat(book.getReviewCount()).isZero();
        assertThat(book.getAverageRating()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(book.getStatus()).isEqualTo("AVAILABLE");

        BookIsbnTemp temp = bookIsbnTempRepository.findById(tempId).orElseThrow();
        assertThat(temp.getStatusCd()).isEqualTo(BookIsbnTemp.STATUS_MERGED);
        assertThat(bookDetailRepository.findByBook_BookId(book.getBookId())).isPresent();
        assertThat(bookAuthorRepository.findActiveByBookId(book.getBookId())).hasSize(2);
        assertThat(bookCategoryRepository.findActiveByBookId(book.getBookId())).hasSize(1);
    }

    @Test
    void approve_freeBook_usesSevenRentalDays() {
        Long tempId = seedReadyTemp("9788966260960", "20131224");

        BookIsbnTempApproveResponse response = service.approve(tempId, request("FREE", 0, List.of()));
        entityManager.flush();

        Book book = bookRepository.findById(response.bookId()).orElseThrow();
        assertThat(book.getDefaultRentalDays()).isEqualTo(7);
        assertThat(book.getRentalPrice()).isZero();
    }

    @Test
    void approve_emptyPages_totalPagesZeroAndNoRows() {
        Long tempId = seedReadyTemp("9788966260961", null);

        BookIsbnTempApproveResponse response = service.approve(tempId, request("PAID", 5000, List.of()));
        entityManager.flush();

        Book book = bookRepository.findById(response.bookId()).orElseThrow();
        assertThat(book.getTotalPages()).isZero();
        assertThat(bookPageRepository.count()).isZero();
    }

    @Test
    void approve_notReadyTemp_throwsConflict() {
        BookIsbnTemp temp = BookIsbnTemp.create("9788966260962");
        temp.applyMetadata(null, null, null, null, null, null, null, null, (byte) 0, (byte) 0); // stays PENDING
        Long tempId = bookIsbnTempRepository.save(temp).getId();

        assertThatThrownBy(() -> service.approve(tempId, request("PAID", 5000, List.of())))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.BOOK_ISBN_TEMP_NOT_READY);
    }

    @Test
    void approve_mergedTemp_throwsConflict() {
        BookIsbnTemp temp = BookIsbnTemp.create("9788966260963");
        temp.applyMetadata("T", "P", "A", null, null, null, null, null, (byte) 1, (byte) 1);
        temp.markMerged();
        Long tempId = bookIsbnTempRepository.save(temp).getId();

        assertThatThrownBy(() -> service.approve(tempId, request("PAID", 5000, List.of())))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.BOOK_ISBN_TEMP_ALREADY_MERGED);
    }

    @Test
    void approve_existingIsbn_throwsConflict() {
        String isbn = "9788966260964";
        seedBook(isbn);
        Long tempId = seedReadyTemp(isbn, null);

        assertThatThrownBy(() -> service.approve(tempId, request("PAID", 5000, List.of())))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.BOOK_ISBN_ALREADY_EXISTS);
    }

    @Test
    void approve_duplicatePageNo_throwsValidationAndCreatesNoBook() {
        Long tempId = seedReadyTemp("9788966260965", null);

        assertThatThrownBy(() -> service.approve(tempId, request("PAID", 5000,
                List.of(new BookIsbnTempApproveRequest.Page(1, "a"),
                        new BookIsbnTempApproveRequest.Page(1, "b")))))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.VALIDATION_ERROR);

        assertThat(bookRepository.findByIsbn("9788966260965")).isEmpty();
    }

    @Test
    void approve_unknownCategory_throwsNotFound() {
        Long tempId = seedReadyTemp("9788966260966", null);

        BookIsbnTempApproveRequest req = new BookIsbnTempApproveRequest(
                "제목", "출판사", List.of("저자"), List.of(999999L),
                "PAID", 5000, "AVAILABLE", null, List.of());

        assertThatThrownBy(() -> service.approve(tempId, req))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.CATEGORY_NOT_FOUND);
    }

    @Test
    void approve_noCategoryIds_autoImportsAladinCategory() {
        Long tempId = seedReadyTemp("9788966260967", null, "2724",
                "국내도서 > 컴퓨터/모바일 > 프로그래밍 > 개발방법론");

        BookIsbnTempApproveRequest req = requestWithCategoryIds(List.of(), "PAID", 5000, List.of());

        BookIsbnTempApproveResponse response = service.approve(tempId, req);
        entityManager.flush();

        assertThat(bookCategoryRepository.findActiveByBookId(response.bookId())).hasSize(1);
        var imported = categoryRepository.findByAladinCategoryId("2724");
        assertThat(imported).isPresent();
        assertThat(imported.get().getCategoryName()).isEqualTo("개발방법론");
        assertThat(imported.get().getParent().getCategoryName()).isEqualTo("프로그래밍");
    }

    @Test
    void approve_noCategoryIdsAndNoAladinInfo_throwsValidation() {
        Long tempId = seedReadyTemp("9788966260968", null, null, null);

        BookIsbnTempApproveRequest req = requestWithCategoryIds(List.of(), "PAID", 5000, List.of());

        assertThatThrownBy(() -> service.approve(tempId, req))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.VALIDATION_ERROR);

        assertThat(bookRepository.findByIsbn("9788966260968")).isEmpty();
    }

    private BookIsbnTempApproveRequest request(String rentalType, int rentalPrice,
                                               List<BookIsbnTempApproveRequest.Page> pages) {
        return requestWithCategoryIds(List.of(categoryId), rentalType, rentalPrice, pages);
    }

    private BookIsbnTempApproveRequest requestWithCategoryIds(List<Long> categoryIds, String rentalType,
                                                               int rentalPrice,
                                                               List<BookIsbnTempApproveRequest.Page> pages) {
        return new BookIsbnTempApproveRequest(
                "클린코드", "인사이트", List.of("로버트 마틴", "이해영"), categoryIds,
                rentalType, rentalPrice, "AVAILABLE",
                new BookIsbnTempApproveRequest.Detail("설명", null, null, null), pages);
    }

    private Long seedReadyTemp(String isbn13, String pubDate) {
        return seedReadyTemp(isbn13, pubDate, "2724", "국내도서 > 컴퓨터/모바일 > 프로그래밍 > 개발방법론");
    }

    private Long seedReadyTemp(String isbn13, String pubDate, String aladinCategoryId, String aladinCategoryName) {
        BookIsbnTemp temp = BookIsbnTemp.create(isbn13);
        temp.applyMetadata("클린코드", "인사이트", "로버트 마틴", "http://cover", "설명", pubDate,
                aladinCategoryId, aladinCategoryName, (byte) 1, (byte) 1);
        temp.markReady();
        return bookIsbnTempRepository.save(temp).getId();
    }

    private Long seedCategory(String name) {
        entityManager.getEntityManager()
                .createNativeQuery("INSERT INTO category (category_name, display_order, status) VALUES (?, 0, 'ACTIVE')")
                .setParameter(1, name)
                .executeUpdate();
        return ((Number) entityManager.getEntityManager()
                .createNativeQuery("SELECT MAX(category_id) FROM category")
                .getSingleResult()).longValue();
    }

    private void seedBook(String isbn) {
        entityManager.getEntityManager()
                .createNativeQuery("INSERT INTO book (title, isbn, rental_type, rental_price, default_rental_days, " +
                        "total_pages, rental_count, average_rating, review_count, status, created_at) " +
                        "VALUES ('기존도서', ?, 'PAID', 5000, 14, 0, 0, 0.00, 0, 'AVAILABLE', ?)")
                .setParameter(1, isbn)
                .setParameter(2, LocalDateTime.now())
                .executeUpdate();
    }
}
