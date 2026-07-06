package com.acorn.abc.rental.domain;

import com.acorn.abc.book.domain.Book;
import com.acorn.abc.member.domain.Member;
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
@Table(name = "book_rental")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class BookRental {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "rental_id")
    private Long rentalId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_id")
    private Member member;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "book_id")
    private Book book;

    @Column(name = "rental_start_at")
    private LocalDateTime rentalStartAt;

    @Column(name = "rental_end_at", nullable = false)
    private LocalDateTime rentalEndAt;

    @Column(name = "rental_status", nullable = false, length = 20)
    private String rentalStatus;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    // 무료/유료 대여 공용 팩토리. 항상 READY 상태로 생성, rental_start_at은 뷰어 최초 진입 시 저장한다
    public static BookRental create(Member member, Book book, LocalDateTime rentalEndAt) {
        BookRental rental = new BookRental();
        rental.member = member;
        rental.book = book;
        rental.rentalEndAt = rentalEndAt;
        rental.rentalStatus = "READY";
        rental.createdAt = LocalDateTime.now();
        return rental;
    }

    // 뷰어 최초 진입 시에만 READY->READING 전환 + rental_start_at 저장(이미 시작한 대여는 그대로 둔다)
    public void startReadingIfNotStarted() {
        if ("READY".equals(this.rentalStatus)) {
            this.rentalStatus = "READING";
            this.rentalStartAt = LocalDateTime.now();
        }
    }
}
