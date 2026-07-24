package org.yu.flow.module.release.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.yu.flow.module.release.domain.FlowRegressionRunDO;

import java.time.LocalDateTime;
import java.util.Optional;

public interface FlowRegressionRunRepository
        extends JpaRepository<FlowRegressionRunDO, String>, JpaSpecificationExecutor<FlowRegressionRunDO> {

    Optional<FlowRegressionRunDO> findFirstByAssetTypeAndAssetIdAndEnvCodeAndStatusAndFinishedAtAfterOrderByFinishedAtDesc(
            String assetType, String assetId, String envCode, String status, LocalDateTime after);
}
