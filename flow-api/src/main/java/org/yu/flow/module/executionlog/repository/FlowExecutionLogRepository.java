package org.yu.flow.module.executionlog.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;
import org.yu.flow.module.executionlog.domain.FlowExecutionLogDO;

@Repository
public interface FlowExecutionLogRepository extends JpaRepository<FlowExecutionLogDO, String>, JpaSpecificationExecutor<FlowExecutionLogDO> {
}
