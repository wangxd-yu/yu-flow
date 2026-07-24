package org.yu.flow.login;

import cn.hutool.core.util.StrUtil;
import org.yu.flow.auto.util.JwtTokenUtil;
import org.yu.flow.config.YuFlowProperties;
import org.yu.flow.login.dto.LoginDto;
import org.yu.flow.module.rbac.domain.SysUserDO;
import org.yu.flow.module.rbac.service.RbacService;
import org.springframework.stereotype.Service;

import jakarta.annotation.Resource;
import java.util.List;

@Service
public class LoginServiceImpl implements LoginService {

    @Resource
    private YuFlowProperties flowProperties;
    @Resource
    private RbacService rbacService;

    @Override
    public String login(LoginDto loginDto) {
        if (loginDto == null || StrUtil.isBlank(loginDto.getUsername()) || StrUtil.isBlank(loginDto.getPassword())) {
            return null;
        }
        String username = loginDto.getUsername().trim();
        String password = loginDto.getPassword();

        // 1) RBAC 开启：优先 DB 用户
        if (rbacService.isRbacEnabled()) {
            SysUserDO user = rbacService.authenticateDbUser(username, password);
            if (user != null) {
                List<String> roles = rbacService.resolveRoleCodes(user.getId());
                String token = JwtTokenUtil.generateToken(user.getUsername(), user.getId(), roles);
                return "Bearer " + token;
            }
            // DB 无此用户 / 密码不匹配时，允许 yml 账号兜底（引导期 / 紧急运维）
        }

        // 2) yml 单账号兜底：若库中已有同名用户，签发时带上 userId，避免变成「无法改密」的 legacy 会话
        if (flowProperties.getUsername().equals(username)
                && flowProperties.getPassword().equals(password)) {
            SysUserDO dbUser = rbacService.findEnabledUserByUsername(username);
            if (dbUser != null) {
                List<String> roles = rbacService.resolveRoleCodes(dbUser.getId());
                if (roles == null || roles.isEmpty()) {
                    roles = List.of("ADMIN");
                }
                return "Bearer " + JwtTokenUtil.generateToken(dbUser.getUsername(), dbUser.getId(), roles);
            }
            String token = JwtTokenUtil.generateToken(username, null, List.of("ADMIN"));
            return "Bearer " + token;
        }
        return null;
    }
}
