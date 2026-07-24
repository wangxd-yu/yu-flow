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
@Table(name = "flow_sys_user_role")
@IdClass(SysUserRoleDO.PK.class)
public class SysUserRoleDO {

    @Id
    @Column(name = "user_id", length = 32)
    private String userId;

    @Id
    @Column(name = "role_id", length = 32)
    private String roleId;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PK implements Serializable {
        private String userId;
        private String roleId;
    }
}
