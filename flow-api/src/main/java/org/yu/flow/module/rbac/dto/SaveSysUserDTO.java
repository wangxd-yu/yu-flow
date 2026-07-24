package org.yu.flow.module.rbac.dto;

import lombok.Data;

import java.util.List;

@Data
public class SaveSysUserDTO {
    private String username;
    private String password;
    private String displayName;
    private Integer status;
    private String remark;
    private List<String> roleCodes;
}
