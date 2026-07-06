package com.acorn.abc.common.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

/**
 * 프로젝트 전체 에러 코드 관리 enum.
 * 새로운 예외가 필요할 때 반드시 여기에 먼저 추가한다.
 * 새로운 예외가 필요할때는 반드시 팀장에게 보고후 승인 받고 추가한다.
 *
 * [명명 규칙]
 * 도메인_상황 형식의 대문자 스네이크 케이스
 * 예: MEMBER_NOT_FOUND, BOOK_STOCK_EMPTY
 *
 * [추가 방법]
 * 아래 기존 항목을 참고해서 도메인 그룹에 맞게 추가한다.
 * BOOK_STOCK_EMPTY(HttpStatus.CONFLICT, "BOOK_STOCK_EMPTY", "재고가 없습니다."),
 *
 * [사용법]
 * throw new BusinessException(ErrorCode.MEMBER_NOT_FOUND);
 */
@Getter
@RequiredArgsConstructor
public enum ErrorCode {

    // Common
    COMMON_NOT_FOUND(HttpStatus.NOT_FOUND, "COMMON_NOT_FOUND", "리소스를 찾을 수 없습니다."),
    INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_SERVER_ERROR", "서버 내부 오류가 발생했습니다."),
    VALIDATION_ERROR(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "입력값을 확인해주세요."),

    // Auth
    AUTH_LOGIN_FAILED(HttpStatus.UNAUTHORIZED, "AUTH_LOGIN_FAILED", "아이디 또는 비밀번호가 올바르지 않습니다."),
    AUTH_TOKEN_EXPIRED(HttpStatus.UNAUTHORIZED, "AUTH_TOKEN_EXPIRED", "토큰이 만료되었습니다."),
    AUTH_ACCESS_DENIED(HttpStatus.FORBIDDEN, "AUTH_ACCESS_DENIED", "접근 권한이 없습니다."),

    // Member
    MEMBER_NOT_FOUND(HttpStatus.NOT_FOUND, "MEMBER_NOT_FOUND", "존재하지 않는 회원입니다."),
    MEMBER_EMAIL_DUPLICATED(HttpStatus.CONFLICT, "MEMBER_EMAIL_DUPLICATED", "이미 사용 중인 이메일입니다."),
    MEMBER_LOGIN_ID_DUPLICATED(HttpStatus.CONFLICT, "MEMBER_LOGIN_ID_DUPLICATED", "이미 사용 중인 아이디입니다."),
    AUTH_INVALID_PASSWORD(HttpStatus.BAD_REQUEST, "AUTH_INVALID_PASSWORD", "현재 비밀번호가 올바르지 않습니다."),

    // Book
    BOOK_NOT_FOUND(HttpStatus.NOT_FOUND, "BOOK_NOT_FOUND", "존재하지 않는 도서입니다."),
    BOOK_PAGE_NOT_FOUND(HttpStatus.NOT_FOUND, "BOOK_PAGE_NOT_FOUND", "존재하지 않는 페이지입니다."),
    BOOK_ISBN_ALREADY_EXISTS(HttpStatus.CONFLICT, "BOOK_ISBN_ALREADY_EXISTS", "이미 같은 ISBN으로 등록된 도서입니다."),
    CATEGORY_NOT_FOUND(HttpStatus.NOT_FOUND, "CATEGORY_NOT_FOUND", "존재하지 않는 카테고리입니다."),

    // BookIsbnTemp (외부 도서 수집)
    BOOK_ISBN_TEMP_NOT_FOUND(HttpStatus.NOT_FOUND, "BOOK_ISBN_TEMP_NOT_FOUND", "존재하지 않는 수집 후보입니다."),
    BOOK_ISBN_TEMP_NOT_READY(HttpStatus.CONFLICT, "BOOK_ISBN_TEMP_NOT_READY", "승인 가능한 상태(READY)가 아닙니다."),
    BOOK_ISBN_TEMP_ALREADY_MERGED(HttpStatus.CONFLICT, "BOOK_ISBN_TEMP_ALREADY_MERGED", "이미 이관 완료된 수집 후보입니다."),

    // Rental
    RENTAL_ALREADY_EXISTS(HttpStatus.CONFLICT, "RENTAL_ALREADY_EXISTS", "이미 대여 중인 도서입니다."),
    RENTAL_NOT_FOUND(HttpStatus.NOT_FOUND, "RENTAL_NOT_FOUND", "존재하지 않는 대여 내역입니다."),
    RENTAL_EXPIRED(HttpStatus.FORBIDDEN, "RENTAL_EXPIRED", "대여 기간이 만료되었습니다."),

    // Bookmark
    BOOKMARK_NOT_FOUND(HttpStatus.NOT_FOUND, "BOOKMARK_NOT_FOUND", "존재하지 않는 북마크입니다."),
    BOOKMARK_ALREADY_EXISTS(HttpStatus.CONFLICT, "BOOKMARK_ALREADY_EXISTS", "이미 같은 페이지에 북마크가 있습니다."),

    // Payment
    PAYMENT_FAILED(HttpStatus.BAD_REQUEST, "PAYMENT_FAILED", "결제에 실패했습니다."),
    BOOK_RENTAL_TYPE_MISMATCH(HttpStatus.BAD_REQUEST, "BOOK_RENTAL_TYPE_MISMATCH", "도서의 대여 유형과 일치하지 않는 요청입니다."),
    MEMBER_COUPON_NOT_USABLE(HttpStatus.CONFLICT, "MEMBER_COUPON_NOT_USABLE", "사용할 수 없는 쿠폰입니다."),
    POINT_BALANCE_INSUFFICIENT(HttpStatus.BAD_REQUEST, "POINT_BALANCE_INSUFFICIENT", "포인트 잔액이 부족합니다.");

    private final HttpStatus httpStatus;
    private final String code;
    private final String message;
}
