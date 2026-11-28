package com.nf.noticeforecast.push;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nf.noticeforecast.device.Device;
import com.nf.noticeforecast.device.DeviceRepository;
import com.nf.noticeforecast.device.EnrollmentStatus;
import com.nf.noticeforecast.device.PushSubscription;
import com.nf.noticeforecast.device.PushSubscriptionRepository;
import com.nf.noticeforecast.extract.card.Card;
import com.nf.noticeforecast.extract.card.CardCondition;
import com.nf.noticeforecast.extract.card.ScheduleItem;
import com.nf.noticeforecast.notice.ExtractionStatus;
import com.nf.noticeforecast.notice.Notice;
import com.nf.noticeforecast.notice.NoticeAlertRow;
import com.nf.noticeforecast.notice.NoticeRepository;
import com.nf.noticeforecast.notice.NoticeSource;
import com.nf.noticeforecast.notice.NoticeSourceRepository;
import com.nf.noticeforecast.notice.NoticeStatusRepository;
import com.nf.noticeforecast.notice.SourceKind;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class NoticeAlertsTest {

    private static final Instant NOW = Instant.parse("2026-11-28T03:00:00Z"); // 11/28 12:00 KST

    private static Notice notice(Instant posted, Instant deadline, boolean allDay, Card card) {
        Notice notice = Notice.builder().sourceId(1L).articleNo(1L).title("계절학기 수강신청").postedAt(posted)
                .sourceUrl("https://www.catholic.ac.kr").attachments(List.of()).deadlineAt(deadline)
                .extractionStatus(ExtractionStatus.DONE).now(posted).build();
        ReflectionTestUtils.setField(notice, "id", 10L);
        ReflectionTestUtils.setField(notice, "deadlineAllDay", allDay);
        ReflectionTestUtils.setField(notice, "card", card);
        return notice;
    }

    private static Card card(CardCondition... conditions) {
        return new Card("겨울 계절학기 수강신청 안내", true, List.of(conditions),
                List.of(new ScheduleItem("PREP_START", "준비", "2026-11-28", true, "RULE")), List.of(), null);
    }

    @Test
    void 마감_임박은_24시간_안이고_날짜만_있으면_전날부터_그날까지다() {
        assertThat(NoticeAlerts.deadlineSoon(NOW.plusSeconds(3_600 * 23), false, NOW)).isTrue();
        assertThat(NoticeAlerts.deadlineSoon(NOW.plusSeconds(3_600 * 25), false, NOW)).isFalse();
        assertThat(NoticeAlerts.deadlineSoon(NOW.minusSeconds(60), false, NOW)).isFalse();
        Instant nov29 = Instant.parse("2026-11-28T15:00:00Z"); // 11/29 00:00 KST
        assertThat(NoticeAlerts.deadlineSoon(nov29, true, NOW)).isTrue();
        Instant nov30 = Instant.parse("2026-11-29T15:00:00Z");
        assertThat(NoticeAlerts.deadlineSoon(nov30, true, NOW)).isFalse();
    }

    @Test
    void 마감_임박_제목은_오늘_내일과_시각을_붙인다() {
        assertThat(NoticeAlerts.deadlineTitle(new NoticeAlertRow(10L, "계절학기 수강신청",
                Instant.parse("2026-11-28T09:00:00Z"), false, 1, null), NOW)).isEqualTo("계절학기 수강신청 오늘 18:00 마감");
        assertThat(NoticeAlerts.deadlineTitle(new NoticeAlertRow(10L, "계절학기 수강신청",
                Instant.parse("2026-11-28T15:00:00Z"), true, 1, null), NOW)).isEqualTo("계절학기 수강신청 내일까지");
    }

    @Test
    void 준비_시작일이_오늘인지_본다() {
        assertThat(NoticeAlerts.prepStartsOn(card(), LocalDate.of(2026, 11, 28))).isTrue();
        assertThat(NoticeAlerts.prepStartsOn(card(), LocalDate.of(2026, 11, 29))).isFalse();
    }

    @Test
    void 새_공지는_48시간_이내이고_matchFilter가_맞고_상태가_없는_기기에만_보낸다() {
        PushDispatcher dispatcher = mock(PushDispatcher.class);
        DeviceRepository devices = mock(DeviceRepository.class);
        PushSubscriptionRepository subscriptions = mock(PushSubscriptionRepository.class);
        NoticeSourceRepository sources = mock(NoticeSourceRepository.class);
        NoticeStatusRepository statuses = mock(NoticeStatusRepository.class);
        NoticeAlerts alerts = new NoticeAlerts(dispatcher, devices, subscriptions, mock(NoticeRepository.class), sources,
                statuses, Clock.fixed(NOW, ZoneOffset.UTC));

        Device match = Device.register("AAAA-AAAA", NOW);
        ReflectionTestUtils.setField(match, "id", "match");
        match.updateMatchFilter("컴퓨터정보공학부", List.of(), 3, EnrollmentStatus.ENROLLED);
        Device other = Device.register("BBBB-BBBB", NOW);
        ReflectionTestUtils.setField(other, "id", "other");
        other.updateMatchFilter("경영학과", List.of(), 3, EnrollmentStatus.ENROLLED);
        Device noFilter = Device.register("CCCC-CCCC", NOW);
        ReflectionTestUtils.setField(noFilter, "id", "none");
        PushSubscription subscription = mock(PushSubscription.class);
        when(subscription.getDeviceId()).thenReturn("match");
        when(subscriptions.findAll()).thenReturn(List.of(subscription));
        when(devices.findAllById(any())).thenReturn(List.of(match, other, noFilter));
        when(sources.findById(1L)).thenReturn(Optional.of(NoticeSource.of("가톨릭대학교", "https://www.catholic.ac.kr",
                "/ko/campuslife/notice.do", SourceKind.UNIVERSITY)));
        when(statuses.findAllByNoticeId(10L)).thenReturn(List.of());
        when(dispatcher.dispatch(any(), any(), isNull(), anyInt())).thenReturn(PushDispatcher.Delivery.SENT);

        Card csieOnly = card(new CardCondition("DEPARTMENT", "IN", List.of("컴퓨터정보공학부"), "근거", "본문"));
        alerts.sendNewNotice(notice(NOW.minusSeconds(3_600), null, false, csieOnly));
        verify(dispatcher).dispatch(eq(match), any(), isNull(), eq(0));
        verify(dispatcher, never()).dispatch(eq(other), any(), any(), anyInt());
        verify(dispatcher, never()).dispatch(eq(noFilter), any(), any(), anyInt());

        alerts.sendNewNotice(notice(NOW.minusSeconds(3_600 * 49), null, false, csieOnly));
        verify(dispatcher, org.mockito.Mockito.times(1)).dispatch(eq(match), any(), isNull(), eq(0));
    }

    @Test
    void 게시_3일_지난_공지도_자격_조건이_바뀌면_마감_전에는_새로_해당한_기기에_새_공지를_보낸다() {
        PushDispatcher dispatcher = mock(PushDispatcher.class);
        DeviceRepository devices = mock(DeviceRepository.class);
        PushSubscriptionRepository subscriptions = mock(PushSubscriptionRepository.class);
        NoticeSourceRepository sources = mock(NoticeSourceRepository.class);
        NoticeStatusRepository statuses = mock(NoticeStatusRepository.class);
        NoticeAlerts alerts = new NoticeAlerts(dispatcher, devices, subscriptions, mock(NoticeRepository.class), sources,
                statuses, Clock.fixed(NOW, ZoneOffset.UTC));

        Device match = Device.register("AAAA-AAAA", NOW);
        ReflectionTestUtils.setField(match, "id", "match");
        match.updateMatchFilter("컴퓨터정보공학부", List.of(), 3, EnrollmentStatus.ENROLLED);
        PushSubscription subscription = mock(PushSubscription.class);
        when(subscription.getDeviceId()).thenReturn("match");
        when(subscriptions.findAll()).thenReturn(List.of(subscription));
        when(devices.findAllById(List.of("match"))).thenReturn(List.of(match));
        when(sources.findById(1L)).thenReturn(Optional.of(NoticeSource.of("가톨릭대학교", "https://www.catholic.ac.kr",
                "/ko/campuslife/notice.do", SourceKind.UNIVERSITY)));
        when(statuses.findAllByNoticeId(10L)).thenReturn(List.of());
        when(statuses.findAllByNoticeIdAndStatus(10L, "PLANNED")).thenReturn(List.of());
        when(dispatcher.dispatch(any(), any(), isNull(), anyInt())).thenReturn(PushDispatcher.Delivery.SENT);

        List<com.nf.noticeforecast.notice.CardDiff.CardChange> condChange = List.of(
                new com.nf.noticeforecast.notice.CardDiff.CardChange("CONDITIONS", "old", "new"));
        Notice openOld = notice(NOW.minusSeconds(86_400L * 3), NOW.plusSeconds(86_400L), false, card());
        alerts.onCardSaved(openOld, condChange);
        verify(dispatcher, org.mockito.Mockito.times(1)).dispatch(eq(match), any(), isNull(), eq(0));

        Notice closedOld = notice(NOW.minusSeconds(86_400L * 3), NOW.minusSeconds(3_600), false, card());
        alerts.onCardSaved(closedOld, condChange);
        verify(dispatcher, org.mockito.Mockito.times(1)).dispatch(eq(match), any(), isNull(), eq(0));
    }

    @Test
    void 마감_임박과_준비_시작은_revision과_무관하게_0으로_발송한다() {
        PushDispatcher dispatcher = mock(PushDispatcher.class);
        DeviceRepository devices = mock(DeviceRepository.class);
        NoticeRepository notices = mock(NoticeRepository.class);
        NoticeStatusRepository statuses = mock(NoticeStatusRepository.class);
        NoticeAlerts alerts = new NoticeAlerts(dispatcher, devices, mock(PushSubscriptionRepository.class), notices,
                mock(NoticeSourceRepository.class), statuses, Clock.fixed(NOW, ZoneOffset.UTC));

        Device device = Device.register("AAAA-AAAA", NOW);
        ReflectionTestUtils.setField(device, "id", "d1");
        com.nf.noticeforecast.notice.NoticeStatus status = mock(com.nf.noticeforecast.notice.NoticeStatus.class);
        when(status.getDeviceId()).thenReturn("d1");
        when(status.getNoticeId()).thenReturn(10L);
        when(statuses.findAllByStatus("PLANNED")).thenReturn(List.of(status));
        when(devices.findAllById(any())).thenReturn(List.of(device));
        when(notices.findAlertRows(any())).thenReturn(List.of(
                new NoticeAlertRow(10L, "계절학기 수강신청", NOW.plusSeconds(3_600), false, 3, card())));

        alerts.sendDeadlines();
        alerts.sendPrepStarts();

        verify(dispatcher, org.mockito.Mockito.times(2)).dispatch(eq(device), any(), isNull(), eq(0));
    }
}
