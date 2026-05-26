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
     * 断点节点 ID 集合（交互式调试模式需要）。
     * <p>为空或 null 时退化为非交互式一次性运行模式（即现有的 /debug/run 行为）。</p>
     */
    private Set<String> breakpoints;
}

