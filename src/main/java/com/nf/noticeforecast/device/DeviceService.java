package com.nf.noticeforecast.device;

import com.nf.noticeforecast.device.dto.DeviceRegisterResponse;
import com.nf.noticeforecast.device.dto.DeviceResponse;
import com.nf.noticeforecast.device.dto.DeviceRestoreResponse;
import com.nf.noticeforecast.device.dto.DeviceSettingsRequest;
import com.nf.noticeforecast.device.dto.PushSubscriptionRequest;
import com.nf.noticeforecast.global.error.BusinessException;
import com.nf.noticeforecast.global.error.ErrorCode;
import com.nf.noticeforecast.notice.NoticeStatusRepository;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class DeviceService {

    private final DeviceRepository deviceRepository;
    private final PushSubscriptionRepository pushSubscriptionRepository;
    private final NoticeStatusRepository noticeStatusRepository;
    private final RestoreCodeGenerator restoreCodeGenerator;

    @Transactional
    public DeviceRegisterResponse register() {
        Instant now = Instant.now().truncatedTo(ChronoUnit.SECONDS);
        return DeviceRegisterResponse.from(deviceRepository.save(Device.register(newRestoreCode(), now)));
    }

    @Transactional
    public DeviceRestoreResponse restore(String restoreCode) {
        Device device = deviceRepository.findByRestoreCode(restoreCode.trim().toUpperCase(Locale.ROOT))
                .orElseThrow(() -> new BusinessException(ErrorCode.RESTORE_CODE_INVALID));
        pushSubscriptionRepository.deleteByDeviceId(device.getId());
        return new DeviceRestoreResponse(device.getId());
    }

    @Transactional(readOnly = true)
    public DeviceResponse get(String deviceId) {
        return toResponse(find(deviceId));
    }

    @Transactional
    public DeviceResponse updateSettings(String deviceId, DeviceSettingsRequest request) {
        Device device = find(deviceId);
        var alerts = request.alertSettings();
        device.updateAlerts(alerts.newNotice(), alerts.prepStart(), alerts.noticeChanged(), alerts.deadline());
        var filter = request.matchFilter().normalized();
        device.updateMatchFilter(filter.department(), filter.extraDepartments(), filter.grade(),
                filter.enrollmentStatus());
        return toResponse(device);
    }

    @Transactional
    public void subscribePush(String deviceId, PushSubscriptionRequest request) {
        pushSubscriptionRepository.upsert(deviceId, request.endpoint(), request.keys().p256dh(),
                request.keys().auth(), Instant.now());
    }

    @Transactional
    public void unsubscribePush(String deviceId) {
        pushSubscriptionRepository.deleteByDeviceId(deviceId);
    }

    @Transactional
    public void delete(String deviceId) {
        pushSubscriptionRepository.deleteByDeviceId(deviceId);
        noticeStatusRepository.deleteAllByDeviceId(deviceId);
        deviceRepository.deleteById(deviceId);
    }

    private DeviceResponse toResponse(Device device) {
        return DeviceResponse.of(device, pushSubscriptionRepository.existsById(device.getId()));
    }

    private Device find(String deviceId) {
        return deviceRepository.findById(deviceId)
                .orElseThrow(() -> new BusinessException(ErrorCode.DEVICE_NOT_FOUND));
    }

    private String newRestoreCode() {
        String code = restoreCodeGenerator.generate();
        while (deviceRepository.existsByRestoreCode(code)) {
            code = restoreCodeGenerator.generate();
        }
        return code;
    }
}
