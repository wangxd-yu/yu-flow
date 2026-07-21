package org.yu.flow.module.api.dto;

import lombok.Data;

import java.util.Map;
import java.util.Set;

/**
 * 调试模式请求 DTO
 */
@Data
public class FlowDebugRequestDTO {
    private String dslContent;
    private Map<String, String> headers;
    private Map<String, String> queryParams;
    private String body;

    /**
     * 服务契约 JSON（可选）。服务调试时用于与 CALL 一致的入参转换/必填校验；
     * 未传时可按 {@link #sourceRef} 回退加载草稿契约。
     */
    private String contract;

    /**
     * 断点节点 ID 集合（交互式调试模式需要）。
     * <p>为空或 null 时退化为非交互式一次性运行模式（即现有的 /debug/run 行为）。</p>
     */
    private Set<String> breakpoints;

    /**
     * 调试来源关联 ID（接口 ID 或任务 ID，写入三方日志 sourceRef）
     */
    private String sourceRef;

    /**
     * 调试来源名称（接口名或任务名，写入三方日志 sourceName）
     */
    private String sourceName;
}

