package org.yu.flow.log.open.domain;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import org.hibernate.annotations.GenericGenerator;

import jakarta.persistence.*;
import java.util.Date;

/**
 * 开放平台入站调用摘要（不含 body）。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Accessors(chain = true)
@Entity
@Table(name = "flow_log_open_call")
public class FlowOpenCallLogDO {

    @Id
    @GeneratedValue(generator = "snow_id")
    @GenericGenerator(name = "snow_id", strategy = "org.yu.flow.auto.util.SnowIdGenerator")
    private String id;

    @Column(name = "platform_id", length = 32)
    private String platformId;

    @Column(name = "app_key", length = 64)
    private String appKey;

    @Column(name = "api_id", length = 32)
    private String apiId;

    @Column(length = 16)
    private String method;

    @Column(length = 512)
    private String path;

    private Integer status;

    @Column(name = "cost_ms")
    private Long costMs;

    @Column(name = "error_code", length = 64)
    private String errorCode;

    @Column(name = "request_id", length = 64)
    private String requestId;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    @Column(name = "create_time")
    private Date createTime;

    @PrePersist
    public void prePersist() {
        if (createTime == null) {
            createTime = new Date();
        }
    }
}
