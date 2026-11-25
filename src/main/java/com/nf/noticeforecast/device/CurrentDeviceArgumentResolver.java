package com.nf.noticeforecast.device;

import com.nf.noticeforecast.global.error.BusinessException;
import com.nf.noticeforecast.global.error.ErrorCode;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.core.MethodParameter;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

@Component
@RequiredArgsConstructor
public class CurrentDeviceArgumentResolver implements HandlerMethodArgumentResolver {

    public static final String HEADER = "X-Device-Id";

    private final DeviceRepository deviceRepository;

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return parameter.hasParameterAnnotation(CurrentDevice.class);
    }

    @Override
    public Device resolveArgument(MethodParameter parameter, ModelAndViewContainer container,
                                  NativeWebRequest request, WebDataBinderFactory binderFactory) {
        String deviceId = request.getHeader(HEADER);
        if (deviceId == null || deviceId.isBlank()) {
            throw new BusinessException(ErrorCode.DEVICE_REQUIRED);
        }
        Device device = deviceRepository.findById(deviceId)
                .orElseThrow(() -> new BusinessException(ErrorCode.DEVICE_NOT_FOUND));
        deviceRepository.touch(device.getId(), Instant.now());
        return device;
    }
}
