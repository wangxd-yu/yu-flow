package org.yu.flow.module.log.login.service;

import org.yu.flow.dto.PageBean;
import org.yu.flow.module.log.login.domain.LoginLogDO;
import org.yu.flow.module.log.login.dto.LoginLogDTO;
import org.yu.flow.module.log.login.query.LoginLogQueryDTO;

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
