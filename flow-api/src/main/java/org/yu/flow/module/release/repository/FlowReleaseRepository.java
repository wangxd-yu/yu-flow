package org.yu.flow.module.release.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.yu.flow.module.release.domain.FlowReleaseDO;

public interface FlowReleaseRepository extends JpaRepository<FlowReleaseDO, String>,
        JpaSpecificationExecutor<FlowReleaseDO> {

    boolean existsByCode(String code);
}
