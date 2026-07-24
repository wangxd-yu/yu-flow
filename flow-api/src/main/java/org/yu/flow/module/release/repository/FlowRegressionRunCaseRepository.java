package org.yu.flow.module.release.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.yu.flow.module.release.domain.FlowRegressionRunCaseDO;

import java.util.List;

public interface FlowRegressionRunCaseRepository extends JpaRepository<FlowRegressionRunCaseDO, String> {

    List<FlowRegressionRunCaseDO> findByRunIdOrderByIdAsc(String runId);
}
