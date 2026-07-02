package com.acorn.abc.book.repository;

import com.acorn.abc.book.domain.Author;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AuthorRepository extends JpaRepository<Author, Long> {

    Optional<Author> findFirstByAuthorNameOrderByAuthorIdAsc(String authorName);
}
