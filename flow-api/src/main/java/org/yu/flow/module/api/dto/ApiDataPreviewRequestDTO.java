package org.yu.flow.module.api.dto;

import lombok.Data;

import java.util.HashMap;
import java.util.Map;

@Data
public class ApiDataPreviewRequestDTO {
    /** true=草稿；false/默认=已发布快照 */
    private Boolean useDraft;
    private Map<String, String> queryParams = new HashMap<>();
    private Map<String, Object> bodyParams = new HashMap<>();
    private Map<String, String> pathParams = new HashMap<>();
    /** 0-based page，默认 0 */
    private Integer page;
    /** 默认 20 */
    private Integer size;
}
