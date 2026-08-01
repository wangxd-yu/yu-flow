package org.yu.flow.module.metrics.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.yu.flow.module.metrics.domain.FlowMetricsMetaDO;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface FlowMetricsMetaRepository extends JpaRepository<FlowMetricsMetaDO, String> {

    Optional<FlowMetricsMetaDO> findByAssetTypeAndAssetId(String assetType, String assetId);

    /** 批量健康查询：一次加载多个资产的 meta */
    List<FlowMetricsMetaDO> findByAssetTypeAndAssetIdIn(String assetType, Collection<String> assetIds);
}
