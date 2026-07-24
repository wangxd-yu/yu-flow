package org.yu.flow.module.release.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.yu.flow.module.release.domain.FlowRegressionSuiteDO;

import java.util.List;
import java.util.Optional;

public interface FlowRegressionSuiteRepository
        extends JpaRepository<FlowRegressionSuiteDO, String>, JpaSpecificationExecutor<FlowRegressionSuiteDO> {

    List<FlowRegressionSuiteDO> findByAssetTypeAndAssetIdAndEnabled(String assetType, String assetId, Integer enabled);

    Optional<FlowRegressionSuiteDO> findFirstByAssetTypeAndAssetIdOrderByUpdateTimeDesc(String assetType, String assetId);

    long countByAssetTypeAndAssetId(String assetType, String assetId);
}
