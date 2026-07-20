package org.yu.flow.module.assetversion.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.yu.flow.module.assetversion.domain.FlowAssetVersionDO;

import java.util.List;
import java.util.Optional;

public interface FlowAssetVersionRepository extends JpaRepository<FlowAssetVersionDO, String> {

    Optional<FlowAssetVersionDO> findTopByBizTypeAndAssetIdOrderByVersionNoDesc(String bizType, String assetId);

    List<FlowAssetVersionDO> findByBizTypeAndAssetIdOrderByVersionNoDesc(String bizType, String assetId);

    long countByBizTypeAndAssetId(String bizType, String assetId);

    @Modifying(clearAutomatically = true)
    @Query("DELETE FROM FlowAssetVersionDO v WHERE v.bizType = :bizType AND v.assetId = :assetId AND v.versionNo < :minKeepVersionNo")
    int deleteOlderThan(@Param("bizType") String bizType,
                        @Param("assetId") String assetId,
                        @Param("minKeepVersionNo") int minKeepVersionNo);
}
