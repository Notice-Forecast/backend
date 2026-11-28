package com.nf.noticeforecast.push;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nf.noticeforecast.device.Device;
import com.nf.noticeforecast.device.PushSubscription;
import com.nf.noticeforecast.device.PushSubscriptionRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import tools.jackson.databind.json.JsonMapper;

class PushDispatcherTest {

    private final PushSender sender = mock(PushSender.class);
    private final PushSubscriptionRepository subscriptions = mock(PushSubscriptionRepository.class);
    private final PushLogRepository logs = mock(PushLogRepository.class);
    private final PushDispatcher dispatcher = new PushDispatcher(sender, subscriptions, logs,
            JsonMapper.builder().build(), Clock.systemUTC());
    private final PushPayload payload = PushPayload.forNotice(PushType.DEADLINE, "마감 임박", "본문", 1024L);
    private Device device;

    @BeforeEach
    void setUp() {
        device = Device.register("ABCD-EFGH", Instant.now());
        ReflectionTestUtils.setField(device, "id", "device-1");
        PushSubscription subscription = mock(PushSubscription.class);
        when(subscription.getDeviceId()).thenReturn("device-1");
        when(subscriptions.findById("device-1")).thenReturn(Optional.of(subscription));
    }

    @Test
    void 보내면_기록을_남기고_페이로드에_빠른_동작이_들어간다() {
        when(sender.send(any(), anyString())).thenReturn(new PushSender.Result(PushSender.Outcome.SENT, 201));

        assertThat(dispatcher.dispatch(device, payload, null, 2)).isEqualTo(PushDispatcher.Delivery.SENT);

        verify(logs).save(any(PushLog.class));
        verify(sender).send(any(), org.mockito.ArgumentMatchers.contains("\"actions\":[\"PLANNED\",\"DISMISSED\"]"));
    }

    @Test
    void 이미_보낸_알림과_꺼_둔_종류는_보내지_않는다() {
        when(logs.existsByDeviceIdAndTypeAndNoticeIdAndSeriesIdAndRevision(anyString(), anyString(), anyLong(),
                anyLong(), anyInt())).thenReturn(true);
        assertThat(dispatcher.dispatch(device, payload, null, 2)).isEqualTo(PushDispatcher.Delivery.SKIPPED_DUPLICATE);

        device.updateAlerts(true, true, true, false);
        assertThat(dispatcher.dispatch(device, payload, null, 2)).isEqualTo(PushDispatcher.Delivery.SKIPPED_SETTING);
        verify(sender, never()).send(any(), anyString());
    }

    @Test
    void 구독이_만료되면_지우고_일시_실패는_기록하지_않는다() {
        when(sender.send(any(), anyString())).thenReturn(new PushSender.Result(PushSender.Outcome.GONE, 410));
        assertThat(dispatcher.dispatch(device, payload, null, 1)).isEqualTo(PushDispatcher.Delivery.GONE);
        verify(subscriptions).deleteById("device-1");

        when(sender.send(any(), anyString())).thenReturn(new PushSender.Result(PushSender.Outcome.FAILED, 503));
        assertThat(dispatcher.dispatch(device, payload, null, 3)).isEqualTo(PushDispatcher.Delivery.FAILED);
        verify(logs, org.mockito.Mockito.times(1)).save(any(PushLog.class));
    }
}
