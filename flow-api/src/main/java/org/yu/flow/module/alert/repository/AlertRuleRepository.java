package org.yu.flow.module.alert.repository;

import org.yu.flow.module.alert.domain.AlertRuleDO;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;

public interface AlertRuleRepository extends JpaRepository<AlertRuleDO, String>,
        JpaSpecificationExecutor<AlertRuleDO> {

    List<AlertRuleDO> findByEnabled(Integer enabled);
}
