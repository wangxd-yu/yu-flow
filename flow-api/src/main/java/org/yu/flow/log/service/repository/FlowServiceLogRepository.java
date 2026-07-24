package org.yu.flow.log.service.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.yu.flow.log.service.domain.FlowServiceLogDO;

import java.util.Date;

public interface FlowServiceLogRepository
        extends JpaRepository<FlowServiceLogDO, String>, JpaSpecificationExecutor<FlowServiceLogDO> {

    @Modifying(clearAutomatically = true)
    @Query("DELETE FROM FlowServiceLogDO l WHERE l.serviceId = :serviceId")
    int deleteByServiceId(@Param("serviceId") String serviceId);

    /** 批量删除指定时间之前的服务日志（定时清理） */
    @Modifying(clearAutomatically = true)
    @Query("DELETE FROM FlowServiceLogDO l WHERE l.createTime < :threshold")
    int deleteByCreateTimeBefore(@Param("threshold") Date threshold);
}
