package com.acorn.abc.payment.domain;

import com.acorn.abc.member.domain.Member;
import com.acorn.abc.rental.domain.BookRental;
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
@Table(name = "payment")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Payment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "payment_id")
    private Long paymentId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_id", nullable = false)
    private Member member;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "rental_id", nullable = false)
    private BookRental rental;

    @Column(name = "payment_type", nullable = false, length = 20)
    private String paymentType;

    @Column(name = "original_amount", nullable = false)
    private int originalAmount;

    @Column(name = "amount", nullable = false)
    private int amount;

    @Column(name = "payment_method", nullable = false, length = 20)
    private String paymentMethod;

    @Column(name = "payment_status", nullable = false, length = 20)
    private String paymentStatus;

    @Column(name = "paid_at", nullable = false)
    private LocalDateTime paidAt;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    public static Payment create(Member member, BookRental rental, int originalAmount, int amount, LocalDateTime paidAt) {
        Payment payment = new Payment();
        payment.member = member;
        payment.rental = rental;
        payment.paymentType = "RENTAL";
        payment.originalAmount = originalAmount;
        payment.amount = amount;
        payment.paymentMethod = "CARD";
        payment.paymentStatus = "PAID";
        payment.paidAt = paidAt;
        payment.createdAt = paidAt;
        return payment;
    }
}
