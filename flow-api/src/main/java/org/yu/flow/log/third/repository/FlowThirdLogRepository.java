package org.yu.flow.log.third.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;
import org.yu.flow.log.third.domain.FlowThirdLogDO;

@Repository
public interface FlowThirdLogRepository extends JpaRepository<FlowThirdLogDO, String>,
        JpaSpecificationExecutor<FlowThirdLogDO> {
}
