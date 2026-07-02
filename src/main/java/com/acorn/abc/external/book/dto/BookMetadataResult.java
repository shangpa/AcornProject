package com.acorn.abc.external.book.dto;

import java.util.List;

/**
 * 3개 provider 결과를 병합한 공통 메타데이터.
 * 필드 우선순위는 BookMetadataAggregateService에서 적용한다.
 */
public record BookMetadataResult(
        String isbn13,
        String title,
        String author,
        String publisher,
        String coverImageUrl,
        String description,
        String pubDate,
        String aladinCategoryId,
        String aladinCategoryName,
        ProviderStatus infonaru,
        ProviderStatus naver,
        ProviderStatus aladin,
        List<String> warnings
) {
}
