package com.acorn.abc.book.domain;

import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapsId;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "book_category")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class BookCategory {

    @EmbeddedId
    private BookCategoryId id;

    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("bookId")
    @JoinColumn(name = "book_id")
    private Book book;

    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("categoryId")
    @JoinColumn(name = "category_id")
    private Category category;

    public static BookCategory create(Book book, Category category) {
        BookCategory bookCategory = new BookCategory();
        bookCategory.id = new BookCategoryId(book.getBookId(), category.getCategoryId());
        bookCategory.book = book;
        bookCategory.category = category;
        return bookCategory;
    }
}
