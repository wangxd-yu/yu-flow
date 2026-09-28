package org.yu.flow.module.release.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.yu.flow.module.release.domain.FlowReleaseItemDO;

import java.util.List;

public interface FlowReleaseItemRepository extends JpaRepository<FlowReleaseItemDO, String> {

    List<FlowReleaseItemDO> findByReleaseIdOrderByCreateTimeAsc(String releaseId);

    boolean existsByReleaseIdAndAssetTypeAndAssetId(String releaseId, String assetType, String assetId);

    long countByReleaseId(String releaseId);

    @Modifying
    void deleteByReleaseId(String releaseId);
}
