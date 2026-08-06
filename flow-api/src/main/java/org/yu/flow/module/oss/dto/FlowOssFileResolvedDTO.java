package org.yu.flow.module.oss.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

import java.io.Serializable;

/**
 * OSS 文件高阶信息解析 DTO（提供给 @FlowOssFileResolve 注解反序列化为对象使用）
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Accessors(chain = true)
public class FlowOssFileResolvedDTO implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 文件 ID */
    private String id;

    /** 原始文件名 */
    private String originalName;

    /** 文件大小 (Bytes) */
    private Long sizeBytes;

    /** MIME 类型 */
    private String contentType;

    /** 扩展名 (无点小写) */
    private String extension;

    /** 存储桶 */
    private String bucket;

    /** 可见性: PUBLIC / PRIVATE */
    private String visibility;

    /** 解析后的完整或相对访问 URL */
    private String url;
}
