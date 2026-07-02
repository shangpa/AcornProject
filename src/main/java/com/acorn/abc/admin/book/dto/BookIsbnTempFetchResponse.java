package com.acorn.abc.admin.book.dto;

/**
 * temp 저장/upsert 응답.
 */
public record BookIsbnTempFetchResponse(
        Long tempId,
        String isbn13,
        byte statusCd,
        String aladinCategoryId,
        String aladinCategoryName,
        ExternalBookDuplicateInfo duplicate
) {
}
