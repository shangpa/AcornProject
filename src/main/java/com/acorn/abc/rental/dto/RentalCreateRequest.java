package com.acorn.abc.rental.dto;

import jakarta.validation.constraints.NotNull;

public record RentalCreateRequest(
        @NotNull(message = "도서 ID는 필수입니다.") Long bookId
) {
}
