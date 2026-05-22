package org.yu.flow.log.loginLog.controller;

import lombok.extern.slf4j.Slf4j;
import org.yu.flow.annotation.YuFlowApi;
import org.yu.flow.auto.dto.PageBean;
import org.yu.flow.dto.R;
import org.yu.flow.log.loginLog.dto.LoginLogDTO;
import org.yu.flow.log.loginLog.query.LoginLogQueryDTO;
import org.yu.flow.log.loginLog.service.LoginLogService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;

/**
 * 登录日志 Controller
 */
@Slf4j
@YuFlowApi
@RestController
@RequestMapping("/flow-api/login-logs")
public class LoginLogController {

    @Resource
    private LoginLogService loginLogService;

    /**
     * 分页查询登录日志
     * 支持按账号、状态、时间范围过滤
     */
    @GetMapping("/page")
    public R<PageBean<LoginLogDTO>> getPage(LoginLogQueryDTO queryDTO) {
        return R.ok(loginLogService.findPage(queryDTO));
    }
}
