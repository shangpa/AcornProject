package com.acorn.abc.payment.controller;

import com.acorn.abc.common.response.ApiResponse;
import com.acorn.abc.common.response.PageResponse;
import com.acorn.abc.common.security.CustomUserDetails;
import com.acorn.abc.payment.dto.MyPaymentCriteria;
import com.acorn.abc.payment.dto.MyPaymentSummaryResponse;
import com.acorn.abc.payment.service.PaymentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@Tag(name = "MyPayments", description = "내 결제 내역 API")
@RestController
@RequestMapping("/api/v1/me/payments")
@RequiredArgsConstructor
public class MyPaymentController {

    private final PaymentService paymentService;

    @Operation(summary = "내 결제 내역 조회", description = "완료된 결제(RENTAL/CARD/PAID) 내역을 조회합니다.")
    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<MyPaymentSummaryResponse>>> getMyPayments(
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        MyPaymentCriteria criteria = MyPaymentCriteria.of(page, size, from, to);
        return ResponseEntity.ok(ApiResponse.ok(paymentService.getMyPayments(userDetails.getMemberId(), criteria)));
    }
}
