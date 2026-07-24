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
@Table(name = "flow_regression_run")
public class FlowRegressionRunDO {

    @Id
    @GeneratedValue(generator = "snow_id")
    @GenericGenerator(name = "snow_id", strategy = "org.yu.flow.auto.util.SnowIdGenerator")
    @Column(length = 64)
    private String id;

    @Column(name = "suite_id", nullable = false, length = 64)
    private String suiteId;

    @Column(name = "asset_type", nullable = false, length = 32)
    private String assetType;

    @Column(name = "asset_id", nullable = false, length = 64)
    private String assetId;

    @Column(name = "env_code", nullable = false, length = 32)
    private String envCode;

    @Column(nullable = false, length = 20)
    private String status;

    @Column(name = "total_cases", nullable = false)
    private Integer totalCases;

    @Column(name = "passed_cases", nullable = false)
    private Integer passedCases;

    @Column(name = "failed_cases", nullable = false)
    private Integer failedCases;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    @Column(name = "started_at", nullable = false)
    private LocalDateTime startedAt;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    @Column(name = "finished_at")
    private LocalDateTime finishedAt;

    @Column(name = "triggered_by", length = 100)
    private String triggeredBy;

    @Column(length = 500)
    private String summary;
}
