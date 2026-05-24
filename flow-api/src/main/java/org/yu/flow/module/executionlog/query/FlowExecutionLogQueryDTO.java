package org.yu.flow.module.executionlog.query;

import lombok.Data;

/**
 * API 执行日志分页查询条件
 */
@Data
public class FlowExecutionLogQueryDTO {
    private Integer page = 0;
    private Integer size = 10;
    /** API ID 精确匹配 */
    private String apiId;
    /** API 名称模糊搜索 */
    private String apiName;
    /** 执行状态：SUCCESS / ERROR */
    private String status;
    /** HTTP 方法精确匹配 */
    private String method;
    /** 请求路径模糊搜索（如 /flow/xxx） */
    private String url;
    /** 查询开始时间（ISO 8601，如 2026-05-24T00:00:00） */
    private String startTime;
    /** 查询结束时间（ISO 8601，如 2026-05-24T23:59:59） */
    private String endTime;
}
