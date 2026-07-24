package org.yu.flow.module.rbac.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
@lombok.NoArgsConstructor
@lombok.AllArgsConstructor
public class SysPermissionDTO {
    private String id;
    private String permCode;
    private String permName;
    private String groupCode;
    private String remark;
}
