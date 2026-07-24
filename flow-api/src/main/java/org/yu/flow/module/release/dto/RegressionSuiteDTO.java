package org.yu.flow.module.release.dto;

import lombok.Data;
import org.yu.flow.module.release.domain.FlowRegressionSuiteDO;

import java.util.List;

@Data
public class RegressionSuiteDTO {
    private String id;
    private String name;
    private String assetType;
    private String assetId;
    private Integer enabled;
    private String createTime;
    private String updateTime;
    private Integer caseCount;
    private List<RegressionCaseDTO> cases;

    public static RegressionSuiteDTO fromDO(FlowRegressionSuiteDO d) {
        if (d == null) {
            return null;
        }
        RegressionSuiteDTO dto = new RegressionSuiteDTO();
        dto.setId(d.getId());
        dto.setName(d.getName());
        dto.setAssetType(d.getAssetType());
        dto.setAssetId(d.getAssetId());
        dto.setEnabled(d.getEnabled());
        if (d.getCreateTime() != null) {
            dto.setCreateTime(d.getCreateTime().toString().replace('T', ' '));
        }
        if (d.getUpdateTime() != null) {
            dto.setUpdateTime(d.getUpdateTime().toString().replace('T', ' '));
        }
        return dto;
    }
}
