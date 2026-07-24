package org.yu.flow.module.release.dto;

import lombok.Data;
import org.yu.flow.module.release.domain.FlowRegressionRunDO;

import java.util.List;

@Data
public class RegressionRunDTO {
    private String id;
    private String suiteId;
    private String assetType;
    private String assetId;
    private String envCode;
    private String status;
    private Integer totalCases;
    private Integer passedCases;
    private Integer failedCases;
    private String startedAt;
    private String finishedAt;
    private String triggeredBy;
    private String summary;
    private List<RegressionRunCaseDTO> cases;

    public static RegressionRunDTO fromDO(FlowRegressionRunDO d) {
        if (d == null) {
            return null;
        }
        RegressionRunDTO dto = new RegressionRunDTO();
        dto.setId(d.getId());
        dto.setSuiteId(d.getSuiteId());
        dto.setAssetType(d.getAssetType());
        dto.setAssetId(d.getAssetId());
        dto.setEnvCode(d.getEnvCode());
        dto.setStatus(d.getStatus());
        dto.setTotalCases(d.getTotalCases());
        dto.setPassedCases(d.getPassedCases());
        dto.setFailedCases(d.getFailedCases());
        if (d.getStartedAt() != null) {
            dto.setStartedAt(d.getStartedAt().toString().replace('T', ' '));
        }
        if (d.getFinishedAt() != null) {
            dto.setFinishedAt(d.getFinishedAt().toString().replace('T', ' '));
        }
        dto.setTriggeredBy(d.getTriggeredBy());
        dto.setSummary(d.getSummary());
        return dto;
    }
}
