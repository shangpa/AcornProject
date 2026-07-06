package com.acorn.abc.rental.controller;

import com.acorn.abc.common.response.ApiResponse;
import com.acorn.abc.common.response.PageResponse;
import com.acorn.abc.common.security.CustomUserDetails;
import com.acorn.abc.rental.dto.MyRentalCriteria;
import com.acorn.abc.rental.dto.MyRentalSummaryResponse;
import com.acorn.abc.rental.service.RentalService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "MyRentals", description = "내 대여 현황 API")
@RestController
@RequestMapping("/api/v1/me/rentals")
@RequiredArgsConstructor
public class MyRentalController {

    private final RentalService rentalService;

    @Operation(summary = "내 대여 현황 조회", description = "READY, READING, OWNED 상태의 내 대여 목록을 조회합니다.")
    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<MyRentalSummaryResponse>>> getMyRentals(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        MyRentalCriteria criteria = MyRentalCriteria.of(status, page, size);
        return ResponseEntity.ok(ApiResponse.ok(rentalService.getMyRentals(userDetails.getMemberId(), criteria)));
    }
}
