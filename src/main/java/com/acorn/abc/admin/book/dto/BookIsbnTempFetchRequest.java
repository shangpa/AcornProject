package com.acorn.abc.admin.book.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * temp 저장/upsert 요청.
 */
public record BookIsbnTempFetchRequest(
        @NotBlank(message = "ISBN13은 필수입니다.")
        String isbn13
) {
}
