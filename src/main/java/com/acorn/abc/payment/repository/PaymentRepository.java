package com.acorn.abc.payment.repository;

import com.acorn.abc.payment.domain.Payment;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;

public interface PaymentRepository extends JpaRepository<Payment, Long> {

    @Query(value = "SELECT p FROM Payment p JOIN FETCH p.rental r JOIN FETCH r.book " +
            "WHERE p.member.memberId = :memberId " +
            "AND (:from IS NULL OR p.paidAt >= :from) " +
            "AND (:to IS NULL OR p.paidAt < :to) " +
            "ORDER BY p.paidAt DESC",
            countQuery = "SELECT COUNT(p) FROM Payment p " +
            "WHERE p.member.memberId = :memberId " +
            "AND (:from IS NULL OR p.paidAt >= :from) " +
            "AND (:to IS NULL OR p.paidAt < :to)")
    Page<Payment> findMyPayments(
            @Param("memberId") Long memberId,
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to,
            Pageable pageable
    );
}
