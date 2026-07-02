package com.acorn.abc.admin.book.dto;

/**
 * provider별 조회 상태(ProviderStatus enum 이름).
 */
public record ExternalBookProviderStatus(
        String infonaru,
        String naver,
        String aladin
) {
}
