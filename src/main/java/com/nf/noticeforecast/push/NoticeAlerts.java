package com.nf.noticeforecast.push;

import com.nf.noticeforecast.device.Device;
import com.nf.noticeforecast.device.DeviceRepository;
import com.nf.noticeforecast.device.PushSubscription;
import com.nf.noticeforecast.device.PushSubscriptionRepository;
import com.nf.noticeforecast.extract.card.Card;
import com.nf.noticeforecast.extract.card.ScheduleItem;
import com.nf.noticeforecast.global.time.KoreanTime;
import com.nf.noticeforecast.notice.CardDiff;
import com.nf.noticeforecast.notice.DepartmentNames;
import com.nf.noticeforecast.notice.MyStatus;
import com.nf.noticeforecast.notice.Notice;
import com.nf.noticeforecast.notice.NoticeAlertRow;
import com.nf.noticeforecast.notice.NoticeRepository;
import com.nf.noticeforecast.notice.NoticeSource;
import com.nf.noticeforecast.notice.NoticeSourceRepository;
import com.nf.noticeforecast.notice.NoticeStatus;
import com.nf.noticeforecast.notice.NoticeStatusRepository;
import com.nf.noticeforecast.notice.SourceKind;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 공지 알림을 고르고 보낸다(CLAUDE.md 9.2~9.3, 문구는 11/28 결정).
 * <ul>
 *   <li>새 공지: 카드가 저장된 직후. 게시 48시간 이내이고 마감 전인 공지만(11/28 결정). 상태가 없고 matchFilter로 불충족이 없는 기기.
 *   학과 공지는 주전공·추가 전공이 그 학과인 기기만. 공지당 1회.</li>
 *   <li>공지 변경: 카드가 바뀐 직후(revision 상승). 그 공지가 신청 예정인 기기. 자격이 완화돼 새로 해당하게 된 기기는 새 공지를 그때 받는다.</li>
 *   <li>준비 시작: 매일 09:00. 신청 예정이고 PREP_START가 오늘.</li>
 *   <li>마감 임박: 10분마다. 신청 예정이고 마감이 24시간 안. 날짜만 있는 마감은 마감 전날 00:00부터(11/27 결정).</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NoticeAlerts {

    static final Duration NEW_NOTICE_WINDOW = Duration.ofHours(48);
    private static final Duration DEADLINE_WINDOW = Duration.ofHours(24);
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("M/d");
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("M/d HH:mm");
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");

    private final PushDispatcher dispatcher;
    private final DeviceRepository deviceRepository;
    private final PushSubscriptionRepository subscriptionRepository;
    private final NoticeRepository noticeRepository;
    private final NoticeSourceRepository sourceRepository;
    private final NoticeStatusRepository statusRepository;
    private final Clock clock;

    /** 카드를 저장한 직후에 부른다. 실패해도 카드 저장에는 영향을 주지 않는다. */
    public void onCardSaved(Notice notice, List<CardDiff.CardChange> changes) {
        try {
            if (!changes.isEmpty()) {
                sendChanged(notice);
            }
            boolean conditionsChanged = changes.stream().anyMatch(c -> CardDiff.CONDITIONS.equals(c.field()));
            sendNewNotice(notice, conditionsChanged);
        } catch (RuntimeException e) {
            log.warn("alerts failed for notice {}: {}", notice.getId(), e.toString());
        }
    }

    void sendNewNotice(Notice notice) {
        sendNewNotice(notice, false);
    }

    void sendNewNotice(Notice notice, boolean skipAgeWindow) {
        Instant now = clock.instant();
        if (notice.getCard() == null
                || (!skipAgeWindow && notice.getPostedAt().isBefore(now.minus(NEW_NOTICE_WINDOW)))
                || deadlinePassed(notice, now)) {
            return;
        }
        NoticeSource source = sourceRepository.findById(notice.getSourceId()).orElse(null);
        Set<String> withStatus = statusRepository.findAllByNoticeId(notice.getId()).stream()
                .map(NoticeStatus::getDeviceId).collect(Collectors.toSet());
        PushPayload payload = PushPayload.forNotice(PushType.NEW_NOTICE, notice.getTitle(),
                notice.getCard().summary(), notice.getId());
        int sent = 0;
        for (Device device : subscribedDevices()) {
            if (device.hasMatchFilter() && !withStatus.contains(device.getId()) && seesSource(device, source)
                    && ServerEligibility.notExcluded(notice.getCard(), device)
                    && dispatcher.dispatch(device, payload, null, 0) == PushDispatcher.Delivery.SENT) {
                sent++;
            }
        }
        if (sent > 0) {
            log.info("new notice alert {} sent to {} devices", notice.getId(), sent);
        }
    }

    void sendChanged(Notice notice) {
        String body = notice.getChangeSummary() == null ? "변경 내역을 확인해 주세요." : notice.getChangeSummary();
        PushPayload payload = PushPayload.forNotice(PushType.NOTICE_CHANGED, "공지가 바뀌었어요", body, notice.getId());
        for (Device device : plannedDevices(notice.getId())) {
            dispatcher.dispatch(device, payload, null, notice.getRevision());
        }
    }

    public void sendPrepStarts() {
        LocalDate today = LocalDate.ofInstant(clock.instant(), KoreanTime.ZONE);
        for (Map.Entry<NoticeAlertRow, List<Device>> entry : plannedByNotice().entrySet()) {
            NoticeAlertRow notice = entry.getKey();
            if (!prepStartsOn(notice.card(), today)) {
                continue;
            }
            String deadline = deadlineText(notice);
            String body = deadline == null ? notice.title() : notice.title() + " · 마감 " + deadline;
            PushPayload payload = PushPayload.forNotice(PushType.PREP_START, "오늘부터 준비하세요", body, notice.id());
            entry.getValue().forEach(device -> dispatcher.dispatch(device, payload, null, 0));
        }
    }

    public void sendDeadlines() {
        Instant now = clock.instant();
        for (Map.Entry<NoticeAlertRow, List<Device>> entry : plannedByNotice().entrySet()) {
            NoticeAlertRow notice = entry.getKey();
            if (!deadlineSoon(notice.deadlineAt(), notice.deadlineAllDay(), now)) {
                continue;
            }
            PushPayload payload = PushPayload.forNotice(PushType.DEADLINE, deadlineTitle(notice, now),
                    "신청 예정으로 둔 공지예요.", notice.id());
            entry.getValue().forEach(device -> dispatcher.dispatch(device, payload, null, 0));
        }
    }

    /** 마감이 24시간 안. 날짜만 있는 마감은 마감 전날 00:00부터 그날이 끝날 때까지. */
    static boolean deadlineSoon(Instant deadline, boolean allDay, Instant now) {
        if (deadline == null) {
            return false;
        }
        if (allDay) {
            return !now.isBefore(deadline.minus(Duration.ofDays(1))) && now.isBefore(deadline.plus(Duration.ofDays(1)));
        }
        return now.isBefore(deadline) && !deadline.isAfter(now.plus(DEADLINE_WINDOW));
    }

    static boolean deadlinePassed(Notice notice, Instant now) {
        Instant deadline = notice.getDeadlineAt();
        if (deadline == null) {
            return false;
        }
        return notice.isDeadlineAllDay() ? !now.isBefore(deadline.plus(Duration.ofDays(1))) : !now.isBefore(deadline);
    }

    /** 예: "계절학기 수강신청 오늘 18:00 마감", 날짜만 있으면 "계절학기 수강신청 내일까지" */
    static String deadlineTitle(NoticeAlertRow notice, Instant now) {
        LocalDate today = LocalDate.ofInstant(now, KoreanTime.ZONE);
        var deadline = notice.deadlineAt().atZone(KoreanTime.ZONE);
        String day = deadline.toLocalDate().equals(today) ? "오늘" : "내일";
        return notice.deadlineAllDay()
                ? notice.title() + " " + day + "까지"
                : notice.title() + " " + day + " " + deadline.format(TIME) + " 마감";
    }

    static boolean prepStartsOn(Card card, LocalDate day) {
        if (card == null) {
            return false;
        }
        return card.schedule().stream()
                .filter(item -> "PREP_START".equals(item.type()))
                .map(ScheduleItem::at)
                .anyMatch(at -> at != null && at.startsWith(day.toString()));
    }

    private static String deadlineText(NoticeAlertRow notice) {
        if (notice.deadlineAt() == null) {
            return null;
        }
        var deadline = notice.deadlineAt().atZone(KoreanTime.ZONE);
        return notice.deadlineAllDay() ? deadline.format(DATE) : deadline.format(DATE_TIME);
    }

    /** 본교 공지는 모두, 학과 공지는 주전공·추가 전공이 그 학과인 기기만(CLAUDE.md 9.3). */
    public static boolean seesSource(Device device, NoticeSource source) {
        if (source == null || source.getKind() == SourceKind.UNIVERSITY) {
            return true;
        }
        String site = DepartmentNames.normalize(source.getName());
        return site.equals(DepartmentNames.normalize(device.getMatchDepartment()))
                || device.getMatchExtraDepartments().stream().anyMatch(name -> site.equals(DepartmentNames.normalize(name)));
    }

    private List<Device> subscribedDevices() {
        List<String> ids = subscriptionRepository.findAll().stream().map(PushSubscription::getDeviceId).toList();
        return deviceRepository.findAllById(ids);
    }

    private List<Device> plannedDevices(Long noticeId) {
        List<String> ids = statusRepository.findAllByNoticeIdAndStatus(noticeId, MyStatus.PLANNED.name()).stream()
                .map(NoticeStatus::getDeviceId).toList();
        return deviceRepository.findAllById(ids);
    }

    private Map<NoticeAlertRow, List<Device>> plannedByNotice() {
        List<NoticeStatus> planned = statusRepository.findAllByStatus(MyStatus.PLANNED.name());
        if (planned.isEmpty()) {
            return Map.of();
        }
        Map<String, Device> devices = deviceRepository.findAllById(
                        planned.stream().map(NoticeStatus::getDeviceId).distinct().toList()).stream()
                .collect(Collectors.toMap(Device::getId, Function.identity()));
        Map<Long, NoticeAlertRow> notices = noticeRepository.findAlertRows(
                        planned.stream().map(NoticeStatus::getNoticeId).distinct().toList()).stream()
                .collect(Collectors.toMap(NoticeAlertRow::id, Function.identity()));
        return planned.stream()
                .filter(s -> devices.containsKey(s.getDeviceId()) && notices.containsKey(s.getNoticeId()))
                .collect(Collectors.groupingBy(s -> notices.get(s.getNoticeId()),
                        Collectors.mapping(s -> devices.get(s.getDeviceId()), Collectors.toList())));
    }
}
