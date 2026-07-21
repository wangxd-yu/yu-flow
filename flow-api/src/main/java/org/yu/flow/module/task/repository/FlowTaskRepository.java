package org.yu.flow.module.task.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.yu.flow.module.task.domain.FlowTaskDO;

import java.util.List;

/**
 * 定时任务 JPA Repository
 *
 * @author yu-flow
 */
public interface FlowTaskRepository extends JpaRepository<FlowTaskDO, String>, JpaSpecificationExecutor<FlowTaskDO> {

    /** 查询所有启用的任务（用于调度器启动时加载） */
    List<FlowTaskDO> findByEnabled(Boolean enabled);

    /** 判断某个目录下是否有任务（删除目录时校验） */
    boolean existsByDirectoryId(String directoryId);

    @Modifying
    @Query("UPDATE FlowTaskDO t SET t.directoryId = :directoryId WHERE t.id IN :ids")
    int updateDirectoryIdByIds(@Param("directoryId") String directoryId, @Param("ids") List<String> ids);

    @Modifying(clearAutomatically = true)
    @Query("UPDATE FlowTaskDO t SET t.deleted = 1 WHERE t.id IN :ids")
    int logicDeleteByIds(@Param("ids") List<String> ids);

    /** 粗筛：DSL / 发布快照中可能引用某 ID 的定时任务 */
    @Query("SELECT t FROM FlowTaskDO t WHERE "
            + "(t.dslContent IS NOT NULL AND t.dslContent LIKE CONCAT('%', :needle, '%')) "
            + "OR (t.publishedSnapshot IS NOT NULL AND t.publishedSnapshot LIKE CONCAT('%', :needle, '%'))")
    List<FlowTaskDO> findPossibleServiceFlowRefs(@Param("needle") String needle);
}
