package com.acorn.abc.admin.book.dto;

import com.acorn.abc.batch.domain.BookIsbnTemp;

import java.time.LocalDateTime;

/**
 * temp 후보 목록 항목.
 */
public record BookIsbnTempSummaryResponse(
        Long tempId,
        String isbn13,
        String bookTitle,
        String publisher,
        String author,
        String coverImageUrl,
        String pubDate,
        String aladinCategoryId,
        String aladinCategoryName,
        byte statusCd,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {

    public static BookIsbnTempSummaryResponse from(BookIsbnTemp temp) {
        return new BookIsbnTempSummaryResponse(
                temp.getId(),
                temp.getIsbn13(),
                temp.getBookTitle(),
                temp.getPublisher(),
                temp.getAuthor(),
                temp.getCoverImageUrl(),
                temp.getPubDate(),
                temp.getAladinCategoryId(),
                temp.getAladinCategoryName(),
                temp.getStatusCd(),
                temp.getCreatedAt(),
                temp.getUpdatedAt()
        );
    }
}
