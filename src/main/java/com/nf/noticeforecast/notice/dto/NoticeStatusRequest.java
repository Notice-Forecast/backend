package com.nf.noticeforecast.notice.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.swagger.v3.oas.annotations.media.Schema;

@StatusKeyRequired
public class NoticeStatusRequest {

    private String status;
    private boolean statusPresent;

    @Schema(description = "null이면 상태를 지웁니다. 키 자체는 반드시 보내야 합니다.", nullable = true,
            allowableValues = {"PLANNED", "APPLIED", "DISMISSED"}, example = "PLANNED",
            requiredMode = Schema.RequiredMode.REQUIRED)
    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
        this.statusPresent = true;
    }

    @JsonIgnore
    @Schema(hidden = true)
    public boolean isStatusPresent() {
        return statusPresent;
    }
}
