package org.yu.flow.module.release.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.GenericGenerator;

import jakarta.persistence.*;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Entity
@Table(name = "flow_regression_run_case")
public class FlowRegressionRunCaseDO {

    @Id
    @GeneratedValue(generator = "snow_id")
    @GenericGenerator(name = "snow_id", strategy = "org.yu.flow.auto.util.SnowIdGenerator")
    @Column(length = 64)
    private String id;

    @Column(name = "run_id", nullable = false, length = 64)
    private String runId;

    @Column(name = "case_id", nullable = false, length = 64)
    private String caseId;

    @Column(name = "case_name", length = 100)
    private String caseName;

    @Column(nullable = false, length = 20)
    private String status;

    @Column(name = "duration_ms")
    private Long durationMs;

    @Column(length = 500)
    private String message;

    @Column(name = "detail_json", length = 2000)
    private String detailJson;
}
