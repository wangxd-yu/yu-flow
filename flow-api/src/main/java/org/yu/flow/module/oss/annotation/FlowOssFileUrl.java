package org.yu.flow.module.oss.annotation;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import org.yu.flow.module.oss.support.FlowOssFileUrlSerializer;

import java.lang.annotation.*;

/**
 * 将文件 ID (fileId) 自动解析为文件访问 URL 的 Jackson 序列化注解
 *
 * <p>使用示例：
 * <pre>
 *   &#64;FlowOssFileUrl
 *   private String avatarFileId; // 序列化后自动输出为完整或相对 URL 字符串
 * </pre>
 */
@Target({ElementType.FIELD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
@JsonSerialize(using = FlowOssFileUrlSerializer.class)
public @interface FlowOssFileUrl {

    /**
     * 场景编码 (可选)
     */
    String profile() default "";

    /**
     * URL 是否包含 HTTP/HTTPS 协议与 IP/Port/域名
     * <ul>
     *   <li>{@code false} (默认): 返回前端可直接使用的相对路径 (如 /opcenter-store/key 或 /flow-api/oss/objects/id/download)</li>
     *   <li>{@code true}: 返回带协议与域名的完整绝对 URL (如 http://192.168.1.1:9000/bucket/key)</li>
     * </ul>
     */
    boolean absolute() default false;
}
