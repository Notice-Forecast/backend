package com.nf.noticeforecast.notice.dto;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import java.util.Set;
import org.junit.jupiter.api.Test;

class NoticeStatusRequestTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void status_키가_없으면_status_필드_오류다() {
        Set<ConstraintViolation<NoticeStatusRequest>> violations = validator.validate(new NoticeStatusRequest());

        assertThat(violations).singleElement().satisfies(violation -> {
            assertThat(violation.getPropertyPath().toString()).isEqualTo("status");
            assertThat(violation.getMessage()).isEqualTo("status는 필수 값입니다.");
        });
    }

    @Test
    void status가_null이어도_키가_있으면_통과한다() {
        NoticeStatusRequest request = new NoticeStatusRequest();
        request.setStatus(null);

        assertThat(validator.validate(request)).isEmpty();
    }
}
