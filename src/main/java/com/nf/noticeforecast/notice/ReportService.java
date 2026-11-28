package com.nf.noticeforecast.notice;

import com.nf.noticeforecast.global.error.BusinessException;
import com.nf.noticeforecast.global.error.ErrorCode;
import com.nf.noticeforecast.notice.dto.ReportRequest;
import java.time.Clock;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ReportService {

    private final NoticeRepository noticeRepository;
    private final ReportRepository reportRepository;
    private final Clock clock;

    @Transactional
    public void report(String deviceId, Long noticeId, ReportRequest request) {
        if (!noticeRepository.existsById(noticeId)) {
            throw new BusinessException(ErrorCode.NOTICE_NOT_FOUND);
        }
        String comment = request.comment() == null || request.comment().isBlank() ? null : request.comment().strip();
        reportRepository.upsert(noticeId, deviceId, request.reason().name(), comment, clock.instant());
    }
}
