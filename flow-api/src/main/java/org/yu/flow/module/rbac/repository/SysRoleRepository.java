package org.yu.flow.module.rbac.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.yu.flow.module.rbac.domain.SysRoleDO;

import java.util.Optional;

public interface SysRoleRepository extends JpaRepository<SysRoleDO, String> {
    Optional<SysRoleDO> findByRoleCode(String roleCode);

    boolean existsByRoleCode(String roleCode);
}
