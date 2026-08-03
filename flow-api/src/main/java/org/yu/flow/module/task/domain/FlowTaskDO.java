package org.yu.flow.module.task.domain;

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
 * 定时任务定义 JPA 实体
 *
 * @author yu-flow
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Entity
@Accessors(chain = true)
@Table(name = "flow_task_info")
@org.hibernate.annotations.SQLDelete(sql = "update flow_task_info set deleted = 1 where id = ?")
@org.hibernate.annotations.Where(clause = "deleted = 0 OR deleted IS NULL")
public class FlowTaskDO implements Serializable {

    @Id
    @Column(name = "id", nullable = false)
    @GeneratedValue(generator = "snow_id")
    @GenericGenerator(name = "snow_id", strategy = "org.yu.flow.auto.util.SnowIdGenerator")
    private String id;

    /** 任务名称 */
    private String name;

    /** 关联全局目录树 */
    private String directoryId;

    /** Cron 表达式，如 0/5 * * * * ? */
    @Column(nullable = false, length = 64)
    private String cron;

    /** 启用状态：0=停用，1=启用 */
    @Column(columnDefinition = "tinyint(1) default 1")
    private Boolean enabled;

    /** 是否记录执行日志
     * @deprecated 请使用 {@link #logMode} 替代 */
    @Column(columnDefinition = "tinyint(1) default 1")
    private Boolean logEnabled;

    /**
     * 日志策略模式（四态枚举）：SYSTEM_DEFAULT / OFF / ERROR_ONLY / ALL。
     * 取代旧版 logEnabled 布尔值。null 等价于 SYSTEM_DEFAULT（继承全局配置）。
     */
    @Column(length = 16)
    private String logMode;

    /** 日志保留天数：null=跟随系统配置，0=永久保留，>0=自定义天数 */
    private Integer logRetentionDays;

    /** 流程定义 DSL JSON（草稿） */
    @Column(columnDefinition = "MEDIUMTEXT")
    private String dslContent;

    /** 发布状态：0=未发布，1=已发布 */
    @Column(columnDefinition = "tinyint default 0")
    private Integer publishStatus;

    /** 发布快照 JSON：dslContent */
    @Column(columnDefinition = "MEDIUMTEXT")
    private String publishedSnapshot;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime publishTime;

    /** 任务描述 */
    private String info;

    /** 标签，英文逗号分隔 */
    private String tags;

    /** 是否已删除：0=正常，1=已删除 */
    @Column(name = "deleted", columnDefinition = "int default 0")
    private Integer deleted = 0;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime createTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime updateTime;
}
