package org.yu.flow.log.execution.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.yu.flow.log.execution.domain.FlowExecutionLogDO;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface FlowExecutionLogRepository extends JpaRepository<FlowExecutionLogDO, String>, JpaSpecificationExecutor<FlowExecutionLogDO> {

    /**
     * 批量删除指定时间之前的执行日志（用于定时清理）。
     * 使用 JPQL bulk delete 避免加载 LONGTEXT 字段到内存。
     *
     * @param threshold 时间阈值，早于此时间的记录将被删除
     * @return 被删除的记录数
     */
    @Modifying
    @Query("DELETE FROM FlowExecutionLogDO e WHERE e.createTime < :threshold")
    int deleteByCreateTimeBefore(@Param("threshold") LocalDateTime threshold);

    /**
     * 按 API 删除指定时间之前的执行日志（API 级保留天数覆盖）。
     */
    @Modifying(clearAutomatically = true)
    @Query("DELETE FROM FlowExecutionLogDO e WHERE e.apiId = :apiId AND e.createTime < :threshold")
    int deleteByApiIdAndCreateTimeBefore(@Param("apiId") String apiId, @Param("threshold") LocalDateTime threshold);

    /**
     * 系统级清理：删除指定时间之前的执行日志，排除有 API 级保留配置的 API。
     */
    @Modifying(clearAutomatically = true)
    @Query("DELETE FROM FlowExecutionLogDO e WHERE e.createTime < :threshold AND (e.apiId IS NULL OR e.apiId NOT IN :excludeApiIds)")
    int deleteByCreateTimeBeforeAndApiIdNotIn(@Param("threshold") LocalDateTime threshold, @Param("excludeApiIds") List<String> excludeApiIds);
}
