package org.yu.flow.module.oss.domain;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import org.hibernate.annotations.GenericGenerator;

import jakarta.persistence.*;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * OSS 隐私下载审计（flow_oss_download_log）
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Entity
@Accessors(chain = true)
@Table(name = "flow_oss_download_log")
public class OssDownloadLogDO implements Serializable {

    public static final String RESULT_SUCCESS = "SUCCESS";
    public static final String RESULT_DENIED = "DENIED";
    public static final String RESULT_NOT_FOUND = "NOT_FOUND";
    public static final String RESULT_ERROR = "ERROR";

    @Id
    @Column(name = "id", nullable = false)
    @GeneratedValue(generator = "snow_id")
    @GenericGenerator(name = "snow_id", strategy = "org.yu.flow.auto.util.SnowIdGenerator")
    private String id;

    @Column(name = "object_id", length = 32)
    private String objectId;

    @Column(name = "downloaded_by", length = 64)
    private String downloadedBy;

    @Column(name = "downloaded_by_name", length = 128)
    private String downloadedByName;

    @Column(name = "client_ip", length = 64)
    private String clientIp;

    @Column(name = "user_agent", length = 512)
    private String userAgent;

    @Column(length = 16)
    private String result;

    @Column(name = "deny_reason", length = 512)
    private String denyReason;

    @Column(name = "time_ms")
    private Long timeMs;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime createTime;
}
