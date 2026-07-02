package com.acorn.abc.batch.service;

import com.acorn.abc.admin.book.dto.BookIsbnTempApproveRequest;
import com.acorn.abc.admin.book.dto.BookIsbnTempApproveResponse;
import com.acorn.abc.batch.domain.BookIsbnTemp;
import com.acorn.abc.batch.repository.BookIsbnTempRepository;
import com.acorn.abc.book.domain.Author;
import com.acorn.abc.book.domain.Book;
import com.acorn.abc.book.domain.BookAuthor;
import com.acorn.abc.book.domain.BookCategory;
import com.acorn.abc.book.domain.BookDetail;
import com.acorn.abc.book.domain.BookPage;
import com.acorn.abc.book.domain.Category;
import com.acorn.abc.book.domain.Publisher;
import com.acorn.abc.book.repository.AuthorRepository;
import com.acorn.abc.book.repository.BookAuthorRepository;
import com.acorn.abc.book.repository.BookCategoryRepository;
import com.acorn.abc.book.repository.BookDetailRepository;
import com.acorn.abc.book.repository.BookPageRepository;
import com.acorn.abc.book.repository.BookRepository;
import com.acorn.abc.book.repository.PublisherRepository;
import com.acorn.abc.category.repository.CategoryRepository;
import com.acorn.abc.category.service.AladinCategoryImportService;
import com.acorn.abc.common.exception.BusinessException;
import com.acorn.abc.common.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * temp 승인 후 실제 book 및 연결 테이블 생성 (단일 트랜잭션).
 * 중간 실패 시 book 관련 row와 temp 상태 변경이 함께 rollback된다.
 */
@Service
@RequiredArgsConstructor
public class BookApprovalService {

    private static final int PAID_RENTAL_DAYS = 14;
    private static final int FREE_RENTAL_DAYS = 7;

    private final BookIsbnTempRepository bookIsbnTempRepository;
    private final BookRepository bookRepository;
    private final PublisherRepository publisherRepository;
    private final AuthorRepository authorRepository;
    private final BookAuthorRepository bookAuthorRepository;
    private final BookDetailRepository bookDetailRepository;
    private final BookPageRepository bookPageRepository;
    private final BookCategoryRepository bookCategoryRepository;
    private final CategoryRepository categoryRepository;
    private final AladinCategoryImportService aladinCategoryImportService;

    @Transactional
    public BookIsbnTempApproveResponse approve(Long tempId, BookIsbnTempApproveRequest request) {
        BookIsbnTemp temp = bookIsbnTempRepository.findById(tempId)
                .orElseThrow(() -> new BusinessException(ErrorCode.BOOK_ISBN_TEMP_NOT_FOUND));

        if (temp.getStatusCd() == BookIsbnTemp.STATUS_MERGED) {
            throw new BusinessException(ErrorCode.BOOK_ISBN_TEMP_ALREADY_MERGED);
        }
        if (temp.getStatusCd() != BookIsbnTemp.STATUS_READY) {
            throw new BusinessException(ErrorCode.BOOK_ISBN_TEMP_NOT_READY);
        }
        if (bookRepository.existsByIsbn(temp.getIsbn13())) {
            throw new BusinessException(ErrorCode.BOOK_ISBN_ALREADY_EXISTS);
        }

        validateRentalPrice(request.rentalType(), request.rentalPrice());
        List<BookIsbnTempApproveRequest.Page> pages = validateAndGetPages(request.pages());
        List<Long> categoryIds = resolveCategoryIds(request.categoryIds(), temp);

        Publisher publisher = publisherRepository.findByPublisherName(request.publisherName())
                .orElseGet(() -> publisherRepository.save(Publisher.create(request.publisherName())));

        int defaultRentalDays = "FREE".equals(request.rentalType()) ? FREE_RENTAL_DAYS : PAID_RENTAL_DAYS;
        int totalPages = pages.stream().mapToInt(BookIsbnTempApproveRequest.Page::pageNo).max().orElse(0);
        LocalDate pubDate = parsePubDate(temp.getPubDate());

        Book book = bookRepository.save(Book.create(
                publisher,
                request.title(),
                temp.getIsbn13(),
                temp.getCoverImageUrl(),
                pubDate,
                request.rentalType(),
                request.rentalPrice(),
                defaultRentalDays,
                totalPages,
                request.status()
        ));

        saveDetail(book, request.detail());
        savePages(book, pages);
        saveCategories(book, categoryIds);
        saveAuthors(book, request.authors());

        temp.markMerged();

        return new BookIsbnTempApproveResponse(book.getBookId(), temp.getId(), book.getStatus());
    }

    private void validateRentalPrice(String rentalType, int rentalPrice) {
        if ("FREE".equals(rentalType) && rentalPrice != 0) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR);
        }
        if ("PAID".equals(rentalType) && rentalPrice <= 0) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR);
        }
    }

    private List<BookIsbnTempApproveRequest.Page> validateAndGetPages(List<BookIsbnTempApproveRequest.Page> pages) {
        if (pages == null) {
            return List.of();
        }
        Set<Integer> pageNos = new HashSet<>();
        for (BookIsbnTempApproveRequest.Page page : pages) {
            if (!pageNos.add(page.pageNo())) {
                throw new BusinessException(ErrorCode.VALIDATION_ERROR);
            }
        }
        return pages;
    }

    private void saveDetail(Book book, BookIsbnTempApproveRequest.Detail detail) {
        BookDetail bookDetail = (detail == null)
                ? BookDetail.create(book, null, null, null, null)
                : BookDetail.create(book, detail.description(), detail.tableOfContents(),
                        detail.publisherReview(), detail.detailContent());
        bookDetailRepository.save(bookDetail);
    }

    private void savePages(Book book, List<BookIsbnTempApproveRequest.Page> pages) {
        List<BookPage> bookPages = new ArrayList<>();
        for (BookIsbnTempApproveRequest.Page page : pages) {
            bookPages.add(BookPage.create(book, page.pageNo(), page.pageContent()));
        }
        bookPageRepository.saveAll(bookPages);
    }

    private List<Long> resolveCategoryIds(List<Long> requestedCategoryIds, BookIsbnTemp temp) {
        if (requestedCategoryIds != null && !requestedCategoryIds.isEmpty()) {
            return requestedCategoryIds;
        }
        return aladinCategoryImportService
                .importOrGet(temp.getAladinCategoryId(), temp.getAladinCategoryName())
                .map(List::of)
                .orElseThrow(() -> new BusinessException(ErrorCode.VALIDATION_ERROR));
    }

    private void saveCategories(Book book, List<Long> categoryIds) {
        Set<Long> uniqueIds = new LinkedHashSet<>(categoryIds);
        List<BookCategory> bookCategories = new ArrayList<>();
        for (Long categoryId : uniqueIds) {
            Category category = categoryRepository.findById(categoryId)
                    .orElseThrow(() -> new BusinessException(ErrorCode.CATEGORY_NOT_FOUND));
            bookCategories.add(BookCategory.create(book, category));
        }
        bookCategoryRepository.saveAll(bookCategories);
    }

    private void saveAuthors(Book book, List<String> authorNames) {
        Set<String> uniqueNames = new LinkedHashSet<>(authorNames);
        List<BookAuthor> bookAuthors = new ArrayList<>();
        int order = 1;
        for (String authorName : uniqueNames) {
            Author author = authorRepository.findFirstByAuthorNameOrderByAuthorIdAsc(authorName)
                    .orElseGet(() -> authorRepository.save(Author.create(authorName)));
            bookAuthors.add(BookAuthor.create(book, author, order++));
        }
        bookAuthorRepository.saveAll(bookAuthors);
    }

    private LocalDate parsePubDate(String pubDate) {
        if (pubDate == null || pubDate.isBlank()) {
            return null;
        }
        String value = pubDate.trim();
        try {
            if (value.matches("\\d{4}-\\d{2}-\\d{2}")) {
                return LocalDate.parse(value);
            }
            if (value.matches("\\d{8}")) {
                return LocalDate.parse(value, DateTimeFormatter.ofPattern("yyyyMMdd"));
            }
            if (value.matches("\\d{4}")) {
                return LocalDate.of(Integer.parseInt(value), 1, 1);
            }
        } catch (Exception e) {
            return null;
        }
        return null;
    }
}
