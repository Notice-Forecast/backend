package com.nf.noticeforecast.device;

import java.security.SecureRandom;
import org.springframework.stereotype.Component;

@Component
public class RestoreCodeGenerator {

    // 손으로 옮겨 적을 때 헷갈리는 0/O, 1/I를 뺐다.
    private static final String ALPHABET = "23456789ABCDEFGHJKLMNPQRSTUVWXYZ";

    private final SecureRandom random = new SecureRandom();

    public String generate() {
        StringBuilder code = new StringBuilder();
        for (int i = 0; i < 8; i++) {
            if (i == 4) {
                code.append('-');
            }
            code.append(ALPHABET.charAt(random.nextInt(ALPHABET.length())));
        }
        return code.toString();
    }
}
