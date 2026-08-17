package org.yu.flow.module.oss.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;

/**
 * 预签名直传开票结果：客户端拿到 uploadUrl 后自行 PUT，再回调 confirm 完成台账。
 */
@Data
@Accessors(chain = true)
public class OssPresignUploadInitDTO {

    /** 预创建的 PENDING 台账 ID，confirm/abort 都用它 */
    private String objectId;

    /** 客户端直传地址（含签名参数） */
    private String uploadUrl;

    /** 直传使用的 HTTP 方法，固定 PUT */
    private String method;

    private String bucket;

    private String objectKey;

    /** uploadUrl 有效期（秒） */
    private Integer expireSeconds;

    /** uploadUrl 过期时刻 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime urlExpiresAt;
}
