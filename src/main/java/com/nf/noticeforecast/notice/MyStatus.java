package com.nf.noticeforecast.notice;

import com.nf.noticeforecast.global.error.BusinessException;
import com.nf.noticeforecast.global.error.ErrorCode;

public enum MyStatus {
    PLANNED, APPLIED, DISMISSED;

    public static MyStatus parse(String value) {
        for (MyStatus status : values()) {
            if (status.name().equals(value)) {
                return status;
            }
        }
        throw new BusinessException(ErrorCode.INVALID_STATUS);
    }
}
