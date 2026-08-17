package org.yu.flow.module.api.domain;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import org.hibernate.annotations.GenericGenerator;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import jakarta.persistence.*;
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

    /**
     * 同名拦截模式：REPLACE（默认，引擎替换宿主）/ WRAP（包裹转发宿主）。
     * <p>运行时以 publishedSnapshot 为准。</p>
     */
    @Column(length = 16)
    private String interceptMode;

    /**
     * WRAP 宿主绑定 JSON。
     * <p>结构示例：{"forward":"LOCAL","targetPath":"/biz/orders","probePath":null}</p>
     */
    @Column(columnDefinition = "TEXT")
    private String hostBinding;

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
     * 是否记录执行日志。null 兼容历史数据，运行时按开启处理。
     * @deprecated 请使用 {@link #logMode} 替代
     */
    @Column(nullable = false)
    private Boolean logEnabled;

    /**
     * 日志策略模式（四态枚举）：SYSTEM_DEFAULT / OFF / ERROR_ONLY / ALL。
     * 取代旧版 logEnabled 布尔值，实现更精细的日志采样控制。
     * null 等价于 SYSTEM_DEFAULT（继承全局配置）。
     */
    @Column(length = 16)
    private String logMode;

    /** 日志保留天数：null=跟随系统配置，0=永久保留，>0=自定义天数 */
    private Integer logRetentionDays;

    /**
     * 查询响应 Redis 缓存配置（JSON）。
     * <p>结构示例：{"enabled":true,"ttlSeconds":300,"keyParams":[{"source":"query","name":"userId"}],"includePageable":true}</p>
     */
    @Column(columnDefinition = "TEXT")
    private String cacheConfig;

    /**
     * 入站防护配置（JSON）。
     * <p>结构示例：{"authMode":"INHERIT","antiReplay":null,"rateLimitEnabled":null,"rateLimitQps":null,"ipAllowlist":null}</p>
     * <p>运行时以 publishedSnapshot 为准，改完需发布后生效。</p>
     */
    @Column(columnDefinition = "TEXT")
    private String securityConfig;

    /**
     * 出站隐私拦截 JSON（{@code ApiPrivacyConfig}）。
     * <p>已发布接口以 publishedSnapshot 为准；目录未覆盖字段即时继承。</p>
     */
    @Column(name = "privacy_config", columnDefinition = "TEXT")
    private String privacyConfig;

    /**
     * 数据查看 / Excel 导出配置（JSON）。
     * <p>结构示例：{"enabled":true,"sheetName":"数据","maxExportRows":50000,"columns":[{"field":"userName","header":"用户名"}]}</p>
     */
    @Column(name = "view_export_config", columnDefinition = "TEXT")
    private String viewExportConfig;

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
     * 发布时的完整内容快照 (JSON)。
     * <p>运行时引擎从此字段读取，用户编辑的草稿不会影响线上。</p>
     */
    @Column(columnDefinition = "MEDIUMTEXT")
    private String publishedSnapshot;

    /**
     * 最近一次发布时间
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime publishTime;

    /**
     * 是否已删除 0：未删除；1：已删除
     */
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
