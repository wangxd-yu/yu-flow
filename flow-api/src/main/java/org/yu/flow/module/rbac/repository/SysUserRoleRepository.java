package org.yu.flow.module.rbac.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.yu.flow.module.rbac.domain.SysUserRoleDO;

import java.util.List;

public interface SysUserRoleRepository extends JpaRepository<SysUserRoleDO, SysUserRoleDO.PK> {
    List<SysUserRoleDO> findByUserId(String userId);

    List<SysUserRoleDO> findByRoleId(String roleId);

    void deleteByUserId(String userId);

    void deleteByRoleId(String roleId);

    long countByRoleId(String roleId);
}
