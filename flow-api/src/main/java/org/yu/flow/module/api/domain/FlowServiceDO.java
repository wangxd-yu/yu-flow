package org.yu.flow.module.api.domain;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * @deprecated 遗留 {@code flow_service} 表实体；现代服务资产请使用
 * {@link org.yu.flow.module.serviceflow.domain.FlowServiceFlowDO}（{@code flow_service_info}）。
 * Hutool CRUD 已从 {@code FlowApiCrudService} 移除，本类仅保留兼容引用。
 */
@Deprecated
@Data
@NoArgsConstructor
public class FlowServiceDO{
    /**
     * 主键ID，通过Snowflake算法生成
     */
    private String id;

    /**
     * 名称
     */
    private String name;

    /**
     * 所属模块名称
     */
    private String module;

    /**
     * 数据源id
     */
    private String datasource;

    /**
     * service类型：DB、FLOW、JSON、STRING
     */
    private String type;

    /**
     * 数据库操作类型：PAGE（分页）、LIST（列表）、OBJECT（对象）、UPDATE（更新，包括：insert、update）
     */
    private String dbType;

    /**
     * @deprecated 已废弃，请使用 dslContent / sqlContent / jsonContent / textContent 替代。
     */
    @Deprecated
    private String script;

    /** 逻辑编排 (FLOW) — Flow DSL JSON */
    private String dslContent;

    /** 数据库 (DB) — SQL 脚本 */
    private String sqlContent;

    /** 静态 JSON (JSON) — JSON 内容 */
    private String jsonContent;

    /** 静态文本 (STRING) — 纯文本内容 */
    private String textContent;

    /**
     * 发布状态：0：未发布；1：已发布
     */
    private Integer publishStatus;

    /**
     * 校验规则
     */
    private String rule;

    /**
     * 版本号
     */
    private String version;

    /**
     * API配置的详细描述
     */
    private String info;

    /**
     * 创建时间，自动记录为当前时间
     */
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    private LocalDateTime updateTime;
}
