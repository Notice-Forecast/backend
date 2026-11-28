package com.nf.noticeforecast.forecast;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nf.noticeforecast.device.Device;
import com.nf.noticeforecast.device.EnrollmentStatus;
import com.nf.noticeforecast.extract.card.Card;
import com.nf.noticeforecast.extract.card.CardCondition;
import com.nf.noticeforecast.extract.card.CardDocument;
import com.nf.noticeforecast.forecast.dto.ForecastDetailResponse;
import com.nf.noticeforecast.forecast.dto.ForecastFollowResponse;
import com.nf.noticeforecast.forecast.dto.ForecastListResponse;
import com.nf.noticeforecast.global.error.BusinessException;
import com.nf.noticeforecast.global.error.ErrorCode;
import com.nf.noticeforecast.global.error.InvalidFieldException;
import com.nf.noticeforecast.global.time.KoreanTime;
import com.nf.noticeforecast.notice.ForecastNoticeRow;
import com.nf.noticeforecast.notice.NoticeRepository;
import com.nf.noticeforecast.notice.NoticeSource;
import com.nf.noticeforecast.notice.NoticeSourceRepository;
import com.nf.noticeforecast.notice.SourceKind;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class ForecastServiceTest {

    private static final Instant NOW = Instant.parse("2026-11-29T00:00:00Z");

    private final ForecastSeriesRepository seriesRepository = mock(ForecastSeriesRepository.class);
    private final SeriesFollowRepository followRepository = mock(SeriesFollowRepository.class);
    private final NoticeRepository noticeRepository = mock(NoticeRepository.class);
    private final NoticeSourceRepository sourceRepository = mock(NoticeSourceRepository.class);
    private final TermCalendarRepository termCalendarRepository = mock(TermCalendarRepository.class);
    private final ForecastService service = new ForecastService(
            seriesRepository, followRepository, noticeRepository, sourceRepository, termCalendarRepository,
            Clock.fixed(NOW, ZoneOffset.UTC));

    private NoticeSource univ;
    private NoticeSource csie;
    private NoticeSource biz;

    @BeforeEach
    void setUp() {
        univ = NoticeSource.of("가톨릭대학교", "https://www.catholic.ac.kr", "/ko/campuslife/notice.do", SourceKind.UNIVERSITY);
        ReflectionTestUtils.setField(univ, "id", 1L);
        csie = NoticeSource.of("컴퓨터정보공학부", "https://csie.catholic.ac.kr", "/csie/community/notice.do", SourceKind.DEPARTMENT);
        ReflectionTestUtils.setField(csie, "id", 2L);
        biz = NoticeSource.of("경영학과", "https://business.catholic.ac.kr", "/business/community/notice.do", SourceKind.DEPARTMENT);
        ReflectionTestUtils.setField(biz, "id", 3L);
        when(sourceRepository.findAll()).thenReturn(List.of(univ, csie, biz));
        when(termCalendarRepository.findAll()).thenReturn(List.of(
                TermCalendar.of("2026-1", LocalDate.of(2026, 3, 3), LocalDate.of(2026, 6, 22)),
                TermCalendar.of("2026-2", LocalDate.of(2026, 8, 31), LocalDate.of(2026, 12, 20)),
                TermCalendar.of("2027-1", LocalDate.of(2027, 3, 2), LocalDate.of(2027, 6, 21))));
    }

    private static ForecastSeries seed(long id, String name, String department, String category, SeriesAnchor anchor) {
        ForecastSeries series = ForecastSeries.startFrom(name, name, department, category, NOW);
        series.curate(name, anchor, true);
        ReflectionTestUtils.setField(series, "id", id);
        return series;
    }

    private static Device device(String dept) {
        Device d = Device.register("ABCD-EFGH", NOW);
        ReflectionTestUtils.setField(d, "id", "dev-1");
        d.updateMatchFilter(dept, List.of(), 3, EnrollmentStatus.ENROLLED);
        return d;
    }

    private static ForecastNoticeRow row(long id, long seriesId, long sourceId, LocalDate date, Card card) {
        return new ForecastNoticeRow(id, seriesId, sourceId, "공지",
                date.atStartOfDay(KoreanTime.ZONE).toInstant(), card);
    }

    @Test
    void 구독_목록과_추천_목록을_나누고_구독한_계열은_추천에서_뺀다() {
        ForecastSeries s1 = seed(1L, "복수전공 변경 신청", "학사지원팀", "ACADEMIC", SeriesAnchor.TERM_END);
        ForecastSeries s2 = seed(2L, "컴공 졸업시험 신청", "컴퓨터정보공학부", "DEPARTMENT", SeriesAnchor.TERM_START);
        ForecastSeries s3 = seed(3L, "경영 특강 안내", "경영학과", "DEPARTMENT", SeriesAnchor.TERM_START);
        when(seriesRepository.findAllBySeedTrueOrderByIdAsc()).thenReturn(List.of(s1, s2, s3));
        when(noticeRepository.findForecastNoticesBySeriesIds(List.of(1L, 2L, 3L))).thenReturn(List.of(
                row(10L, 1L, 1L, LocalDate.of(2026, 5, 25), null),
                row(11L, 2L, 2L, LocalDate.of(2026, 3, 10), null),
                row(12L, 3L, 3L, LocalDate.of(2026, 3, 10), null)));
        when(followRepository.findAllByDeviceId("dev-1")).thenReturn(List.of(
                SeriesFollow.of("dev-1", 1L, FollowNotify.IMMEDIATE, FollowSource.RECOMMENDED, NOW)));

        ForecastListResponse response = service.list(device("컴퓨터정보공학부"), null, null);

        assertThat(response.following()).extracting(ForecastListResponse.FollowingItem::seriesId).containsExactly(1L);
        assertThat(response.recommended()).extracting(ForecastListResponse.RecommendedItem::seriesId).containsExactly(2L);
    }

    @Test
    void 검색어_q가_있으면_추천_여부와_관계없이_이름으로_찾는다() {
        ForecastSeries s3 = seed(3L, "경영 특강 안내", "경영학과", "DEPARTMENT", SeriesAnchor.TERM_START);
        when(seriesRepository.findAllBySeedTrueOrderByIdAsc()).thenReturn(List.of(s3));
        when(noticeRepository.findForecastNoticesBySeriesIds(List.of(3L))).thenReturn(List.of(
                row(12L, 3L, 3L, LocalDate.of(2026, 3, 10), null)));
        when(followRepository.findAllByDeviceId("dev-1")).thenReturn(List.of());

        ForecastListResponse response = service.list(device("컴퓨터정보공학부"), "경영 특강", null);

        assertThat(response.recommended()).extracting(ForecastListResponse.RecommendedItem::seriesId).containsExactly(3L);
    }

    @Test
    void 상세는_지난_회차_카드의_서류로_prepare를_채우고_시드가_아니면_404다() {
        ForecastSeries s1 = seed(1L, "학술연구장학생 신청", "학생지원팀", "SCHOLARSHIP", SeriesAnchor.TERM_END);
        Card card = new Card("요약", true, List.of(new CardCondition("ENROLLMENT_STATUS", "IN", List.of("ENROLLED"), "근거", "본문")),
                List.of(), List.of(new CardDocument("성적증명서", "CERTIFICATE", null), new CardDocument("추천서", "RECOMMENDATION", null)), null);
        when(seriesRepository.findById(1L)).thenReturn(Optional.of(s1));
        when(noticeRepository.findForecastNoticesBySeriesIds(List.of(1L))).thenReturn(List.of(
                row(10L, 1L, 1L, LocalDate.of(2026, 5, 25), card)));
        when(followRepository.findByDeviceIdAndSeriesId("dev-1", 1L)).thenReturn(Optional.of(
                SeriesFollow.of("dev-1", 1L, FollowNotify.DAILY, FollowSource.RECOMMENDED, NOW)));

        ForecastDetailResponse detail = service.detail(device("컴퓨터정보공학부"), 1L);

        assertThat(detail.prepare()).containsExactly("성적증명서", "추천서");
        assertThat(detail.follow()).isEqualTo(new ForecastDetailResponse.FollowInfo(FollowNotify.DAILY));

        ForecastSeries nonSeed = ForecastSeries.startFrom("일반", "일반", "팀", null, NOW);
        when(seriesRepository.findById(99L)).thenReturn(Optional.of(nonSeed));
        assertThatThrownBy(() -> service.detail(null, 99L))
                .isInstanceOfSatisfying(BusinessException.class, e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.SERIES_NOT_FOUND));
    }

    @Test
    void 구독은_저장하고_이미_있으면_notify만_바꾸며_잘못된_interests는_400이다() {
        ForecastSeries s1 = seed(1L, "복수전공 변경 신청", "학사지원팀", "ACADEMIC", SeriesAnchor.TERM_END);
        when(seriesRepository.findById(1L)).thenReturn(Optional.of(s1));
        when(noticeRepository.findForecastNoticesBySeriesIds(List.of(1L))).thenReturn(List.of(
                row(10L, 1L, 1L, LocalDate.of(2026, 5, 25), null)));
        when(followRepository.findByDeviceIdAndSeriesId("dev-1", 1L)).thenReturn(Optional.empty());

        ForecastFollowResponse created = service.follow(device("컴퓨터정보공학부"), 1L, FollowNotify.IMMEDIATE, FollowSource.MANUAL);
        assertThat(created.notifyMode()).isEqualTo(FollowNotify.IMMEDIATE);
        assertThat(created.source()).isEqualTo(FollowSource.MANUAL);
        verify(followRepository).upsert("dev-1", 1L, "IMMEDIATE", "MANUAL", NOW);

        assertThatThrownBy(() -> service.follow(device("컴퓨터정보공학부"), 1L, FollowNotify.DAILY, null))
                .isInstanceOfSatisfying(InvalidFieldException.class, e -> assertThat(e.getField()).isEqualTo("notify"));

        service.unfollow("dev-1", 1L);
        verify(followRepository).deleteOne("dev-1", 1L);

        assertThatThrownBy(() -> ForecastService.parseInterests("INVALID_CAT"))
                .isInstanceOfSatisfying(InvalidFieldException.class, e -> assertThat(e.getField()).isEqualTo("interests"));
    }

    @Test
    void 자격_조건이_불충족이면_interests가_맞아도_추천하지_않는다() {
        ForecastSeries scholarship = seed(1L, "휴학생 장학", "학생지원팀", "SCHOLARSHIP", SeriesAnchor.TERM_START);
        Card leaveOnly = new Card("요약", true,
                List.of(new CardCondition("ENROLLMENT_STATUS", "IN", List.of("LEAVE"), "근거", "본문")),
                List.of(), List.of(), null);
        when(seriesRepository.findAllBySeedTrueOrderByIdAsc()).thenReturn(List.of(scholarship));
        when(noticeRepository.findForecastNoticesBySeriesIds(List.of(1L))).thenReturn(List.of(
                row(10L, 1L, 1L, LocalDate.of(2026, 3, 10), leaveOnly)));
        when(followRepository.findAllByDeviceId("dev-1")).thenReturn(List.of());

        ForecastListResponse response = service.list(device("컴퓨터정보공학부"), null, "SCHOLARSHIP");

        assertThat(response.recommended()).isEmpty();
    }

    @Test
    void interests에_DEPARTMENT가_있어도_다른_학과_계열은_추천하지_않는다() {
        ForecastSeries bizSeries = seed(3L, "경영 특강 안내", "경영학과", "DEPARTMENT", SeriesAnchor.TERM_START);
        when(seriesRepository.findAllBySeedTrueOrderByIdAsc()).thenReturn(List.of(bizSeries));
        when(noticeRepository.findForecastNoticesBySeriesIds(List.of(3L))).thenReturn(List.of(
                row(12L, 3L, 3L, LocalDate.of(2026, 3, 10), null)));
        when(followRepository.findAllByDeviceId("dev-1")).thenReturn(List.of());

        ForecastListResponse response = service.list(device("컴퓨터정보공학부"), null, "DEPARTMENT");

        assertThat(response.recommended()).isEmpty();
    }

    @Test
    void notify_필드는_JSON_직렬화와_검증_오류_모두_notify_이름으로_나간다() {
        tools.jackson.databind.json.JsonMapper mapper = tools.jackson.databind.json.JsonMapper.builder().build();
        String followJson = mapper.writeValueAsString(
                new ForecastFollowResponse(1L, FollowNotify.IMMEDIATE, FollowSource.RECOMMENDED));
        String detailFollowJson = mapper.writeValueAsString(new ForecastDetailResponse.FollowInfo(FollowNotify.DAILY));

        assertThat(followJson).contains("\"notify\":\"IMMEDIATE\"").doesNotContain("notifyMode");
        assertThat(detailFollowJson).contains("\"notify\":\"DAILY\"").doesNotContain("notifyMode");

        com.nf.noticeforecast.forecast.dto.ForecastFollowRequest parsed = mapper.readValue(
                "{\"notify\":\"DAILY\"}", com.nf.noticeforecast.forecast.dto.ForecastFollowRequest.class);
        assertThat(parsed.getNotify()).isEqualTo(FollowNotify.DAILY);

        var validator = jakarta.validation.Validation.buildDefaultValidatorFactory().getValidator();
        var violations = validator.validate(new com.nf.noticeforecast.forecast.dto.ForecastFollowRequest());
        assertThat(violations).singleElement().satisfies(v ->
                assertThat(v.getPropertyPath().toString()).isEqualTo("notify"));
    }
}
