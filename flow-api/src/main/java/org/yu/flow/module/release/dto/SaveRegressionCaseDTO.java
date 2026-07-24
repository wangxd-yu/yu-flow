package org.yu.flow.module.release.dto;

import lombok.Data;

@Data
public class SaveRegressionCaseDTO {
    private String name;
    private Integer sortOrder;
    private Integer enabled;
    private String headersJson;
    private String queryJson;
    private String body;
    private String expectTraceStatus;
    private String expectJsonPath;
    private String expectValue;
    private Integer timeoutMs;
}
