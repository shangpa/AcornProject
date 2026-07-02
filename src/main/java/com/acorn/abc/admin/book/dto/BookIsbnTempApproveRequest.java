package com.acorn.abc.admin.book.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.util.List;

/**
 * temp 승인 후 실제 book 생성 요청.
 * rentalPrice(FREE=0, PAID>0)와 pageNo 중복은 서비스에서 추가 검증한다.
 */
public record BookIsbnTempApproveRequest(
        @NotBlank(message = "제목은 필수입니다.")
        String title,

        @NotBlank(message = "출판사명은 필수입니다.")
        String publisherName,

        @NotEmpty(message = "저자는 최소 1명 이상 필요합니다.")
        List<@NotBlank(message = "저자명은 비어 있을 수 없습니다.") String> authors,

        // categoryIds가 비어 있으면 알라딘 카테고리 자동 매핑으로 대체한다 (BookApprovalService).
        List<Long> categoryIds,

        @NotNull(message = "대여 타입은 필수입니다.")
        @Pattern(regexp = "FREE|PAID", message = "대여 타입은 FREE 또는 PAID여야 합니다.")
        String rentalType,

        @NotNull(message = "대여 가격은 필수입니다.")
        @Min(value = 0, message = "대여 가격은 0 이상이어야 합니다.")
        Integer rentalPrice,

        @NotBlank(message = "도서 상태는 필수입니다.")
        @Pattern(regexp = "AVAILABLE|HIDDEN|INACTIVE", message = "도서 상태가 올바르지 않습니다.")
        String status,

        @Valid
        Detail detail,

        @Valid
        List<Page> pages
) {

    public record Detail(
            String description,
            String tableOfContents,
            String publisherReview,
            String detailContent
    ) {
    }

    public record Page(
            @NotNull(message = "페이지 번호는 필수입니다.")
            @Min(value = 1, message = "페이지 번호는 1 이상이어야 합니다.")
            Integer pageNo,

            @NotBlank(message = "페이지 내용은 필수입니다.")
            String pageContent
    ) {
    }
}
