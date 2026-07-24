package org.yu.flow.module.rbac.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
@lombok.NoArgsConstructor
@lombok.AllArgsConstructor
public class SysRoleDTO {
    private String id;
    private String roleCode;
    private String roleName;
    private Integer status;
    private Integer isBuiltin;
    private String remark;
    /** 已绑定权限码 */
    private List<String> permCodes;
    private Integer permCount;
    private String createTime;
    private String updateTime;
}
