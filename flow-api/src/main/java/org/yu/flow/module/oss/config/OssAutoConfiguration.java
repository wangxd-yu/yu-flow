package org.yu.flow.module.oss.config;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.ComponentScan;

/**
 * OSS 模块自动装配（依赖 MinIO SDK）。
 *
 * <p>宿主项目未引入 {@code io.minio:minio}，或显式关闭 {@code yu.flow.oss.enabled=false} 时跳过，
 * 避免 {@code OssStorageService} 等 Bean 在内省时因缺类导致
 * {@code Lookup method resolution failed}。</p>
 *
 * <p>注意：各 OSS 组件自身也标了 {@link ConditionalOnOssEnabled}，以防宿主额外
 * {@code @ComponentScan("org.yu.flow")} 时绕过本配置的扫描控制。</p>
 */
@AutoConfiguration
@ConditionalOnOssEnabled
@ComponentScan(basePackages = "org.yu.flow.module.oss")
public class OssAutoConfiguration {
}
