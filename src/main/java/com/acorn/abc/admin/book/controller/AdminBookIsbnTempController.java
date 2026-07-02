package com.acorn.abc.admin.book.controller;

import com.acorn.abc.admin.book.dto.BookIsbnTempApproveRequest;
import com.acorn.abc.admin.book.dto.BookIsbnTempApproveResponse;
import com.acorn.abc.admin.book.dto.BookIsbnTempFetchRequest;
import com.acorn.abc.admin.book.dto.BookIsbnTempFetchResponse;
import com.acorn.abc.admin.book.dto.BookIsbnTempSummaryResponse;
import com.acorn.abc.batch.domain.BookIsbnTemp;
import com.acorn.abc.batch.service.BookApprovalService;
import com.acorn.abc.batch.service.BookIsbnTempService;
import com.acorn.abc.common.response.ApiResponse;
import com.acorn.abc.common.response.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Admin Book ISBN Temp", description = "관리자 도서 수집 후보(temp) API")
@RestController
@RequestMapping("/api/v1/admin/book-isbn-temps")
@RequiredArgsConstructor
public class AdminBookIsbnTempController {

    private final BookIsbnTempService bookIsbnTempService;
    private final BookApprovalService bookApprovalService;

    @Operation(summary = "temp 저장/upsert",
            description = "외부 API 병합 결과를 book_isbn_temp에 insert 또는 update한다.")
    @PostMapping("/fetch")
    public ResponseEntity<ApiResponse<BookIsbnTempFetchResponse>> fetch(
            @Valid @RequestBody BookIsbnTempFetchRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(bookIsbnTempService.fetch(request.isbn13())));
    }

    @Operation(summary = "temp 후보 목록 조회", description = "status(status_cd)별 수집 후보를 페이징 조회한다.")
    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<BookIsbnTempSummaryResponse>>> list(
            @RequestParam(name = "status", defaultValue = "READY") String status,
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "20") int size) {

        byte statusCd = toStatusCd(status);
        Page<BookIsbnTemp> result = bookIsbnTempService.list(statusCd,
                PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "updatedAt", "createdAt")));

        PageResponse<BookIsbnTempSummaryResponse> body = PageResponse.of(
                result.getContent().stream().map(BookIsbnTempSummaryResponse::from).toList(),
                result.getNumber(), result.getSize(), result.getTotalElements());
        return ResponseEntity.ok(ApiResponse.ok(body));
    }

    @Operation(summary = "temp 승인 이관",
            description = "READY 상태의 temp를 실제 book으로 이관한다. 성공 시 temp를 MERGED로 변경한다.")
    @PostMapping("/{tempId}/approve")
    public ResponseEntity<ApiResponse<BookIsbnTempApproveResponse>> approve(
            @PathVariable("tempId") Long tempId,
            @Valid @RequestBody BookIsbnTempApproveRequest request) {
        BookIsbnTempApproveResponse response = bookApprovalService.approve(tempId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.created(response));
    }

    private byte toStatusCd(String status) {
        return switch (status.toUpperCase()) {
            case "PENDING" -> BookIsbnTemp.STATUS_PENDING;
            case "READY" -> BookIsbnTemp.STATUS_READY;
            case "MERGED" -> BookIsbnTemp.STATUS_MERGED;
            default -> BookIsbnTemp.STATUS_READY;
        };
    }
}
