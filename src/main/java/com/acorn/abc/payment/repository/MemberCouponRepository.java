package com.acorn.abc.payment.repository;

import com.acorn.abc.payment.domain.MemberCoupon;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface MemberCouponRepository extends JpaRepository<MemberCoupon, Long> {

    Optional<MemberCoupon> findByMemberCouponIdAndMember_MemberId(Long memberCouponId, Long memberId);
}
