package com.nf.noticeforecast.notice;

import java.util.regex.Pattern;

/** 학과 이름은 가운뎃점(ㆍ, ·, •)과 공백을 빼고 비교한다. 학교 사이트에 두 표기가 섞여 있다(CLAUDE.md 6.3, 11/27 결정). */
public final class DepartmentNames {

    private static final Pattern IGNORED = Pattern.compile("[ㆍ·•\s]");

    private DepartmentNames() {
    }

    public static String normalize(String name) {
        return name == null ? "" : IGNORED.matcher(name).replaceAll("");
    }
}
