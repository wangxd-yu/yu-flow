package org.yu.flow.module.rbac.dto;

import lombok.Data;

@Data
public class SysUserQueryDTO {
    private String username;
    private Integer status;
    private int page = 1;
    private int size = 20;
}
