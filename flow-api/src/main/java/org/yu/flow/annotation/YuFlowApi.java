package org.yu.flow.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Yu Flow 内部 API 标识注解，
 * 用于隔离异常处理机制，避免侵入宿主系统的全局异常处理。
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface YuFlowApi {
}
