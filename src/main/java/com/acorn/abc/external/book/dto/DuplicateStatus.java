package com.acorn.abc.external.book.dto;

/**
 * ISBN/내용 기반 중복 판정 상태.
 * NONE: 중복 후보 없음
 * BOOK_EXISTS: 같은 ISBN의 내부 도서가 이미 있음
 * POSSIBLE_DUPLICATE: 같은 내용으로 보이는 내부 도서가 있음
 */
public enum DuplicateStatus {
    NONE,
    BOOK_EXISTS,
    POSSIBLE_DUPLICATE
}
