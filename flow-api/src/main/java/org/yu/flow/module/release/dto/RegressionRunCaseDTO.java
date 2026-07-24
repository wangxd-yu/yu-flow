package org.yu.flow.module.release.dto;

import lombok.Data;
import org.yu.flow.module.release.domain.FlowRegressionRunCaseDO;

@Data
public class RegressionRunCaseDTO {
    private String id;
    private String runId;
    private String caseId;
    private String caseName;
    private String status;
    private Long durationMs;
    private String message;
    private String detailJson;

    public static RegressionRunCaseDTO fromDO(FlowRegressionRunCaseDO d) {
        if (d == null) {
            return null;
        }
        RegressionRunCaseDTO dto = new RegressionRunCaseDTO();
        dto.setId(d.getId());
        dto.setRunId(d.getRunId());
        dto.setCaseId(d.getCaseId());
        dto.setCaseName(d.getCaseName());
        dto.setStatus(d.getStatus());
        dto.setDurationMs(d.getDurationMs());
        dto.setMessage(d.getMessage());
        dto.setDetailJson(d.getDetailJson());
        return dto;
    }
}
