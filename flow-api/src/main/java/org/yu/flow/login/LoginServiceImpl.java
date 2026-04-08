package org.yu.flow.login;

import org.yu.flow.auto.util.JwtTokenUtil;
import org.yu.flow.config.YuFlowProperties;
import org.yu.flow.login.dto.LoginDto;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class LoginServiceImpl implements LoginService {

    @Autowired
    private YuFlowProperties flowProperties;

    @Override
    public String login(LoginDto loginDto) {
        // 验证账号密码是否匹配配置文件中的值
        if (flowProperties.getUsername().equals(loginDto.getUsername()) &&
            flowProperties.getPassword().equals(loginDto.getPassword())) {
            String token = "Bearer " + JwtTokenUtil.generateToken(loginDto.getUsername(), loginDto.getPassword());
            return token;
        }
        return null;
    }
}
