package com.acorn.abc.rental.controller;

import com.acorn.abc.common.response.ApiResponse;
import com.acorn.abc.common.security.CustomUserDetails;
import com.acorn.abc.payment.dto.PaidRentalRequest;
import com.acorn.abc.payment.dto.PaymentCreateResponse;
import com.acorn.abc.payment.service.PaymentService;
import com.acorn.abc.rental.dto.RentalCreateRequest;
import com.acorn.abc.rental.dto.RentalResponse;
import com.acorn.abc.rental.service.RentalService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Rentals", description = "대여 API")
@RestController
@RequestMapping("/api/v1/rentals")
@RequiredArgsConstructor
public class RentalController {

    private final RentalService rentalService;
    private final PaymentService paymentService;

    @Operation(summary = "무료 대여 생성", description = "무료 도서를 대여합니다.")
    @PostMapping("/free")
    public ResponseEntity<ApiResponse<RentalResponse>> createFreeRental(
            @Valid @RequestBody RentalCreateRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        RentalResponse response = rentalService.createFreeRental(userDetails.getMemberId(), request.bookId());
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.created(response));
    }

    @Operation(summary = "유료 대여 결제 및 대여 생성", description = "유료 도서를 결제하고 대여를 생성합니다.")
    @PostMapping("/paid")
    public ResponseEntity<ApiResponse<PaymentCreateResponse>> createPaidRental(
            @Valid @RequestBody PaidRentalRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        PaymentCreateResponse response = paymentService.createPaidRentalAndPayment(userDetails.getMemberId(), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.created(response));
    }
}
