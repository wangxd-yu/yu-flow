package org.yu.flow.module.rbac.domain;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.persistence.*;
import java.io.Serializable;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "flow_sys_role_permission")
@IdClass(SysRolePermissionDO.PK.class)
public class SysRolePermissionDO {

    @Id
    @Column(name = "role_id", length = 32)
    private String roleId;

    @Id
    @Column(name = "perm_code", length = 128)
    private String permCode;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PK implements Serializable {
        private String roleId;
        private String permCode;
    }
}
