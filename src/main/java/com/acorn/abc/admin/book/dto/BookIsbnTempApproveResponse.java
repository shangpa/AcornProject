package com.acorn.abc.admin.book.dto;

/**
 * temp 승인 이관 응답.
 */
public record BookIsbnTempApproveResponse(
        Long bookId,
        Long tempId,
        String status
) {
}
