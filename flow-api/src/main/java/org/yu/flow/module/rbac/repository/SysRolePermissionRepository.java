package org.yu.flow.module.rbac.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.yu.flow.module.rbac.domain.SysRolePermissionDO;

import java.util.Collection;
import java.util.List;

public interface SysRolePermissionRepository extends JpaRepository<SysRolePermissionDO, SysRolePermissionDO.PK> {
    List<SysRolePermissionDO> findByRoleIdIn(Collection<String> roleIds);

    List<SysRolePermissionDO> findByRoleId(String roleId);

    void deleteByRoleId(String roleId);
}
