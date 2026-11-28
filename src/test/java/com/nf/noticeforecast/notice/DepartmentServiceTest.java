package com.nf.noticeforecast.notice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.nf.noticeforecast.notice.dto.DepartmentListResponse;
import com.nf.noticeforecast.notice.dto.DepartmentListResponse.DepartmentItem;
import java.util.List;
import org.junit.jupiter.api.Test;

class DepartmentServiceTest {

    private final NoticeSourceRepository repository = mock(NoticeSourceRepository.class);
    private final DepartmentService service = new DepartmentService(repository);

    private static NoticeSource source(String name, SourceKind kind, boolean enabled) {
        NoticeSource source = NoticeSource.of(name, "https://example.catholic.ac.kr", "/notice.do", kind);
        source.update(name, kind, enabled);
        return source;
    }

    @Test
    void 활성화된_학과만_가나다순으로_저장된_이름_그대로_돌려준다() {
        when(repository.findAllByEnabledTrueOrderByIdAsc()).thenReturn(List.of(
                source("가톨릭대학교", SourceKind.UNIVERSITY, true),
                source("컴퓨터정보공학부", SourceKind.DEPARTMENT, true),
                source("공간디자인ㆍ소비자학과", SourceKind.DEPARTMENT, true),
                source("경영학과", SourceKind.DEPARTMENT, true),
                source("교직과", SourceKind.DEPARTMENT, true),
                source("AI의공학과", SourceKind.DEPARTMENT, true),
                source("비활성학과", SourceKind.DEPARTMENT, false),
                source("단과대학", SourceKind.COLLEGE, true)));

        DepartmentListResponse response = service.list();

        assertThat(response.items()).extracting(DepartmentItem::name)
                .containsExactly("경영학과", "공간디자인ㆍ소비자학과", "교직과", "컴퓨터정보공학부", "AI의공학과");
    }
}
