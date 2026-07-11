package com.acorn.abc.book.service;

import com.acorn.abc.book.domain.Book;
import com.acorn.abc.book.domain.BookAuthor;
import com.acorn.abc.book.domain.BookDetail;
import com.acorn.abc.book.dto.BookCardResponse;
import com.acorn.abc.book.dto.BookCategoryResponse;
import com.acorn.abc.book.dto.BookDetailResponse;
import com.acorn.abc.book.dto.BookListCriteria;
import com.acorn.abc.book.dto.BookRecommendationResponse;
import com.acorn.abc.book.dto.BookRentalInfoResponse;
import com.acorn.abc.book.dto.BookReviewSummaryResponse;
import com.acorn.abc.book.dto.BookSearchCriteria;
import com.acorn.abc.book.dto.BookSearchResponse;
import com.acorn.abc.book.repository.BookAuthorRepository;
import com.acorn.abc.book.repository.BookCategoryRepository;
import com.acorn.abc.book.repository.BookDetailRepository;
import com.acorn.abc.book.repository.BookKeywordMapRepository;
import com.acorn.abc.book.repository.BookRepository;
import com.acorn.abc.book.repository.BookSpecifications;
import com.acorn.abc.common.exception.BusinessException;
import com.acorn.abc.common.exception.ErrorCode;
import com.acorn.abc.common.response.PageResponse;
import com.acorn.abc.favorite.repository.FavoriteBookRepository;
import com.acorn.abc.rental.domain.BookRental;
import com.acorn.abc.rental.repository.BookRentalRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class BookService {

    private static final String AVAILABLE = "AVAILABLE";
    private static final int DEFAULT_PAGE = 0;
    private static final int DEFAULT_SIZE = 20;
    private static final int MAX_SIZE = 100;
    private static final int DEFAULT_RECOMMENDATION_LIMIT = 10;
    private static final int MAX_RECOMMENDATION_LIMIT = 50;

    private final BookRepository bookRepository;
    private final BookAuthorRepository bookAuthorRepository;
    private final BookCategoryRepository bookCategoryRepository;
    private final BookKeywordMapRepository bookKeywordMapRepository;
    private final BookDetailRepository bookDetailRepository;
    private final FavoriteBookRepository favoriteBookRepository;
    private final BookRentalRepository bookRentalRepository;

    public PageResponse<BookCardResponse> getBooks(
            Long categoryId,
            Long parentCategoryId,
            Long collectionId,
            String rentalType,
            String section,
            String sort,
            Integer page,
            Integer size,
            Long memberId
    ) {
        BookListCriteria criteria = new BookListCriteria(
                categoryId,
                parentCategoryId,
                collectionId,
                normalizeRentalType(rentalType),
                normalizeText(section),
                normalizeSort(sort),
                normalizePage(page),
                normalizeSize(size)
        );

        Specification<Book> spec = BookSpecifications.available()
                .and(BookSpecifications.hasCategory(criteria.categoryId()))
                .and(BookSpecifications.hasParentCategory(criteria.parentCategoryId()))
                .and(BookSpecifications.inCollection(criteria.collectionId()))
                .and(BookSpecifications.inEventSection("event".equalsIgnoreCase(criteria.section())))
                .and(BookSpecifications.hasRentalType(criteria.rentalType()));

        Pageable pageable = PageRequest.of(criteria.page(), criteria.size(), sortFor(criteria.sort()));
        Page<Book> books = bookRepository.findAll(spec, pageable);
        return toPageResponse(books, memberId);
    }

    public BookSearchResponse searchBooks(String keyword, Long categoryId, String rentalType, String sort, Integer page, Integer size, Long memberId) {
        if (keyword == null || keyword.isBlank()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR);
        }
        String trimmedKeyword = keyword.trim();
        BookSearchCriteria criteria = new BookSearchCriteria(
                trimmedKeyword,
                categoryId,
                normalizeRentalType(rentalType),
                normalizeSort(sort),
                normalizePage(page),
                normalizeSize(size)
        );

        Pageable pageable = PageRequest.of(criteria.page(), criteria.size(), sortFor(criteria.sort()));
        Page<Book> result = bookRepository.search(
                "%" + criteria.keyword() + "%",
                criteria.categoryId(),
                criteria.rentalType(),
                pageable
        );
        PageResponse<BookCardResponse> resultPage = toPageResponse(result, memberId);
        return new BookSearchResponse(trimmedKeyword, resultPage.totalElements(), resultPage);
    }

    public BookDetailResponse getBookDetail(Long bookId, Long memberId) {
        Book book = getAvailableBookOrThrow(bookId);

        BookDetail detail = bookDetailRepository.findByBook_BookId(bookId).orElse(null);
        List<String> authors = bookAuthorRepository.findActiveByBookId(bookId).stream()
                .map(bookAuthor -> bookAuthor.getAuthor().getAuthorName())
                .toList();
        List<BookCategoryResponse> categories = bookCategoryRepository.findActiveByBookId(bookId).stream()
                .map(bookCategory -> new BookCategoryResponse(
                        bookCategory.getCategory().getCategoryId(),
                        bookCategory.getCategory().getParent() == null ? null : bookCategory.getCategory().getParent().getCategoryId(),
                        bookCategory.getCategory().getCategoryName(),
                        bookCategory.getCategory().getDisplayOrder()
                ))
                .toList();
        List<String> keywords = bookKeywordMapRepository.findActiveByBookId(bookId).stream()
                .map(bookKeywordMap -> bookKeywordMap.getKeyword().getKeywordName())
                .toList();

        BookRentalInfoResponse rentalInfo = new BookRentalInfoResponse(
                book.getRentalType(),
                book.getRentalPrice(),
                book.getDefaultRentalDays()
        );
        BookReviewSummaryResponse reviewSummary = new BookReviewSummaryResponse(
                defaultBigDecimal(book.getAverageRating()),
                book.getReviewCount()
        );

        String myRentalState = memberId == null ? null
                : bookRentalRepository.findFirstByMember_MemberIdAndBook_BookIdOrderByCreatedAtDescRentalIdDesc(memberId, bookId)
                        .map(BookRental::getRentalStatus)
                        .orElse(null);
        boolean myFavoriteYn = memberId != null
                && favoriteBookRepository.existsByMember_MemberIdAndBook_BookId(memberId, bookId);

        return new BookDetailResponse(
                book.getBookId(),
                book.getTitle(),
                book.getIsbn(),
                book.getCoverImageUrl(),
                book.getPubDate(),
                book.getPublisher() == null ? null : book.getPublisher().getPublisherName(),
                authors,
                categories,
                keywords,
                detail == null ? null : detail.getDescription(),
                detail == null ? null : detail.getTableOfContents(),
                detail == null ? null : detail.getPublisherReview(),
                rentalInfo,
                book.getTotalPages(),
                reviewSummary,
                myRentalState,
                myFavoriteYn
        );
    }

    public BookRecommendationResponse getRecommendations(Long baseBookId, String type, Integer limit, Long memberId) {
        // 회원별 "관심 카테고리 중심" 개인화(화면흐름도 02-User-상세화면설계도.md:47, U-001 메인/홈)는
        // "관심 카테고리"를 어떤 데이터로 정의할지(즐겨찾기 기준? 최근 대여 기준?) api-spec/화면흐름도/ERD 어디에도 정의가 없어
        // 구현하지 않음(2026-07-02_화면흐름도-API명세_4.2_대조검토.md 4-2 참고). 회원/비회원 모두 동일한 추천을 받지만,
        // favoriteYn은 BookCard 계약대로 로그인 회원 기준으로 계산한다.
        String recommendationType = normalizeRecommendationType(type, baseBookId);
        int normalizedLimit = normalizeRecommendationLimit(limit);
        Pageable pageable = PageRequest.of(0, normalizedLimit);
        List<Book> books = bookRepository.findRecommendations(baseBookId, recommendationType, pageable);
        return new BookRecommendationResponse(recommendationType, toCardResponses(books, memberId));
    }

    public List<BookCardResponse> findCardsByCollectionId(Long collectionId, Integer limit) {
        Pageable pageable = limit == null ? Pageable.unpaged() : PageRequest.of(0, limit);
        List<Book> books = bookRepository.findByCollectionId(collectionId, pageable);
        return toCardResponses(books, null);
    }

    // 다른 도메인(library 등)이 도서 정보가 필요할 때 BookRepository 대신 이 메서드를 거치도록 한다(AVAILABLE 규칙을 한 곳에서만 관리)
    public Book getAvailableBookOrThrow(Long bookId) {
        return bookRepository.findById(bookId)
                .filter(candidate -> AVAILABLE.equals(candidate.getStatus()))
                .orElseThrow(() -> new BusinessException(ErrorCode.BOOK_NOT_FOUND));
    }

    private PageResponse<BookCardResponse> toPageResponse(Page<Book> page, Long memberId) {
        List<BookCardResponse> content = toCardResponses(page.getContent(), memberId);
        return PageResponse.of(content, page.getNumber(), page.getSize(), page.getTotalElements());
    }

    private List<BookCardResponse> toCardResponses(List<Book> books, Long memberId) {
        if (books.isEmpty()) {
            return List.of();
        }
        List<Long> bookIds = books.stream().map(Book::getBookId).toList();
        Map<Long, List<String>> authorsByBookId = new LinkedHashMap<>();
        for (BookAuthor bookAuthor : bookAuthorRepository.findActiveByBookIds(bookIds)) {
            authorsByBookId.computeIfAbsent(bookAuthor.getBook().getBookId(), id -> new ArrayList<>())
                    .add(bookAuthor.getAuthor().getAuthorName());
        }
        Set<Long> favoriteBookIds = memberId == null
                ? Set.of()
                : Set.copyOf(favoriteBookRepository.findBookIdsByMemberIdAndBookIdIn(memberId, bookIds));
        return books.stream()
                .map(book -> new BookCardResponse(
                        book.getBookId(),
                        book.getTitle(),
                        book.getCoverImageUrl(),
                        authorsByBookId.getOrDefault(book.getBookId(), List.of()),
                        book.getPublisher() == null ? null : book.getPublisher().getPublisherName(),
                        book.getRentalType(),
                        book.getRentalPrice(),
                        defaultBigDecimal(book.getAverageRating()),
                        book.getReviewCount(),
                        favoriteBookIds.contains(book.getBookId())
                ))
                .toList();
    }

    private Sort sortFor(String sort) {
        if ("title".equals(sort)) {
            return Sort.by(Sort.Order.asc("title"), Sort.Order.asc("bookId"));
        }
        if ("popular".equals(sort)) {
            return Sort.by(Sort.Order.desc("rentalCount"), Sort.Order.desc("reviewCount"), Sort.Order.desc("bookId"));
        }
        if ("rating".equals(sort)) {
            return Sort.by(Sort.Order.desc("averageRating"), Sort.Order.desc("reviewCount"), Sort.Order.desc("bookId"));
        }
        if ("reviews".equals(sort)) {
            return Sort.by(Sort.Order.desc("reviewCount"), Sort.Order.desc("bookId"));
        }
        return Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("bookId"));
    }

    private BigDecimal defaultBigDecimal(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private String normalizeRentalType(String rentalType) {
        String value = normalizeUpper(rentalType);
        if (value == null) {
            return null;
        }
        if (!"FREE".equals(value) && !"PAID".equals(value)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR);
        }
        return value;
    }

    private String normalizeSort(String sort) {
        String value = normalizeText(sort);
        if (value == null) {
            return "latest";
        }
        // 잘못된 sort 값은 latest로 조용히 넘기지 않고 VALIDATION_ERROR로 응답한다(normalizeRentalType과 동일 패턴, 계획 2-5)
        if (!"latest".equals(value) && !"title".equals(value) && !"popular".equals(value)
                && !"rating".equals(value) && !"reviews".equals(value)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR);
        }
        return value;
    }

    private String normalizeRecommendationType(String type, Long baseBookId) {
        if (baseBookId == null) {
            return "MAIN";
        }
        String value = normalizeUpper(type);
        if (value == null || "MAIN".equals(value)) {
            return "MAIN";
        }
        value = value.replace("SAME_", "");
        if (!"AUTHOR".equals(value) && !"CATEGORY".equals(value) && !"KEYWORD".equals(value)) {
            return "MAIN";
        }
        return value;
    }

    private int normalizeRecommendationLimit(Integer limit) {
        if (limit == null || limit <= 0) {
            return DEFAULT_RECOMMENDATION_LIMIT;
        }
        return Math.min(limit, MAX_RECOMMENDATION_LIMIT);
    }

    private int normalizePage(Integer page) {
        if (page == null || page < 0) {
            return DEFAULT_PAGE;
        }
        return page;
    }

    private int normalizeSize(Integer size) {
        if (size == null || size <= 0) {
            return DEFAULT_SIZE;
        }
        return Math.min(size, MAX_SIZE);
    }

    private String normalizeUpper(String value) {
        String normalized = normalizeText(value);
        return normalized == null ? null : normalized.toUpperCase(Locale.ROOT);
    }

    private String normalizeText(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
