package com.acorn.abc.book.repository;

import com.acorn.abc.book.domain.BookPage;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BookPageRepository extends JpaRepository<BookPage, Long> {
}
