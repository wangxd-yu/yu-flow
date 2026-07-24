package org.yu.flow.module.release.domain;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.GenericGenerator;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Entity
@Table(name = "flow_regression_case")
public class FlowRegressionCaseDO {

    @Id
    @GeneratedValue(generator = "snow_id")
    @GenericGenerator(name = "snow_id", strategy = "org.yu.flow.auto.util.SnowIdGenerator")
    @Column(length = 64)
    private String id;

    @Column(name = "suite_id", nullable = false, length = 64)
    private String suiteId;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(name = "sort_order", nullable = false)
    private Integer sortOrder;

    @Column(nullable = false)
    private Integer enabled;

    @Column(name = "headers_json", columnDefinition = "TEXT")
    private String headersJson;

    @Column(name = "query_json", columnDefinition = "TEXT")
    private String queryJson;

    @Column(columnDefinition = "MEDIUMTEXT")
    private String body;

    @Column(name = "expect_trace_status", length = 20)
    private String expectTraceStatus;

    @Column(name = "expect_json_path", length = 128)
    private String expectJsonPath;

    @Column(name = "expect_value", length = 500)
    private String expectValue;

    @Column(name = "timeout_ms", nullable = false)
    private Integer timeoutMs;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    @Column(name = "create_time")
    private LocalDateTime createTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    @Column(name = "update_time")
    private LocalDateTime updateTime;
}
