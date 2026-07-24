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
@Table(name = "flow_alert_rule")
public class AlertRuleDO {

    @Id
    @GeneratedValue(generator = "snow_id")
    @GenericGenerator(name = "snow_id", strategy = "org.yu.flow.auto.util.SnowIdGenerator")
    @Column(length = 64)
    private String id;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(nullable = false)
    private Integer enabled;

    /** API,TASK,SERVICE,PLATFORM CSV；空=全部 */
    @Column(name = "scope_asset_types", length = 200)
    private String scopeAssetTypes;

    @Column(name = "window", nullable = false, length = 20)
    private String window;

    @Column(name = "min_health", nullable = false, length = 20)
    private String minHealth;

    @Column(name = "top_n", nullable = false)
    private Integer topN;

    /** JSON 数组，如 ["id1","id2"] */
    @Column(name = "channel_ids", length = 500)
    private String channelIds;

    @Column(name = "interval_minutes", nullable = false)
    private Integer intervalMinutes;

    @Column(name = "dedup_minutes", nullable = false)
    private Integer dedupMinutes;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    @Column(name = "create_time")
    private LocalDateTime createTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    @Column(name = "update_time")
    private LocalDateTime updateTime;
}
