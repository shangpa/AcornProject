package com.acorn.abc.book.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "author")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Author {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "author_id")
    private Long authorId;

    @Column(name = "author_name", nullable = false, length = 100)
    private String authorName;

    @Column(name = "author_intro", columnDefinition = "TEXT")
    private String authorIntro;

    @Column(name = "status", nullable = false, length = 20)
    private String status;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    public static Author create(String authorName) {
        Author author = new Author();
        author.authorName = authorName;
        author.status = "ACTIVE";
        author.createdAt = LocalDateTime.now();
        return author;
    }
}
