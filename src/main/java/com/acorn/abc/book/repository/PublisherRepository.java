package com.acorn.abc.book.repository;

import com.acorn.abc.book.domain.Publisher;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PublisherRepository extends JpaRepository<Publisher, Long> {

    Optional<Publisher> findByPublisherName(String publisherName);
}
