package org.yu.flow.module.rbac.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class AuthMeDTO {
    private String userId;
    private String username;
    private String displayName;
    private List<String> roles;
    private List<String> permissions;
    /** 是否 yml 兜底账号（无 DB 用户） */
    private Boolean legacyAdmin;
    /**
     * OSS 模块是否已装配（classpath 有 MinIO 且 yu.flow.oss.enabled=true）。
     * 前端据此隐藏 OSS 菜单，避免点进去 404。
     */
    private Boolean ossEnabled;
}
