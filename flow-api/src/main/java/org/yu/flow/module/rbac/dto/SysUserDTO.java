package org.yu.flow.module.rbac.dto;

import lombok.Data;

import java.util.List;

@Data
public class SysUserDTO {
    private String id;
    private String username;
    private String displayName;
    private Integer status;
    private Integer isBuiltin;
    private String remark;
    private List<String> roleCodes;
    private String createTime;
    private String updateTime;
}
