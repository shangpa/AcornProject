package com.acorn.abc.book.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "book")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Book {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "book_id")
    private Long bookId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "publisher_id")
    private Publisher publisher;

    @Column(name = "title", nullable = false, length = 255)
    private String title;

    @Column(name = "isbn", length = 13)
    private String isbn;

    @Column(name = "cover_image_url", length = 500)
    private String coverImageUrl;

    @Column(name = "pub_date")
    private LocalDate pubDate;

    @Column(name = "rental_type", nullable = false, length = 20)
    private String rentalType;

    @Column(name = "rental_price", nullable = false)
    private int rentalPrice;

    @Column(name = "default_rental_days", nullable = false)
    private int defaultRentalDays;

    @Column(name = "total_pages", nullable = false)
    private int totalPages;

    @Column(name = "rental_count", nullable = false)
    private int rentalCount;

    @Column(name = "average_rating", nullable = false, precision = 3, scale = 2)
    private BigDecimal averageRating;

    @Column(name = "review_count", nullable = false)
    private int reviewCount;

    @Column(name = "status", nullable = false, length = 20)
    private String status;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    public static Book create(Publisher publisher, String title, String isbn, String coverImageUrl,
                              LocalDate pubDate, String rentalType, int rentalPrice,
                              int defaultRentalDays, int totalPages, String status) {
        Book book = new Book();
        book.publisher = publisher;
        book.title = title;
        book.isbn = isbn;
        book.coverImageUrl = coverImageUrl;
        book.pubDate = pubDate;
        book.rentalType = rentalType;
        book.rentalPrice = rentalPrice;
        book.defaultRentalDays = defaultRentalDays;
        book.totalPages = totalPages;
        book.rentalCount = 0;
        book.averageRating = BigDecimal.ZERO;
        book.reviewCount = 0;
        book.status = status;
        book.createdAt = LocalDateTime.now();
        return book;
    }
}
