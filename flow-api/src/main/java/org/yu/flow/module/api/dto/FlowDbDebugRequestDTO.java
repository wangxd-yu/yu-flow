package org.yu.flow.module.api.dto;

import lombok.Data;

import java.util.Map;

/**
 * 数据库模式调试运行请求
 */
@Data
public class FlowDbDebugRequestDTO {
    /** SQL 脚本（支持动态 ${} 参数） */
    private String sqlContent;
    /** 数据源编码 */
    private String datasource;
    /** 响应类型：PAGE / LIST / OBJECT / UPDATE / INSERT */
    private String responseType;

    private Map<String, String> headers;
    private Map<String, String> queryParams;
    /** Body JSON 字符串或已解析对象序列化前的原始字符串 */
    private String body;

    /** 分页页码（从 0 开始，PAGE 模式使用；也可放在 queryParams.page） */
    private Integer page;
    /** 分页大小（PAGE 模式使用；也可放在 queryParams.size） */
    private Integer size;

    private String sourceRef;
    private String sourceName;

    /**
     * 是否在调试结束后回滚事务。默认 true：写操作（INSERT/UPDATE/DELETE）不会真正落库。
     * 设为 false 时按正式接口逻辑提交。
     */
    private Boolean rollbackTransaction;
}
