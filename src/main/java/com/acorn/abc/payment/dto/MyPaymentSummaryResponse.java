package com.acorn.abc.payment.dto;

import java.time.LocalDateTime;

public record MyPaymentSummaryResponse(
        Long paymentId,
        Long rentalId,
        Long bookId,
        String title,
        String coverImageUrl,
        int originalAmount,
        int couponDiscountAmount,
        int pointUsedAmount,
        int amount,
        String paymentMethod,
        String paymentStatus,
        LocalDateTime paidAt
) {
}
