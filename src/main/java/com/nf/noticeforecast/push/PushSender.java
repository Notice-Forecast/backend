package com.nf.noticeforecast.push;

import com.nf.noticeforecast.device.PushSubscription;

/** 웹 푸시 발송을 감춘다. 테스트에서는 가짜로 바꾼다(CLAUDE.md 13장). */
public interface PushSender {

    enum Outcome {
        /** 푸시 서버가 받았다(2xx) */
        SENT,
        /** 구독이 만료됐다(404·410). 구독을 지운다 */
        GONE,
        /** 그 밖의 실패(네트워크, 429, 5xx 등). 다음에 다시 보낼 수 있다 */
        FAILED,
        /** VAPID 키가 없어 보내지 않았다 */
        NOT_CONFIGURED
    }

    record Result(Outcome outcome, int status) {
    }

    Result send(PushSubscription subscription, String payloadJson);
}
