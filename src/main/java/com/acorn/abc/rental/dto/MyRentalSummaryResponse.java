package com.acorn.abc.rental.dto;

import java.time.LocalDateTime;

public record MyRentalSummaryResponse(
        Long rentalId,
        Long bookId,
        String title,
        String coverImageUrl,
        String rentalStatus,
        LocalDateTime rentalEndAt,
        int currentPage,
        int totalPages,
        double progressRate
) {
}
