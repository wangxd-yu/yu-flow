package org.yu.flow.log.third.domain;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import org.hibernate.annotations.GenericGenerator;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.time.ZoneId;

/**
 * 第三方接口调用日志实体
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Accessors(chain = true)
@Entity
@Table(name = "flow_log_third")
public class FlowThirdLogDO {

    @Id
    @GeneratedValue(generator = "snow_id")
    @GenericGenerator(name = "snow_id", strategy = "org.yu.flow.auto.util.SnowIdGenerator")
    @Column(name = "id", nullable = false, length = 64)
    private String id;

    /** 接口标识 */
    @Column(name = "api_type", length = 50)
    private String apiType;

    /** 调用来源：API / TASK / DEBUG / OTHER */
    @Column(name = "source", length = 16)
    private String source;

    /** 来源关联 ID（apiId / taskId） */
    @Column(name = "source_ref", length = 64)
    private String sourceRef;

    /** 来源名称（接口名 / 任务名） */
    @Column(name = "source_name", length = 128)
    private String sourceName;

    @Column(name = "request_url", length = 512)
    private String requestUrl;

    @Column(name = "request_method", length = 10)
    private String requestMethod;

    @Basic(fetch = FetchType.LAZY)
    @Column(name = "request_params", columnDefinition = "TEXT")
    private String requestParams;

    @Basic(fetch = FetchType.LAZY)
    @Column(name = "request_headers", columnDefinition = "TEXT")
    private String requestHeaders;

    @Column(name = "response_status")
    private Integer responseStatus;

    @Basic(fetch = FetchType.LAZY)
    @Column(name = "response_body", columnDefinition = "TEXT")
    private String responseBody;

    /** 请求耗时(ms) */
    @Column(name = "elapsed_time")
    private Long elapsedTime;

    /** 是否成功（0-失败, 1-成功） */
    @Column(name = "is_success")
    private Integer isSuccess;

    @Column(name = "error_message", length = 1000)
    private String errorMessage;

    @Basic(fetch = FetchType.LAZY)
    @Column(name = "curl", columnDefinition = "TEXT")
    private String curl;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    @Column(name = "create_time")
    private LocalDateTime createTime;

    @PrePersist
    public void prePersist() {
        if (createTime == null) {
            createTime = LocalDateTime.now(ZoneId.of("Asia/Shanghai"));
        }
    }
}
