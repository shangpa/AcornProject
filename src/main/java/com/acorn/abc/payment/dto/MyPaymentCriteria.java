package com.acorn.abc.payment.dto;

import java.time.LocalDate;

public record MyPaymentCriteria(
        int page,
        int size,
        LocalDate from,
        LocalDate to
) {
    private static final int DEFAULT_SIZE = 10;

    public static MyPaymentCriteria of(Integer page, Integer size, LocalDate from, LocalDate to) {
        return new MyPaymentCriteria(
                page == null ? 0 : page,
                size == null ? DEFAULT_SIZE : size,
                from,
                to
        );
    }
}
