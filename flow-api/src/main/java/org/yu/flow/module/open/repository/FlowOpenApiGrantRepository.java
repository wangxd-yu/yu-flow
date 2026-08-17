package org.yu.flow.module.open.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.yu.flow.module.open.domain.FlowOpenApiGrantDO;

import java.util.List;
import java.util.Optional;

public interface FlowOpenApiGrantRepository extends JpaRepository<FlowOpenApiGrantDO, String> {

    List<FlowOpenApiGrantDO> findByPlatformId(String platformId);

    List<FlowOpenApiGrantDO> findByApiId(String apiId);

    Optional<FlowOpenApiGrantDO> findByPlatformIdAndApiId(String platformId, String apiId);

    boolean existsByPlatformIdAndApiId(String platformId, String apiId);

    long countByPlatformId(String platformId);

    @Modifying(clearAutomatically = true)
    @Query("DELETE FROM FlowOpenApiGrantDO g WHERE g.platformId = :platformId")
    int deleteByPlatformId(@Param("platformId") String platformId);

    @Modifying(clearAutomatically = true)
    @Query("DELETE FROM FlowOpenApiGrantDO g WHERE g.apiId = :apiId")
    int deleteByApiId(@Param("apiId") String apiId);

    @Modifying(clearAutomatically = true)
    @Query("DELETE FROM FlowOpenApiGrantDO g WHERE g.apiId IN :apiIds")
    int deleteByApiIdIn(@Param("apiIds") List<String> apiIds);
}
