package org.yu.flow.module.rbac.dto;

import lombok.Data;

import java.util.List;

@Data
public class SaveSysRoleDTO {
    /** 创建时必填；更新时不可改 */
    private String roleCode;
    private String roleName;
    private Integer status;
    private String remark;
    /** 权限码列表；传空数组表示清空（慎用） */
    private List<String> permCodes;
}
