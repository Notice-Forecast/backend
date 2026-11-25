package com.nf.noticeforecast.device.dto;

import com.nf.noticeforecast.device.Device;
import io.swagger.v3.oas.annotations.media.Schema;

public record DeviceResponse(
        @Schema(example = "6f1c2b9e-8a4d-4f3e-9b7a-2d5c1e0f4a83", requiredMode = Schema.RequiredMode.REQUIRED) String deviceId,
        @Schema(example = "K7QF-2M9X", requiredMode = Schema.RequiredMode.REQUIRED) String restoreCode,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) boolean pushSubscribed,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) AlertSettings alertSettings,
        @Schema(description = "설정을 저장하기 전에는 null", nullable = true) MatchFilter matchFilter
) {
    public static DeviceResponse of(Device device, boolean pushSubscribed) {
        AlertSettings alerts = new AlertSettings(device.isAlertNewNotice(), device.isAlertPrepStart(),
                device.isAlertNoticeChanged(), device.isAlertDeadline());
        MatchFilter filter = device.hasMatchFilter()
                ? new MatchFilter(device.getMatchDepartment(), device.getMatchGrade(), device.getMatchEnrollmentStatus())
                : null;
        return new DeviceResponse(device.getId(), device.getRestoreCode(), pushSubscribed, alerts, filter);
    }
}
