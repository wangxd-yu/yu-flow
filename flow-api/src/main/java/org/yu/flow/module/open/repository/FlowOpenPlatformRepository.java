package org.yu.flow.module.open.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.yu.flow.module.open.domain.FlowOpenPlatformDO;

import java.util.Optional;

public interface FlowOpenPlatformRepository extends JpaRepository<FlowOpenPlatformDO, String>,
        JpaSpecificationExecutor<FlowOpenPlatformDO> {

    boolean existsByCode(String code);

    boolean existsByCodeAndIdNot(String code, String id);

    Optional<FlowOpenPlatformDO> findByCode(String code);
}
