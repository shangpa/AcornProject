package com.acorn.abc.payment.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

public record PaidRentalRequest(
        @NotNull(message = "도서 ID는 필수입니다.") Long bookId,
        Long memberCouponId,
        @PositiveOrZero(message = "포인트 사용 금액은 0 이상이어야 합니다.") Integer pointAmount,
        @NotBlank(message = "카드 승인 토큰은 필수입니다.") String cardApprovalToken
) {
}
