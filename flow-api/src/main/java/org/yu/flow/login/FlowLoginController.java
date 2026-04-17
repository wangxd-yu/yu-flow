package org.yu.flow.login;

import org.yu.flow.annotation.YuFlowApi;

import lombok.extern.slf4j.Slf4j;
import org.yu.flow.dto.R;
import org.yu.flow.login.dto.LoginDto;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;

/**
 * @author yu-flow
 * @date 2025-07-28 23:32
 */
@Slf4j
@YuFlowApi
@RestController
@RequestMapping(value = {"flow-api"})
public class FlowLoginController {

    @Resource
    private LoginService loginService;

    @PostMapping("/login")
    public R<String> login(@RequestBody LoginDto loginDto) {
        String token = loginService.login(loginDto);
        if (token == null) {
            return R.fail("登录失败，用户名或密码错误");
        }
        return R.ok(token);
    }

}
