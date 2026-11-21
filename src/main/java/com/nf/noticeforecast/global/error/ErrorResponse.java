package com.nf.noticeforecast.global.error;

public record ErrorResponse(
        int status,
        String code,
        String message
) {
}
