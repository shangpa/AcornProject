package com.acorn.abc.member.repository;

import com.acorn.abc.member.domain.PointHistory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PointHistoryRepository extends JpaRepository<PointHistory, Long> {

    Optional<PointHistory> findFirstByPayment_PaymentId(Long paymentId);
}
