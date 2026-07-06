package com.acorn.abc.payment.repository;

import com.acorn.abc.payment.domain.CouponUsage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CouponUsageRepository extends JpaRepository<CouponUsage, Long> {

    Optional<CouponUsage> findByPayment_PaymentId(Long paymentId);
}
