package org.yu.flow.module.envvar.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.yu.flow.module.envvar.domain.SysEnvVariableDO;

import java.util.List;
import java.util.Optional;

public interface SysEnvVariableRepository extends JpaRepository<SysEnvVariableDO, String> {

    Optional<SysEnvVariableDO> findByCode(String code);

    boolean existsByCode(String code);

    List<SysEnvVariableDO> findAllByOrderByCodeAsc();
}
