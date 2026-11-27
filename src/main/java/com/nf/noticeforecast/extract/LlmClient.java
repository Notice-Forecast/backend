package com.nf.noticeforecast.extract;

/** LLM 호출을 감춘다. 테스트에서는 가짜로 바꾼다(CLAUDE.md 13장). */
public interface LlmClient {

    /**
     * @throws LlmUnavailableException 네트워크 오류, 429, 5xx처럼 나중에 다시 시도할 오류
     * @throws LlmRejectedException    요청 자체가 거절된 오류(400 등). 다시 보내도 같다
     */
    LlmResult complete(LlmRequest request);
}
