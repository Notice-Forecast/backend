package com.nf.noticeforecast.notice;

import com.nf.noticeforecast.global.error.BusinessException;
import com.nf.noticeforecast.global.error.ErrorCode;
import com.nf.noticeforecast.global.time.KoreanTime;
import com.nf.noticeforecast.notice.dto.NoticeStatusResponse;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class NoticeStatusService {

    private final NoticeRepository noticeRepository;
    private final NoticeStatusRepository noticeStatusRepository;

    @Transactional
    public NoticeStatusResponse change(String deviceId, Long noticeId, String requestedStatus) {
        MyStatus status = requestedStatus == null ? null : MyStatus.parse(requestedStatus);
        if (!noticeRepository.existsById(noticeId)) {
            throw new BusinessException(ErrorCode.NOTICE_NOT_FOUND);
        }
        if (status == null) {
            noticeStatusRepository.deleteOne(deviceId, noticeId);
            return new NoticeStatusResponse(noticeId, null, null);
        }
        Instant now = Instant.now();
        noticeStatusRepository.upsert(deviceId, noticeId, status.name(), now);
        return new NoticeStatusResponse(noticeId, status, KoreanTime.from(now));
    }
}
