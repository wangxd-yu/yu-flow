package org.yu.flow.module.api.dto;

import lombok.Data;

import java.util.HashMap;
import java.util.Map;

@Data
public class ApiDataExportRequestDTO {
    private Boolean useDraft;
    private Map<String, String> queryParams = new HashMap<>();
    private Map<String, Object> bodyParams = new HashMap<>();
    private Map<String, String> pathParams = new HashMap<>();
}
