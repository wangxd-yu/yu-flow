package org.yu.flow.module.mqtask.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.yu.flow.module.mqtask.domain.FlowMqTaskLogDO;

import java.time.LocalDateTime;
import java.util.List;

/**
 * MQ 任务日志 JPA Repository
 *
 * @author yu-flow
 */
public interface FlowMqTaskLogRepository extends JpaRepository<FlowMqTaskLogDO, String>, JpaSpecificationExecutor<FlowMqTaskLogDO> {

    /** 清空某任务的全部日志 */
    @Modifying(clearAutomatically = true)
    @Query("DELETE FROM FlowMqTaskLogDO l WHERE l.taskId = :taskId")
    int deleteByTaskId(@Param("taskId") String taskId);

    /** 批量删除指定时间之前的 MQ 任务日志（定时清理） */
    @Modifying(clearAutomatically = true)
    @Query("DELETE FROM FlowMqTaskLogDO l WHERE l.createTime < :threshold")
    int deleteByCreateTimeBefore(@Param("threshold") LocalDateTime threshold);

    /** 删除某任务指定时间之前的日志（任务级保留天数清理） */
    @Modifying(clearAutomatically = true)
    @Query("DELETE FROM FlowMqTaskLogDO l WHERE l.taskId = :taskId AND l.createTime < :threshold")
    int deleteByTaskIdAndCreateTimeBefore(@Param("taskId") String taskId, @Param("threshold") LocalDateTime threshold);

    /** 批量删除指定时间之前、且不属于排除任务的日志（系统级保留天数清理，排除有任务级配置的任务） */
    @Modifying(clearAutomatically = true)
    @Query("DELETE FROM FlowMqTaskLogDO l WHERE l.createTime < :threshold AND (l.taskId IS NULL OR l.taskId NOT IN :excludeTaskIds)")
    int deleteByCreateTimeBeforeAndTaskIdNotIn(@Param("threshold") LocalDateTime threshold, @Param("excludeTaskIds") List<String> excludeTaskIds);
}
