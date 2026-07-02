package com.acorn.abc.external.book.dto;

/**
 * provider 한 곳의 단건 조회 결과. 병합 서비스가 provider별 partial failure를 다룬다.
 * status 외 필드는 데이터가 없으면 null이다.
 */
public record ProviderBookData(
        ProviderStatus status,
        String title,
        String author,
        String publisher,
        String coverImageUrl,
        String description,
        String pubDate,
        String categoryId,
        String categoryName
) {

    public static ProviderBookData notTried() {
        return new ProviderBookData(ProviderStatus.NOT_TRIED, null, null, null, null, null, null, null, null);
    }

    public static ProviderBookData noData() {
        return new ProviderBookData(ProviderStatus.SUCCESS_NO_DATA, null, null, null, null, null, null, null, null);
    }

    public static ProviderBookData failedRetryable() {
        return new ProviderBookData(ProviderStatus.FAILED_RETRYABLE, null, null, null, null, null, null, null, null);
    }

    public static ProviderBookData failed() {
        return new ProviderBookData(ProviderStatus.FAILED, null, null, null, null, null, null, null, null);
    }
}
