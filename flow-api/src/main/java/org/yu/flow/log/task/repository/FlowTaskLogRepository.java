package org.yu.flow.log.task.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.yu.flow.log.task.domain.FlowTaskLogDO;

import java.util.Date;

/**
 * 定时任务日志 JPA Repository
 *
 * @author yu-flow
 */
public interface FlowTaskLogRepository extends JpaRepository<FlowTaskLogDO, String>, JpaSpecificationExecutor<FlowTaskLogDO> {

    /** 清空某任务的全部日志 */
    @Modifying(clearAutomatically = true)
    @Query("DELETE FROM FlowTaskLogDO l WHERE l.taskId = :taskId")
    int deleteByTaskId(@Param("taskId") String taskId);

    /** 批量删除指定时间之前的任务日志（定时清理） */
    @Modifying(clearAutomatically = true)
    @Query("DELETE FROM FlowTaskLogDO l WHERE l.createTime < :threshold")
    int deleteByCreateTimeBefore(@Param("threshold") Date threshold);
}
