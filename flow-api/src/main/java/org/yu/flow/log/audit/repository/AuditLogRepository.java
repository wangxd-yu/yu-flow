package org.yu.flow.log.audit.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.yu.flow.log.audit.domain.AuditLogDO;

import java.time.LocalDateTime;

public interface AuditLogRepository extends JpaRepository<AuditLogDO, String>, JpaSpecificationExecutor<AuditLogDO> {

    /** 批量删除指定时间之前的配置审计日志（定时清理） */
    @Modifying(clearAutomatically = true)
    @Query("DELETE FROM AuditLogDO l WHERE l.createTime < :threshold")
    int deleteByCreateTimeBefore(@Param("threshold") LocalDateTime threshold);
}
