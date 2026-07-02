package com.acorn.abc.batch.repository;

import com.acorn.abc.batch.domain.BookIsbnTemp;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface BookIsbnTempRepository extends JpaRepository<BookIsbnTemp, Long> {

    Optional<BookIsbnTemp> findByIsbn13(String isbn13);

    Page<BookIsbnTemp> findByStatusCd(byte statusCd, Pageable pageable);
}
