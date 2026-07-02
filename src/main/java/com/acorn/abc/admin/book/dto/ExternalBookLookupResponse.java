package com.acorn.abc.admin.book.dto;

import java.util.List;

/**
 * 관리자 ISBN13 preview 응답 (DB 저장 없음).
 */
public record ExternalBookLookupResponse(
        String isbn13,
        String title,
        String author,
        String publisher,
        String coverImageUrl,
        String description,
        String pubDate,
        String aladinCategoryId,
        String aladinCategoryName,
        ExternalBookProviderStatus providers,
        ExternalBookDuplicateInfo duplicate,
        List<String> warnings
) {
}
