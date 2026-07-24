package org.yu.flow.module.rbac.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.yu.flow.module.rbac.domain.SysPermissionDO;

public interface SysPermissionRepository extends JpaRepository<SysPermissionDO, String> {
}
