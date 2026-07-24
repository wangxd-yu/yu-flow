package org.yu.flow.log.third.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.yu.flow.log.third.domain.FlowThirdLogDO;

import java.time.LocalDateTime;

@Repository
public interface FlowThirdLogRepository extends JpaRepository<FlowThirdLogDO, String>,
        JpaSpecificationExecutor<FlowThirdLogDO> {

    /** 批量删除指定时间之前的第三方调用日志（定时清理） */
    @Modifying(clearAutomatically = true)
    @Query("DELETE FROM FlowThirdLogDO l WHERE l.createTime < :threshold")
    int deleteByCreateTimeBefore(@Param("threshold") LocalDateTime threshold);
}
