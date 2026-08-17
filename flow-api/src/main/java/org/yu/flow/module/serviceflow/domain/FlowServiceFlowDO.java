package org.yu.flow.module.serviceflow.domain;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import org.hibernate.annotations.GenericGenerator;

import jakarta.persistence.*;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 内部服务编排定义 JPA 实体（表 flow_service_info）
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Entity
@Accessors(chain = true)
@Table(name = "flow_service_info")
@org.hibernate.annotations.SQLDelete(sql = "update flow_service_info set deleted = 1 where id = ?")
@org.hibernate.annotations.Where(clause = "deleted = 0 OR deleted IS NULL")
public class FlowServiceFlowDO implements Serializable {

    @Id
    @Column(name = "id", nullable = false)
    @GeneratedValue(generator = "snow_id")
    @GenericGenerator(name = "snow_id", strategy = "org.yu.flow.auto.util.SnowIdGenerator")
    private String id;

    private String name;

    private String directoryId;

    @Column(nullable = false)
    private Boolean enabled;

    /** 是否记录执行日志
     * @deprecated 请使用 {@link #logMode} 替代 */
    @Column(nullable = false)
    private Boolean logEnabled;

    /**
     * 日志策略模式（四态枚举）：SYSTEM_DEFAULT / OFF / ERROR_ONLY / ALL。
     * 取代旧版 logEnabled 布尔值。null 等价于 SYSTEM_DEFAULT（继承全局配置）。
     */
    @Column(length = 16)
    private String logMode;

    @Column(columnDefinition = "MEDIUMTEXT")
    private String dslContent;

    /**
     * 服务契约 JSON：
     * <pre>{ "inputs": SchemaNode[], "outputs": SchemaNode[], "outputDescription": "..." }</pre>
     */
    @Column(columnDefinition = "MEDIUMTEXT")
    private String contract;

    /** 发布状态：0=未发布，1=已发布 */
    @Column(columnDefinition = "tinyint default 0")
    private Integer publishStatus;

    /** 发布快照 JSON：dslContent + contract */
    @Column(columnDefinition = "MEDIUMTEXT")
    private String publishedSnapshot;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime publishTime;

    private String info;

    private String tags;

    @Column(name = "deleted", columnDefinition = "int default 0")
    private Integer deleted = 0;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime createTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime updateTime;

    @PrePersist
    @PreUpdate
    public void ensureLogModeDefaults() {
        if (logMode == null || logMode.isBlank()) {
            logMode = "SYSTEM_DEFAULT";
        }
    }
}
