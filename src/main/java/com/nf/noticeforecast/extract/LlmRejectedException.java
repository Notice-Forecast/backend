package com.nf.noticeforecast.extract;

public class LlmRejectedException extends RuntimeException {

    public LlmRejectedException(String message, Throwable cause) {
        super(message, cause);
    }
}
