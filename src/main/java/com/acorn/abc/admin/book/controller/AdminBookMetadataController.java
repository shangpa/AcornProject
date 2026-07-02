package com.acorn.abc.admin.book.controller;

import com.acorn.abc.admin.book.dto.ExternalBookLookupResponse;
import com.acorn.abc.batch.service.BookIsbnTempService;
import com.acorn.abc.common.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Admin External Book", description = "관리자 외부 도서 메타데이터 조회 API")
@RestController
@RequestMapping("/api/v1/admin/books/external")
@RequiredArgsConstructor
public class AdminBookMetadataController {

    private final BookIsbnTempService bookIsbnTempService;

    @Operation(summary = "ISBN13 preview 조회",
            description = "외부 API 3곳을 병합해 도서 메타데이터와 중복 정보를 반환한다. DB에는 저장하지 않는다.")
    @GetMapping("/lookup")
    public ResponseEntity<ApiResponse<ExternalBookLookupResponse>> lookup(
            @RequestParam("isbn13") String isbn13) {
        return ResponseEntity.ok(ApiResponse.ok(bookIsbnTempService.lookup(isbn13)));
    }
}
