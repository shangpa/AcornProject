package com.acorn.abc.rental.dto;

public record MyRentalCriteria(
        String status,
        int page,
        int size
) {
    private static final int DEFAULT_SIZE = 10;

    public static MyRentalCriteria of(String status, Integer page, Integer size) {
        return new MyRentalCriteria(
                status,
                page == null ? 0 : page,
                size == null ? DEFAULT_SIZE : size
        );
    }
}
