package com.acorn.abc.rental.dto;

import java.time.LocalDateTime;

public record RentalResponse(
        Long rentalId,
        Long bookId,
        String rentalStatus,
        LocalDateTime rentalEndAt
) {
}
