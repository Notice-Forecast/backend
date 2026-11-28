package com.nf.noticeforecast.forecast;

import com.nf.noticeforecast.device.Device;
import com.nf.noticeforecast.extract.card.Card;
import com.nf.noticeforecast.extract.card.CardDocument;
import com.nf.noticeforecast.forecast.dto.ForecastDetailResponse;
import com.nf.noticeforecast.forecast.dto.ForecastDetailResponse.FollowInfo;
import com.nf.noticeforecast.forecast.dto.ForecastDetailResponse.PastRound;
import com.nf.noticeforecast.forecast.dto.ForecastFollowResponse;
import com.nf.noticeforecast.forecast.dto.ForecastListResponse;
import com.nf.noticeforecast.forecast.dto.ForecastListResponse.FollowingItem;
import com.nf.noticeforecast.forecast.dto.ForecastListResponse.RecommendedItem;
import com.nf.noticeforecast.global.error.BusinessException;
import com.nf.noticeforecast.global.error.ErrorCode;
import com.nf.noticeforecast.global.error.InvalidFieldException;
import com.nf.noticeforecast.global.time.KoreanTime;
import com.nf.noticeforecast.notice.DepartmentNames;
import com.nf.noticeforecast.notice.ForecastNoticeRow;
import com.nf.noticeforecast.notice.NoticeCategory;
import com.nf.noticeforecast.notice.NoticeRepository;
import com.nf.noticeforecast.notice.NoticeSource;
import com.nf.noticeforecast.notice.NoticeSourceRepository;
import com.nf.noticeforecast.notice.SourceKind;
import com.nf.noticeforecast.push.NoticeAlerts;
import com.nf.noticeforecast.push.ServerEligibility;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ForecastService {

    private static final Comparator<LocalDate> NULLS_LAST = Comparator.nullsLast(Comparator.naturalOrder());

    private final ForecastSeriesRepository seriesRepository;
    private final SeriesFollowRepository followRepository;
    private final NoticeRepository noticeRepository;
    private final NoticeSourceRepository sourceRepository;
    private final TermCalendarRepository termCalendarRepository;
    private final Clock clock;

    @Transactional(readOnly = true)
    public ForecastListResponse list(Device device, String query, String interestsRaw) {
        Set<NoticeCategory> interests = parseInterests(interestsRaw);
        String normalizedQuery = query == null || query.isBlank() ? null : normalizeQuery(query);
        List<ForecastSeries> seeds = seriesRepository.findAllBySeedTrueOrderByIdAsc();
        if (seeds.isEmpty()) {
            return new ForecastListResponse(List.of(), List.of());
        }
        List<Long> seedIds = seeds.stream().map(ForecastSeries::getId).toList();
        Map<Long, List<ForecastNoticeRow>> noticesBySeries = noticeRepository.findForecastNoticesBySeriesIds(seedIds)
                .stream().collect(Collectors.groupingBy(ForecastNoticeRow::seriesId));
        List<TermCalendar> terms = termCalendarRepository.findAll();
        Map<Long, NoticeSource> sourcesById = sourceRepository.findAll().stream()
                .collect(Collectors.toMap(NoticeSource::getId, Function.identity()));
        Map<Long, SeriesFollow> followsBySeries = followRepository.findAllByDeviceId(device.getId()).stream()
                .collect(Collectors.toMap(SeriesFollow::getSeriesId, Function.identity()));
        LocalDate today = LocalDate.ofInstant(clock.instant(), KoreanTime.ZONE);

        List<FollowingItem> following = new ArrayList<>();
        List<RecommendedItem> recommended = new ArrayList<>();
        for (ForecastSeries series : seeds) {
            if (normalizedQuery != null && !normalizeQuery(series.getName()).contains(normalizedQuery)) {
                continue;
            }
            List<ForecastNoticeRow> rows = noticesBySeries.getOrDefault(series.getId(), List.of());
            ForecastCalculator.Prediction prediction = ForecastCalculator.predict(series.getAnchor(), rows, terms, today);
            NoticeCategory category = parseCategory(series.getCategory());
            SeriesFollow follow = followsBySeries.get(series.getId());
            if (follow != null) {
                following.add(new FollowingItem(series.getId(), series.getName(), category,
                        prediction.expectedFrom(), prediction.expectedTo(), prediction.confidence(),
                        prediction.past().size(), follow.getSource(), follow.getNotify()));
            } else if (normalizedQuery != null || isRecommended(series, rows, device, sourcesById, interests)) {
                recommended.add(new RecommendedItem(series.getId(), series.getName(), category,
                        prediction.expectedFrom(), prediction.expectedTo(), prediction.confidence(),
                        prediction.past().size()));
            }
        }
        following.sort(Comparator.comparing(FollowingItem::expectedFrom, NULLS_LAST)
                .thenComparing(FollowingItem::seriesId));
        recommended.sort(Comparator.comparing(RecommendedItem::expectedFrom, NULLS_LAST)
                .thenComparing(RecommendedItem::seriesId));
        return new ForecastListResponse(following, recommended);
    }

    @Transactional(readOnly = true)
    public ForecastDetailResponse detail(Device device, Long seriesId) {
        ForecastSeries series = findSeed(seriesId);
        List<ForecastNoticeRow> rows = noticeRepository.findForecastNoticesBySeriesIds(List.of(seriesId));
        List<TermCalendar> terms = termCalendarRepository.findAll();
        LocalDate today = LocalDate.ofInstant(clock.instant(), KoreanTime.ZONE);
        ForecastCalculator.Prediction prediction = ForecastCalculator.predict(series.getAnchor(), rows, terms, today);
        List<PastRound> past = prediction.past().stream()
                .map(r -> new PastRound(r.term(), r.postedAt(), r.noticeId()))
                .toList();
        List<String> prepare = extractPrepare(rows);
        FollowInfo follow = device == null ? null
                : followRepository.findByDeviceIdAndSeriesId(device.getId(), seriesId)
                        .map(f -> new FollowInfo(f.getNotify()))
                        .orElse(null);
        return new ForecastDetailResponse(series.getId(), series.getName(), parseCategory(series.getCategory()),
                prediction.expectedFrom(), prediction.expectedTo(), prediction.confidence(), prediction.reason(),
                past, prepare, follow);
    }

    @Transactional
    public ForecastFollowResponse follow(Device device, Long seriesId, FollowNotify notify, FollowSource requestedSource) {
        if (notify != FollowNotify.IMMEDIATE) {
            throw new InvalidFieldException("notify", "현재는 IMMEDIATE만 지원합니다.");
        }
        ForecastSeries series = findSeed(seriesId);
        SeriesFollow existing = followRepository.findByDeviceIdAndSeriesId(device.getId(), seriesId).orElse(null);
        FollowSource source;
        if (existing != null) {
            source = existing.getSource();
        } else if (requestedSource != null) {
            source = requestedSource;
        } else {
            List<ForecastNoticeRow> rows = noticeRepository.findForecastNoticesBySeriesIds(List.of(seriesId));
            Map<Long, NoticeSource> sourcesById = sourceRepository.findAll().stream()
                    .collect(Collectors.toMap(NoticeSource::getId, Function.identity()));
            source = isRecommended(series, rows, device, sourcesById, Set.of())
                    ? FollowSource.RECOMMENDED : FollowSource.MANUAL;
        }
        followRepository.upsert(device.getId(), seriesId, notify.name(), source.name(), clock.instant());
        return new ForecastFollowResponse(seriesId, notify, source);
    }

    @Transactional
    public void unfollow(String deviceId, Long seriesId) {
        followRepository.deleteOne(deviceId, seriesId);
    }

    /**
     * 추천 규칙(CLAUDE.md 10장, 11/28·11/29 결정):
     * 학과 계열은 그 학과가 주전공·추가 전공일 때, 본교 계열은 모두에게 추천.
     * 최근 회차에 카드가 있으면 자격 조건이 matchFilter로 불충족이면 interests가 맞아도 추천하지 않는다.
     */
    static boolean isRecommended(ForecastSeries series, List<ForecastNoticeRow> rows, Device device,
                                 Map<Long, NoticeSource> sourcesById, Set<NoticeCategory> interests) {
        if (!matchesDepartment(series, rows, device, sourcesById)) {
            return false;
        }
        Card latestCard = latestCard(rows);
        if (latestCard != null && device != null && device.hasMatchFilter()
                && !ServerEligibility.notExcluded(latestCard, device)) {
            return false;
        }
        NoticeCategory category = parseCategory(series.getCategory());
        if (category != null && interests.contains(category)) {
            return true;
        }
        return true;
    }

    private static boolean matchesDepartment(ForecastSeries series, List<ForecastNoticeRow> rows, Device device,
                                             Map<Long, NoticeSource> sourcesById) {
        if (rows.isEmpty()) {
            return parseCategory(series.getCategory()) != NoticeCategory.DEPARTMENT
                    || matchesDepartmentName(device, series.getDepartment());
        }
        boolean anyUniversity = rows.stream()
                .map(r -> sourcesById.get(r.sourceId()))
                .anyMatch(s -> s == null || s.getKind() == SourceKind.UNIVERSITY);
        if (anyUniversity) {
            return true;
        }
        if (device == null || !device.hasMatchFilter()) {
            return false;
        }
        return rows.stream()
                .map(r -> sourcesById.get(r.sourceId()))
                .anyMatch(s -> NoticeAlerts.seesSource(device, s));
    }

    private static boolean matchesDepartmentName(Device device, String department) {
        if (device == null || !device.hasMatchFilter() || department == null || department.isBlank()) {
            return false;
        }
        String target = DepartmentNames.normalize(department);
        return target.equals(DepartmentNames.normalize(device.getMatchDepartment()))
                || device.getMatchExtraDepartments().stream().anyMatch(d -> target.equals(DepartmentNames.normalize(d)));
    }

    private static Card latestCard(List<ForecastNoticeRow> rows) {
        for (int i = rows.size() - 1; i >= 0; i--) {
            if (rows.get(i).card() != null) {
                return rows.get(i).card();
            }
        }
        return null;
    }

    static List<String> extractPrepare(List<ForecastNoticeRow> rows) {
        for (int i = rows.size() - 1; i >= 0; i--) {
            Card card = rows.get(i).card();
            if (card != null && card.documents() != null && !card.documents().isEmpty()) {
                return card.documents().stream()
                        .map(CardDocument::name)
                        .filter(name -> name != null && !name.isBlank())
                        .map(String::strip)
                        .collect(Collectors.toCollection(LinkedHashSet::new))
                        .stream().toList();
            }
        }
        return List.of();
    }

    static Set<NoticeCategory> parseInterests(String raw) {
        if (raw == null || raw.isBlank()) {
            return Set.of();
        }
        Set<NoticeCategory> categories = EnumSet.noneOf(NoticeCategory.class);
        for (String token : raw.split(",")) {
            String trimmed = token.strip();
            if (trimmed.isEmpty()) {
                continue;
            }
            try {
                categories.add(NoticeCategory.valueOf(trimmed.toUpperCase(Locale.ROOT)));
            } catch (IllegalArgumentException e) {
                throw new InvalidFieldException("interests",
                        Arrays.stream(NoticeCategory.values()).map(Enum::name).collect(Collectors.joining(", "))
                                + " 중 하나여야 합니다.");
            }
        }
        return categories;
    }

    private ForecastSeries findSeed(Long seriesId) {
        ForecastSeries series = seriesRepository.findById(seriesId)
                .orElseThrow(() -> new BusinessException(ErrorCode.SERIES_NOT_FOUND));
        if (!series.isSeed()) {
            throw new BusinessException(ErrorCode.SERIES_NOT_FOUND);
        }
        return series;
    }

    private static NoticeCategory parseCategory(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return NoticeCategory.valueOf(raw);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static String normalizeQuery(String text) {
        return text.replaceAll("\\s+", "").toLowerCase(Locale.ROOT);
    }
}
