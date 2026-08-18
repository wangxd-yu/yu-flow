package org.yu.flow.module.alert.domain;

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
@Table(name = "flow_log_alert")
public class AlertEventDO {

    @Id
    @GeneratedValue(generator = "snow_id")
    @GenericGenerator(name = "snow_id", strategy = "org.yu.flow.auto.util.SnowIdGenerator")
    @Column(length = 64)
    private String id;

    @Column(name = "rule_id", length = 64)
    private String ruleId;

    @Column(name = "rule_name", length = 100)
    private String ruleName;

    @Column(length = 200)
    private String fingerprint;

    @Column(name = "asset_type", length = 32)
    private String assetType;

    @Column(name = "asset_id", length = 64)
    private String assetId;

    @Column(name = "asset_name", length = 200)
    private String assetName;

    @Column(length = 20)
    private String health;

    @Column(name = "error_rate")
    private Double errorRate;

    @Column(name = "fail_count")
    private Long failCount;

    @Column(name = "window", length = 20)
    private String window;

    @Column(name = "channel_type", length = 20)
    private String channelType;

    @Column(name = "channel_id", length = 64)
    private String channelId;

    /** SUCCESS | FAIL | SUPPRESSED */
    @Column(nullable = false, length = 20)
    private String status;

    @Column(name = "payload_json", columnDefinition = "TEXT")
    private String payloadJson;

    @Column(name = "error_msg", length = 500)
    private String errorMsg;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    @Column(name = "fired_at", nullable = false)
    private LocalDateTime firedAt;
}
