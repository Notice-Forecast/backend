package com.nf.noticeforecast.device;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.nf.noticeforecast.device.dto.PushSubscriptionRequest;
import com.nf.noticeforecast.forecast.SeriesFollowRepository;
import com.nf.noticeforecast.global.error.InvalidFieldException;
import com.nf.noticeforecast.notice.NoticeStatusRepository;
import com.nf.noticeforecast.notice.ReportRepository;
import com.nf.noticeforecast.push.PushLogRepository;
import java.util.Base64;
import org.junit.jupiter.api.Test;

class DeviceServiceTest {

    private final PushSubscriptionRepository pushSubscriptions = mock(PushSubscriptionRepository.class);
    private final DeviceService service = new DeviceService(
            mock(DeviceRepository.class), pushSubscriptions, mock(NoticeStatusRepository.class),
            mock(ReportRepository.class), mock(PushLogRepository.class), mock(SeriesFollowRepository.class),
            new RestoreCodeGenerator());

    private static String b64(int byteLength) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(new byte[byteLength]);
    }

    @Test
    void 올바른_길이의_푸시_키만_저장한다() {
        String p256dh = b64(65);
        String auth = b64(16);
        PushSubscriptionRequest request = new PushSubscriptionRequest(
                "https://fcm.googleapis.com/fcm/send/abc", new PushSubscriptionRequest.Keys(p256dh, auth));

        service.subscribePush("d1", request);

        verify(pushSubscriptions).upsert(eq("d1"), eq("https://fcm.googleapis.com/fcm/send/abc"),
                eq(p256dh), eq(auth), any());
    }

    @Test
    void 잘못된_p256dh_키는_keys_p256dh_필드_오류다() {
        PushSubscriptionRequest request = new PushSubscriptionRequest(
                "https://fcm.googleapis.com/fcm/send/abc", new PushSubscriptionRequest.Keys(b64(32), b64(16)));

        assertThatThrownBy(() -> service.subscribePush("d1", request))
                .isInstanceOfSatisfying(InvalidFieldException.class,
                        ex -> assertThat(ex.getField()).isEqualTo("keys.p256dh"));
    }

    @Test
    void 잘못된_auth_키는_keys_auth_필드_오류다() {
        PushSubscriptionRequest request = new PushSubscriptionRequest(
                "https://fcm.googleapis.com/fcm/send/abc", new PushSubscriptionRequest.Keys(b64(65), "%%%not-base64%%%"));

        assertThatThrownBy(() -> service.subscribePush("d1", request))
                .isInstanceOfSatisfying(InvalidFieldException.class,
                        ex -> assertThat(ex.getField()).isEqualTo("keys.auth"));
    }
}
