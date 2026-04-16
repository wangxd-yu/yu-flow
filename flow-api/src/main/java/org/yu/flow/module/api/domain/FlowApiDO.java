package org.yu.flow.module.api.domain;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import org.hibernate.annotations.GenericGenerator;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import javax.persistence.*;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * @author yu-flow
 * @date 2025-03-05 23:46
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Entity
@Accessors(chain = true)
@EntityListeners(AuditingEntityListener.class)
@Table(name = "flow_api_info")
@org.hibernate.annotations.SQLDelete(sql = "update flow_api_info set deleted = 1 where id = ?")
@org.hibernate.annotations.Where(clause = "deleted = 0 OR deleted IS NULL")
public class FlowApiDO implements Serializable {
    @Id
    @Column(name = "id", nullable = false)
    @GeneratedValue(generator = "snow_id")
    @GenericGenerator(name = "snow_id", strategy = "org.yu.flow.auto.util.SnowIdGenerator")
    private String id;
    private String name;
    private String url;

    /** 关联全局目录树 */
    private String directoryId;
    /**
     * 响应数据类型：PAGE(分页)、LIST(列表)、OBJECT(对象)
     */
    private String responseType;
    private String version;
    private String method;
    private String serviceType;

    /** 逻辑编排 (FLOW) — Flow DSL JSON */
    @Column(columnDefinition = "MEDIUMTEXT")
    private String dslContent;

    /** 数据库 (DB) — SQL 脚本 */
    @Column(columnDefinition = "TEXT")
    private String sqlContent;

    /** 静态 JSON (JSON) — JSON 内容 */
    @Column(columnDefinition = "MEDIUMTEXT")
    private String jsonContent;

    /** 静态文本 (STRING) — 纯文本内容 */
    @Column(columnDefinition = "TEXT")
    private String textContent;
    private String datasource;
    /**
     * 发布状态 0：未发布；1：已发布
     */
    private Integer publishStatus;
    /**
     * 优先级，与请求的ss-level比较，大的优先
     */
    private Integer level;

    /**
     * 基座模板ID
     */
    @Column(length = 32)
    private String templateId;

    /**
     * 自定义成功返回包装
     */
    @Column(columnDefinition = "TEXT")
    private String customSuccessWrapper;

    /**
     * 自定义分页返回包装
     */
    @Column(columnDefinition = "TEXT")
    private String customPageWrapper;

    /**
     * 自定义失败返回包装
     */
    @Column(columnDefinition = "TEXT")
    private String customFailWrapper;

    /**
     * 描述
     */
    private String info;

    /**
     * 标签，英文都好分隔
     */
    private String tags;

    // API 契约，包含 request/responses 的 JSON Schema 等信息
    @Column(columnDefinition = "MEDIUMTEXT")
    private String contract;

    /**
     * 是否已删除 0：未删除；1：已删除
     */
    @Column(name = "deleted", columnDefinition = "int default 0")
    private Integer deleted = 0;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime createTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime updateTime;
}
