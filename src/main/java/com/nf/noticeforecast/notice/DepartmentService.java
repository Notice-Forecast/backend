package com.nf.noticeforecast.notice;

import com.nf.noticeforecast.notice.dto.DepartmentListResponse;
import com.nf.noticeforecast.notice.dto.DepartmentListResponse.DepartmentItem;
import java.text.Collator;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class DepartmentService {

    private final NoticeSourceRepository noticeSourceRepository;

    @Transactional(readOnly = true)
    public DepartmentListResponse list() {
        Comparator<String> order = Comparator.comparingInt(DepartmentService::scriptGroup)
                .thenComparing(Collator.getInstance(Locale.KOREAN));
        List<DepartmentItem> items = noticeSourceRepository.findAllByEnabledTrueOrderByIdAsc().stream()
                .filter(NoticeSource::isEnabled)
                .filter(source -> source.getKind() == SourceKind.DEPARTMENT)
                .map(NoticeSource::getName)
                .sorted(order)
                .map(DepartmentItem::new)
                .toList();
        return new DepartmentListResponse(items);
    }

    private static int scriptGroup(String name) {
        return !name.isEmpty() && name.charAt(0) >= '가' && name.charAt(0) <= '힣' ? 0 : 1;
    }
}
