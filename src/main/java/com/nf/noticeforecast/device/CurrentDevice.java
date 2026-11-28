package com.nf.noticeforecast.device;

import io.swagger.v3.oas.annotations.Parameter;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
@Parameter(hidden = true)
public @interface CurrentDevice {

    /** false면 X-Device-Id가 없을 때 null을 넘긴다. 헤더가 있는데 등록되지 않은 ID면 그래도 404다. */
    boolean required() default true;
}
