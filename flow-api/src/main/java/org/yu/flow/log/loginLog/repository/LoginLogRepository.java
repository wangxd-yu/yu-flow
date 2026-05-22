package org.yu.flow.log.loginLog.repository;

import org.yu.flow.log.loginLog.domain.LoginLogDO;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

/**
 * 登录日志 Repository
 */
public interface LoginLogRepository extends JpaRepository<LoginLogDO, String>,
        JpaSpecificationExecutor<LoginLogDO> {
}
