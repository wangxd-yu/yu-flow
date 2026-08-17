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

    /** 含逻辑删除行的存在性判断（跨环境导入按 ID upsert 时用于识别被删除过的同 ID 服务） */
    @Query(value = "SELECT COUNT(1) FROM flow_service_info WHERE id = :id", nativeQuery = true)
    long countAnyById(@Param("id") String id);

    /** 恢复逻辑删除行，使其重新可见 */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = "UPDATE flow_service_info SET deleted = 0 WHERE id = :id", nativeQuery = true)
    int restoreDeletedById(@Param("id") String id);

    /** 粗筛：DSL / 发布快照中可能引用某 serviceId 的内部服务 */
    @Query("SELECT s FROM FlowServiceFlowDO s WHERE "
            + "(s.dslContent IS NOT NULL AND s.dslContent LIKE CONCAT('%', :needle, '%')) "
            + "OR (s.publishedSnapshot IS NOT NULL AND s.publishedSnapshot LIKE CONCAT('%', :needle, '%'))")
    List<FlowServiceFlowDO> findPossibleServiceFlowRefs(@Param("needle") String needle);
}
