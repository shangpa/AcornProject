package com.acorn.abc.payment.dto;

public record PaymentCreateResponse(
        Long rentalId,
        Long paymentId,
        int originalAmount,
        int couponDiscountAmount,
        int pointUsedAmount,
        int amount,
        String paymentMethod,
        String paymentStatus
) {
}
