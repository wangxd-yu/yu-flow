package org.yu.flow.module.rbac.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.yu.flow.module.rbac.domain.SysUserDO;

import java.util.Optional;

public interface SysUserRepository extends JpaRepository<SysUserDO, String>, JpaSpecificationExecutor<SysUserDO> {
    Optional<SysUserDO> findByUsername(String username);

    boolean existsByUsername(String username);
}
