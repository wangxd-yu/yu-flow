package org.yu.flow.module.alert.repository;

import org.yu.flow.module.alert.domain.AlertEventDO;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;

public interface AlertEventRepository extends JpaRepository<AlertEventDO, String>,
        JpaSpecificationExecutor<AlertEventDO> {

    /** 批量删除指定时间之前的告警历史（定时清理，按 firedAt） */
    @Modifying(clearAutomatically = true)
    @Query("DELETE FROM AlertEventDO e WHERE e.firedAt < :threshold")
    int deleteByFiredAtBefore(@Param("threshold") LocalDateTime threshold);
}
