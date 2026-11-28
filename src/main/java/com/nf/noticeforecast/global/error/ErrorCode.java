package com.nf.noticeforecast.global.error;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum ErrorCode {
    VALIDATION_FAILED(HttpStatus.BAD_REQUEST, "입력한 내용을 다시 확인해 주세요."),
    INVALID_STATUS(HttpStatus.BAD_REQUEST, "선택할 수 없는 상태입니다."),
    DEVICE_REQUIRED(HttpStatus.UNAUTHORIZED, "기기 등록이 필요합니다."),
    ADMIN_REQUIRED(HttpStatus.UNAUTHORIZED, "관리자 키가 필요합니다."),
    DEVICE_NOT_FOUND(HttpStatus.NOT_FOUND, "등록되지 않은 기기입니다."),
    NOTICE_NOT_FOUND(HttpStatus.NOT_FOUND, "공지를 찾을 수 없습니다."),
    NOTICE_BODY_MISSING(HttpStatus.CONFLICT, "본문이 없는 공지라 AI 추출을 할 수 없습니다."),
    LLM_NOT_CONFIGURED(HttpStatus.SERVICE_UNAVAILABLE, "AI 추출 설정(LLM_API_KEY)이 없습니다."),
    SERIES_NOT_FOUND(HttpStatus.NOT_FOUND, "예보 계열을 찾을 수 없습니다."),
    RESTORE_CODE_INVALID(HttpStatus.NOT_FOUND, "복구 코드를 찾을 수 없습니다."),
    NOT_FOUND(HttpStatus.NOT_FOUND, "요청한 주소를 찾을 수 없습니다."),
    METHOD_NOT_ALLOWED(HttpStatus.METHOD_NOT_ALLOWED, "지원하지 않는 요청 방식입니다."),
    UNSUPPORTED_MEDIA_TYPE(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "지원하지 않는 요청 형식입니다."),
    RATE_LIMITED(HttpStatus.TOO_MANY_REQUESTS, "요청이 너무 많습니다. 잠시 후 다시 시도해 주세요."),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "서버에 문제가 생겼습니다. 잠시 후 다시 시도해 주세요.");

    private final HttpStatus status;
    private final String message;
}
