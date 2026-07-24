package org.yu.flow.module.release.dto;

import lombok.Data;
import org.yu.flow.module.release.domain.FlowEnvDO;

@Data
public class FlowEnvDTO {
    private String id;
    private String code;
    private String name;
    private Integer requireSuitePass;
    private Integer passTtlHours;
    private Integer enabled;
    private Integer sortOrder;
    private String remark;

    public static FlowEnvDTO fromDO(FlowEnvDO d) {
        if (d == null) {
            return null;
        }
        FlowEnvDTO dto = new FlowEnvDTO();
        dto.setId(d.getId());
        dto.setCode(d.getCode());
        dto.setName(d.getName());
        dto.setRequireSuitePass(d.getRequireSuitePass());
        dto.setPassTtlHours(d.getPassTtlHours());
        dto.setEnabled(d.getEnabled());
        dto.setSortOrder(d.getSortOrder());
        dto.setRemark(d.getRemark());
        return dto;
    }
}
