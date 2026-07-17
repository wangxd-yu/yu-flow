package org.yu.flow.log.login.repository;

import org.yu.flow.log.login.domain.LoginLogDO;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;

/**
 * 登录日志 Repository
 */
public interface LoginLogRepository extends JpaRepository<LoginLogDO, String>,
        JpaSpecificationExecutor<LoginLogDO> {

    /**
     * 批量删除指定时间之前的登录日志（用于定时清理）。
     *
     * @param threshold 时间阈值，早于此时间的记录将被删除
     * @return 被删除的记录数
     */
    @Modifying
    @Query("DELETE FROM LoginLogDO l WHERE l.createTime < :threshold")
    int deleteByCreateTimeBefore(@Param("threshold") LocalDateTime threshold);
}
