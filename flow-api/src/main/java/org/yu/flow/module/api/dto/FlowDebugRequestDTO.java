package org.yu.flow.module.api.dto;

import lombok.Data;

import java.util.Map;

/**
 * 调试模式请求 DTO
 */
@Data
public class FlowDebugRequestDTO {
    private String dslContent;
    private Map<String, String> headers;
    private Map<String, String> queryParams;
    private String body;
}
