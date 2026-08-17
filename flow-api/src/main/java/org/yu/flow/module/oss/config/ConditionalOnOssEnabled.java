package org.yu.flow.module.oss.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * OSS 模块启用条件：classpath 存在 MinIO，且 {@code yu.flow.oss.enabled=true}（默认 true）。
 *
 * <p>必须标在各个 {@code @Service}/{@code @Component} 上：宿主若自行 {@code @ComponentScan("org.yu.flow")}，
 * 仅靠 {@link OssAutoConfiguration} 挡不住，缺 MinIO 时仍会内省 {@code OssStorageService} 导致启动失败。</p>
 */
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
@ConditionalOnClass(name = "io.minio.MinioClient")
@ConditionalOnProperty(prefix = "yu.flow.oss", name = "enabled", havingValue = "true", matchIfMissing = true)
public @interface ConditionalOnOssEnabled {
}
