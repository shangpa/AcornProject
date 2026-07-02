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
@Table(name = "book_page")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class BookPage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "book_page_id")
    private Long bookPageId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "book_id", nullable = false)
    private Book book;

    @Column(name = "page_no", nullable = false)
    private int pageNo;

    @Column(name = "page_content", nullable = false, columnDefinition = "LONGTEXT")
    private String pageContent;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    public static BookPage create(Book book, int pageNo, String pageContent) {
        BookPage page = new BookPage();
        page.book = book;
        page.pageNo = pageNo;
        page.pageContent = pageContent;
        page.createdAt = LocalDateTime.now();
        return page;
    }
}
