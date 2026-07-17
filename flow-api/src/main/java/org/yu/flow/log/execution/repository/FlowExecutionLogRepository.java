package org.yu.flow.log.execution.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.yu.flow.log.execution.domain.FlowExecutionLogDO;

import java.util.Date;

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
    int deleteByCreateTimeBefore(@Param("threshold") Date threshold);
}
