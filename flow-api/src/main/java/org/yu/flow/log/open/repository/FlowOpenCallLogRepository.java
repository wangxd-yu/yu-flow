package org.yu.flow.log.open.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.yu.flow.log.open.domain.FlowOpenCallLogDO;

import java.util.Date;

public interface FlowOpenCallLogRepository extends JpaRepository<FlowOpenCallLogDO, String> {

    /** 批量删除指定时间之前的开放平台调用日志（定时清理） */
    @Modifying(clearAutomatically = true)
    @Query("DELETE FROM FlowOpenCallLogDO l WHERE l.createTime < :threshold")
    int deleteByCreateTimeBefore(@Param("threshold") Date threshold);
}
