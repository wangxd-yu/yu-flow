package org.yu.flow.log.login.service;

import org.yu.flow.auto.dto.PageBean;
import org.yu.flow.log.login.domain.LoginLogDO;
import org.yu.flow.log.login.dto.LoginLogDTO;
import org.yu.flow.log.login.query.LoginLogQueryDTO;

/**
 * 登录日志 Service 接口
 */
public interface LoginLogService {

    /**
     * 保存登录日志
     */
    void saveLog(LoginLogDO log);

    /**
     * 分页查询登录日志
     */
    PageBean<LoginLogDTO> findPage(LoginLogQueryDTO queryDTO);
}
