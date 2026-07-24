package org.yu.flow.module.release.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.yu.flow.module.release.domain.FlowRegressionCaseDO;

import java.util.List;

public interface FlowRegressionCaseRepository extends JpaRepository<FlowRegressionCaseDO, String> {

    List<FlowRegressionCaseDO> findBySuiteIdOrderBySortOrderAsc(String suiteId);

    List<FlowRegressionCaseDO> findBySuiteIdAndEnabledOrderBySortOrderAsc(String suiteId, Integer enabled);

    long countBySuiteId(String suiteId);

    void deleteBySuiteId(String suiteId);
}
