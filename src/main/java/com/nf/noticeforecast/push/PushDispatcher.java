package com.nf.noticeforecast.push;

import com.nf.noticeforecast.device.Device;
import com.nf.noticeforecast.device.PushSubscription;
import com.nf.noticeforecast.device.PushSubscriptionRepository;
import java.time.Clock;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import tools.jackson.databind.json.JsonMapper;

/**
 * 알림 하나를 기기 하나에 보낸다(CLAUDE.md 9.1~9.2).
 * <ul>
 *   <li>기기의 alertSettings에서 꺼 둔 종류는 보내지 않는다.</li>
 *   <li>push_log에 같은 (기기, 종류, 공지, 계열, revision)이 있으면 보내지 않는다.</li>
 *   <li>푸시 서버가 받았거나(2xx) 구독이 만료됐으면(404·410, 구독 삭제) 기록을 남긴다. 그 밖의 실패는 기록하지 않아 다음에 다시 보낸다.</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PushDispatcher {

    public enum Delivery { SENT, SKIPPED_SETTING, SKIPPED_DUPLICATE, NO_SUBSCRIPTION, GONE, FAILED, NOT_CONFIGURED }

    private final PushSender sender;
    private final PushSubscriptionRepository subscriptionRepository;
    private final PushLogRepository pushLogRepository;
    private final JsonMapper jsonMapper;
    private final Clock clock;

    public Delivery dispatch(Device device, PushPayload payload, Long seriesId, int revision) {
        if (!enabled(device, payload.type())) {
            return Delivery.SKIPPED_SETTING;
        }
        long noticeId = payload.noticeId() == null ? PushLog.NONE : payload.noticeId();
        long series = seriesId == null ? PushLog.NONE : seriesId;
        if (pushLogRepository.existsByDeviceIdAndTypeAndNoticeIdAndSeriesIdAndRevision(device.getId(),
                payload.type().name(), noticeId, series, revision)) {
            return Delivery.SKIPPED_DUPLICATE;
        }
        Optional<PushSubscription> subscription = subscriptionRepository.findById(device.getId());
        if (subscription.isEmpty()) {
            return Delivery.NO_SUBSCRIPTION;
        }
        Delivery delivery = deliver(subscription.get(), payload);
        if (delivery == Delivery.SENT || delivery == Delivery.GONE) {
            try {
                pushLogRepository.save(PushLog.of(device.getId(), payload.type(), payload.noticeId(), seriesId,
                        revision, clock.instant()));
            } catch (DataIntegrityViolationException e) {
                log.info("push already logged for device {} {}", device.getId(), payload.type());
            }
        }
        return delivery;
    }

    /** 설정·중복 검사와 기록 없이 바로 보낸다. 시연 준비용 테스트 푸시에 쓴다. */
    public Delivery sendDirect(String deviceId, PushPayload payload) {
        return subscriptionRepository.findById(deviceId)
                .map(subscription -> deliver(subscription, payload))
                .orElse(Delivery.NO_SUBSCRIPTION);
    }

    private Delivery deliver(PushSubscription subscription, PushPayload payload) {
        PushSender.Result result = sender.send(subscription, jsonMapper.writeValueAsString(payload));
        return switch (result.outcome()) {
            case SENT -> Delivery.SENT;
            case GONE -> {
                subscriptionRepository.deleteById(subscription.getDeviceId());
                log.info("push subscription gone for device {} ({})", subscription.getDeviceId(), result.status());
                yield Delivery.GONE;
            }
            case FAILED -> Delivery.FAILED;
            case NOT_CONFIGURED -> Delivery.NOT_CONFIGURED;
        };
    }

    static boolean enabled(Device device, PushType type) {
        return switch (type) {
            case NEW_NOTICE -> device.isAlertNewNotice();
            case PREP_START -> device.isAlertPrepStart();
            case NOTICE_CHANGED -> device.isAlertNoticeChanged();
            case DEADLINE -> device.isAlertDeadline();
            // 예보 알림은 계열 구독(notify)으로 켜고 끈다.
            case SERIES_POSTED -> true;
        };
    }
}
