package org.yu.flow.module.oss.annotation;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import org.yu.flow.module.oss.support.FlowOssFileResolveSerializer;

import java.lang.annotation.*;

/**
 * 将文件 ID (fileId) 自动解析为包含完整元信息对象 (FlowOssFileResolvedDTO) 的 Jackson 序列化注解
 *
 * <p>使用示例：
 * <pre>
 *   &#64;FlowOssFileResolve
 *   private String contractFileId; // 序列化后自动输出为 FlowOssFileResolvedDTO JSON 对象
 * </pre>
 */
@Target({ElementType.FIELD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
@JsonSerialize(using = FlowOssFileResolveSerializer.class)
public @interface FlowOssFileResolve {

    /**
     * 场景编码 (可选)
     */
    String profile() default "";

    /**
     * URL 是否包含 HTTP/HTTPS 协议与 IP/Port/域名 (默认 false: 相对路径)
     */
    boolean absolute() default false;
}
