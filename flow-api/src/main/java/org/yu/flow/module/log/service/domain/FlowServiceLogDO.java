package org.yu.flow.module.log.service.domain;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import org.hibernate.annotations.GenericGenerator;
import org.yu.flow.util.trace.RequestIds;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.time.ZoneId;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Accessors(chain = true)
@Entity
@Table(name = "flow_log_service")
public class FlowServiceLogDO {

    @Id
    @GeneratedValue(generator = "snow_id")
    @GenericGenerator(name = "snow_id", strategy = "org.yu.flow.util.SnowIdGenerator")
    private String id;

    @Column(nullable = false, length = 32)
    private String serviceId;

    private String serviceName;

    /** MANUAL / CALL / DEBUG */
    @Column(length = 16)
    private String triggerType;

    @Column(length = 16)
    private String status;

    private Long costTimeMs;

    @Column(columnDefinition = "TEXT")
    private String errorMsg;

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
