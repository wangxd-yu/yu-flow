package org.yu.flow.module.metrics.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.yu.flow.module.metrics.domain.FlowMetricsMetaDO;

import java.util.Optional;

public interface FlowMetricsMetaRepository extends JpaRepository<FlowMetricsMetaDO, String> {

    Optional<FlowMetricsMetaDO> findByAssetTypeAndAssetId(String assetType, String assetId);
}
