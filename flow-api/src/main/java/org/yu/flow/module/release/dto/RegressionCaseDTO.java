package org.yu.flow.module.release.dto;

import lombok.Data;
import org.yu.flow.module.release.domain.FlowRegressionCaseDO;

@Data
public class RegressionCaseDTO {
    private String id;
    private String suiteId;
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

    public static RegressionCaseDTO fromDO(FlowRegressionCaseDO d) {
        if (d == null) {
            return null;
        }
        RegressionCaseDTO dto = new RegressionCaseDTO();
        dto.setId(d.getId());
        dto.setSuiteId(d.getSuiteId());
        dto.setName(d.getName());
        dto.setSortOrder(d.getSortOrder());
        dto.setEnabled(d.getEnabled());
        dto.setHeadersJson(d.getHeadersJson());
        dto.setQueryJson(d.getQueryJson());
        dto.setBody(d.getBody());
        dto.setExpectTraceStatus(d.getExpectTraceStatus());
        dto.setExpectJsonPath(d.getExpectJsonPath());
        dto.setExpectValue(d.getExpectValue());
        dto.setTimeoutMs(d.getTimeoutMs());
        return dto;
    }
}
