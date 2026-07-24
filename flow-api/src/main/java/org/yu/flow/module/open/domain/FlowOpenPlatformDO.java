package org.yu.flow.module.open.domain;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import org.hibernate.annotations.GenericGenerator;

import jakarta.persistence.*;
import java.util.Date;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Accessors(chain = true)
@Entity
@Table(name = "flow_open_platform")
public class FlowOpenPlatformDO {

    @Id
    @GeneratedValue(generator = "snow_id")
    @GenericGenerator(name = "snow_id", strategy = "org.yu.flow.auto.util.SnowIdGenerator")
    private String id;

    @Column(nullable = false, length = 128)
    private String name;

    @Column(nullable = false, length = 64)
    private String code;

    /** 0停用 1启用 */
    @Column(nullable = false)
    private Integer status;

    private String contact;

    @Column(length = 512)
    private String remark;

    /** IP 白名单 JSON 数组字符串 */
    @Column(name = "ip_allowlist", length = 1024)
    private String ipAllowlist;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    @Column(name = "expire_at")
    private Date expireAt;

    /** 是否记录入站摘要日志：0关 1开；null 跟随全局 */
    @Column(name = "open_call_log_enabled")
    private Integer openCallLogEnabled;

    /** 平台级 QPS 上限；null/≤0 表示不限 */
    @Column(name = "rate_limit_qps")
    private Integer rateLimitQps;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    @Column(name = "create_time")
    private Date createTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    @Column(name = "update_time")
    private Date updateTime;

    @PrePersist
    public void prePersist() {
        Date now = new Date();
        if (createTime == null) createTime = now;
        updateTime = now;
        if (status == null) status = 1;
        if (openCallLogEnabled == null) openCallLogEnabled = 1;
    }

    @PreUpdate
    public void preUpdate() {
        updateTime = new Date();
    }
}
