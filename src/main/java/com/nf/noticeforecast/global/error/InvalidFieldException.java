package com.nf.noticeforecast.global.error;

import lombok.Getter;

/** 쿼리·본문의 특정 필드 값이 잘못됐을 때. 400 VALIDATION_FAILED와 fields { field: message }로 응답한다. */
@Getter
public class InvalidFieldException extends RuntimeException {

    private final String field;

    public InvalidFieldException(String field, String message) {
        super(message);
        this.field = field;
    }
}
