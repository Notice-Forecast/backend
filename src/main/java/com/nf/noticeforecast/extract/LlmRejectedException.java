package com.nf.noticeforecast.extract;

public class LlmRejectedException extends RuntimeException {

    private final int status;
    private final boolean inputTooLong;

    public LlmRejectedException(String message, Throwable cause) {
        this(message, cause, false);
    }

    public LlmRejectedException(String message, Throwable cause, boolean inputTooLong) {
        this(message, cause, 400, inputTooLong);
    }

    public LlmRejectedException(String message, Throwable cause, int status, boolean inputTooLong) {
        super(message, cause);
        this.status = status;
        this.inputTooLong = inputTooLong;
    }

    /** 키·권한 문제(401·403). 공지와 관계없이 모든 요청이 실패한다. */
    public boolean isAuthFailure() {
        return status == 401 || status == 403;
    }

    public boolean isInputTooLong() {
        return inputTooLong;
    }
}
