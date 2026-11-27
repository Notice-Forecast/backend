package com.nf.noticeforecast.attachment;

/** @param text 마스킹한 텍스트. status가 READ일 때만 채운다 */
public record AttachmentText(String name, Status status, String text) {

    public enum Status {
        /** 텍스트를 읽었다 */
        READ,
        /** 명단성 파일 이름이라 받지 않았다(11/26 결정) */
        SKIPPED_ROSTER,
        /** robots.txt 제한이거나 학교 밖으로 넘겨 보내서 받지 않았다 */
        BLOCKED,
        /** 10MB를 넘어 받지 않았다 */
        TOO_LARGE,
        /** PDF·HWP·HWPX가 아닌 형식 */
        UNSUPPORTED,
        /** 받지 못했거나 텍스트를 뽑지 못했다 */
        UNREADABLE
    }

    public static AttachmentText of(String name, Status status) {
        return new AttachmentText(name, status, null);
    }

    public boolean read() {
        return status == Status.READ && text != null && !text.isBlank();
    }
}
