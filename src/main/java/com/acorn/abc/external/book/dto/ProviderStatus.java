package com.acorn.abc.external.book.dto;

/**
 * 외부 API provider 호출 상태.
 * code는 book_isbn_temp의 naver_res_status/aladin_res_status 컬럼값과 일치한다.
 * 0=미시도, 1=성공(데이터있음), 2=성공(데이터없음), 3=실패(재시도가능), 4=실패(재시도불가)
 */
public enum ProviderStatus {

    NOT_TRIED((byte) 0),
    SUCCESS_HAS_DATA((byte) 1),
    SUCCESS_NO_DATA((byte) 2),
    FAILED_RETRYABLE((byte) 3),
    FAILED((byte) 4);

    private final byte code;

    ProviderStatus(byte code) {
        this.code = code;
    }

    public byte code() {
        return code;
    }
}
