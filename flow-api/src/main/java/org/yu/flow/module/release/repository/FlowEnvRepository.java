package org.yu.flow.module.release.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.yu.flow.module.release.domain.FlowEnvDO;

import java.util.List;
import java.util.Optional;

public interface FlowEnvRepository extends JpaRepository<FlowEnvDO, String> {

    Optional<FlowEnvDO> findByCode(String code);

    List<FlowEnvDO> findByEnabledOrderBySortOrderAsc(Integer enabled);
}
