package com.acorn.abc.book.domain;

import jakarta.persistence.Column;
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
@Table(name = "book_author")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class BookAuthor {

    @EmbeddedId
    private BookAuthorId id;

    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("bookId")
    @JoinColumn(name = "book_id")
    private Book book;

    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("authorId")
    @JoinColumn(name = "author_id")
    private Author author;

    @Column(name = "author_order", nullable = false)
    private int authorOrder;

    public static BookAuthor create(Book book, Author author, int authorOrder) {
        BookAuthor bookAuthor = new BookAuthor();
        bookAuthor.id = new BookAuthorId(book.getBookId(), author.getAuthorId());
        bookAuthor.book = book;
        bookAuthor.author = author;
        bookAuthor.authorOrder = authorOrder;
        return bookAuthor;
    }
}
