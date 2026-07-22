package org.yu.flow.module.metrics.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.yu.flow.module.metrics.domain.FlowMetricsMinuteDO;

import java.util.Date;
import java.util.List;
import java.util.Optional;

public interface FlowMetricsMinuteRepository extends JpaRepository<FlowMetricsMinuteDO, String> {

    Optional<FlowMetricsMinuteDO> findByAssetTypeAndAssetIdAndTriggerTypeAndBucketStart(
            String assetType, String assetId, String triggerType, Date bucketStart);

    List<FlowMetricsMinuteDO> findByAssetTypeAndAssetIdAndBucketStartGreaterThanEqualAndBucketStartLessThan(
            String assetType, String assetId, Date fromInclusive, Date toExclusive);

    List<FlowMetricsMinuteDO> findByAssetTypeAndBucketStartGreaterThanEqualAndBucketStartLessThan(
            String assetType, Date fromInclusive, Date toExclusive);

    @Modifying(clearAutomatically = true)
    @Query("DELETE FROM FlowMetricsMinuteDO m WHERE m.bucketStart < :before")
    int deleteByBucketStartBefore(@Param("before") Date before);
}
