package com.nf.noticeforecast.notice.dto;

import jakarta.validation.Constraint;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import jakarta.validation.Payload;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * status 키가 빠진 요청을 막는다. 오류를 status 필드에 붙여 fields 키가 "status"로 나가게 한다.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = StatusKeyRequired.Validator.class)
public @interface StatusKeyRequired {

    String message() default "status는 필수 값입니다.";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};

    class Validator implements ConstraintValidator<StatusKeyRequired, NoticeStatusRequest> {

        @Override
        public boolean isValid(NoticeStatusRequest request, ConstraintValidatorContext context) {
            if (request == null || request.isStatusPresent()) {
                return true;
            }
            context.disableDefaultConstraintViolation();
            context.buildConstraintViolationWithTemplate(context.getDefaultConstraintMessageTemplate())
                    .addPropertyNode("status")
                    .addConstraintViolation();
            return false;
        }
    }
}
