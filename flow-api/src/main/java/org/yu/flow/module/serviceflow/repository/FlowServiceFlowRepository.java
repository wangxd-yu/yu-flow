package org.yu.flow.module.serviceflow.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.yu.flow.module.serviceflow.domain.FlowServiceFlowDO;

import java.util.List;

public interface FlowServiceFlowRepository
        extends JpaRepository<FlowServiceFlowDO, String>, JpaSpecificationExecutor<FlowServiceFlowDO> {

    boolean existsByDirectoryId(String directoryId);

    @Modifying
    @Query("UPDATE FlowServiceFlowDO s SET s.directoryId = :directoryId WHERE s.id IN :ids")
    int updateDirectoryIdByIds(@Param("directoryId") String directoryId, @Param("ids") List<String> ids);

    @Modifying(clearAutomatically = true)
    @Query("UPDATE FlowServiceFlowDO s SET s.deleted = 1 WHERE s.id IN :ids")
    int logicDeleteByIds(@Param("ids") List<String> ids);

    /** 粗筛：DSL / 发布快照中可能引用某 serviceId 的内部服务 */
    @Query("SELECT s FROM FlowServiceFlowDO s WHERE "
            + "(s.dslContent IS NOT NULL AND s.dslContent LIKE CONCAT('%', :needle, '%')) "
            + "OR (s.publishedSnapshot IS NOT NULL AND s.publishedSnapshot LIKE CONCAT('%', :needle, '%'))")
    List<FlowServiceFlowDO> findPossibleServiceFlowRefs(@Param("needle") String needle);
}
