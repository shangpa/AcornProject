package com.acorn.abc.rental.repository;

import com.acorn.abc.rental.domain.BookRental;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface BookRentalRepository extends JpaRepository<BookRental, Long> {

    // book_rental은 (member_id, book_id) UNIQUE 제약이 없어 재대여 시 행이 여러 개 쌓일 수 있다 -> 최신 1건만 조회
    // createdAt이 동률일 수 있어 rentalId desc를 tie-breaker로 추가해 조회 결과를 결정적으로 만든다
    Optional<BookRental> findFirstByMember_MemberIdAndBook_BookIdOrderByCreatedAtDescRentalIdDesc(Long memberId, Long bookId);

    @Query(value = "SELECT r FROM BookRental r JOIN FETCH r.book " +
            "WHERE r.member.memberId = :memberId AND (:status IS NULL OR r.rentalStatus = :status) " +
            "ORDER BY r.createdAt DESC, r.rentalId DESC",
            countQuery = "SELECT COUNT(r) FROM BookRental r " +
            "WHERE r.member.memberId = :memberId AND (:status IS NULL OR r.rentalStatus = :status)")
    Page<BookRental> findMyRentals(@Param("memberId") Long memberId, @Param("status") String status, Pageable pageable);
}
