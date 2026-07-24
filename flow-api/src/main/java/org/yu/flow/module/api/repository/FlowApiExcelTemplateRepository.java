package org.yu.flow.module.api.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.yu.flow.module.api.domain.FlowApiExcelTemplateDO;

import java.util.Optional;

public interface FlowApiExcelTemplateRepository extends JpaRepository<FlowApiExcelTemplateDO, String> {

    Optional<FlowApiExcelTemplateDO> findByApiId(String apiId);

    void deleteByApiId(String apiId);

    boolean existsByApiId(String apiId);
}
