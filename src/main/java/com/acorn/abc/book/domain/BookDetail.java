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

import java.time.LocalDateTime;

@Entity
@Table(name = "book_detail")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class BookDetail {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "book_detail_id")
    private Long bookDetailId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "book_id", nullable = false)
    private Book book;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "table_of_contents", columnDefinition = "TEXT")
    private String tableOfContents;

    @Column(name = "publisher_review", columnDefinition = "TEXT")
    private String publisherReview;

    @Column(name = "detail_content", columnDefinition = "TEXT")
    private String detailContent;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    public static BookDetail create(Book book, String description, String tableOfContents,
                                    String publisherReview, String detailContent) {
        BookDetail detail = new BookDetail();
        detail.book = book;
        detail.description = description;
        detail.tableOfContents = tableOfContents;
        detail.publisherReview = publisherReview;
        detail.detailContent = detailContent;
        detail.createdAt = LocalDateTime.now();
        return detail;
    }
}
