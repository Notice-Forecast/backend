package com.nf.noticeforecast.notice;

import com.nf.noticeforecast.notice.dto.DepartmentListResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "학과")
@RestController
@RequiredArgsConstructor
@RequestMapping(value = "/api/v1/departments", produces = MediaType.APPLICATION_JSON_VALUE)
public class DepartmentController {

    private final DepartmentService departmentService;

    @Operation(summary = "학과 목록",
            description = "공지를 수집하는 학과 이름 목록을 가나다순으로 돌려줍니다. 온보딩·설정 선택지에 그대로 사용합니다.")
    @GetMapping
    public DepartmentListResponse list() {
        return departmentService.list();
    }
}
