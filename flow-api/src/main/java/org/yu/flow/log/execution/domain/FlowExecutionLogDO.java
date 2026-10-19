package org.yu.flow.log.execution.domain;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import org.hibernate.annotations.GenericGenerator;
import org.yu.flow.log.trace.RequestIds;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.time.ZoneId;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Accessors(chain = true)
@Entity
@Table(name = "flow_log_execution")
public class FlowExecutionLogDO {

    @Id
    @GeneratedValue(generator = "snow_id")
    @GenericGenerator(name = "snow_id", strategy = "org.yu.flow.auto.util.SnowIdGenerator")
    private String id;

    private String apiId;
    private String apiName;
    private String url;
    /** 接口类型：FLOW / DB / JSON / STRING */
    private String serviceType;
    private String method;

    @Basic(fetch = FetchType.LAZY)
    @Column(columnDefinition = "LONGTEXT")
    private String requestParams;

    @Basic(fetch = FetchType.LAZY)
    @Column(columnDefinition = "LONGTEXT")
    private String responseBody;

    private String status;

    @Column(columnDefinition = "TEXT")
    private String errorMsg;

    private Long costTimeMs;

    @Basic(fetch = FetchType.LAZY)
    @Column(columnDefinition = "LONGTEXT")
    private String traceData;

    /** 请求 ID（响应头 X-Request-Id），按它和应用日志对线 */
    @Builder.Default
    @Column(name = "request_id", length = 64)
    private String requestId = RequestIds.current();

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime createTime;

    @PrePersist
    public void prePersist() {
        if (requestId == null) {
            requestId = RequestIds.current();
        }
        if (createTime == null) {
            createTime = LocalDateTime.now(ZoneId.of("Asia/Shanghai"));
        }
    }
}
