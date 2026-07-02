package com.acorn.abc.admin.book.dto;

import java.util.List;

/**
 * 내부 도서 중복 정보. preview/fetch 응답에 포함한다.
 */
public record ExternalBookDuplicateInfo(
        String status,
        Long existingBookId,
        List<Candidate> candidates
) {

    public record Candidate(
            Long bookId,
            String title,
            String isbn,
            String status
    ) {
    }
}
