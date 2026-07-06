package com.acorn.abc.payment.domain;

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
@Table(name = "coupon_usage")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CouponUsage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "coupon_usage_id")
    private Long couponUsageId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_coupon_id")
    private MemberCoupon memberCoupon;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "rental_id")
    private BookRental rental;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "payment_id")
    private Payment payment;

    @Column(name = "discount_amount", nullable = false)
    private int discountAmount;

    @Column(name = "usage_note", length = 255)
    private String usageNote;

    @Column(name = "used_at", nullable = false)
    private LocalDateTime usedAt;

    public static CouponUsage create(MemberCoupon memberCoupon, BookRental rental, Payment payment, int discountAmount, LocalDateTime usedAt) {
        CouponUsage usage = new CouponUsage();
        usage.memberCoupon = memberCoupon;
        usage.rental = rental;
        usage.payment = payment;
        usage.discountAmount = discountAmount;
        usage.usedAt = usedAt;
        return usage;
    }
}
