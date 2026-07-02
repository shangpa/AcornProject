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
@Table(name = "publisher")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Publisher {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "publisher_id")
    private Long publisherId;

    @Column(name = "publisher_name", nullable = false, length = 100)
    private String publisherName;

    @Column(name = "status", nullable = false, length = 20)
    private String status;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    public static Publisher create(String publisherName) {
        Publisher publisher = new Publisher();
        publisher.publisherName = publisherName;
        publisher.status = "ACTIVE";
        publisher.createdAt = LocalDateTime.now();
        return publisher;
    }
}
