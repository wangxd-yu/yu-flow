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
        }

        // 2) yml 兜底：默认关闭；仅 allow-yml-admin-fallback=true 时用于紧急运维
        boolean allowFallback = flowProperties.getSecurity() != null
                && flowProperties.getSecurity().isAllowYmlAdminFallback();
        if (!allowFallback) {
            return null;
        }
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
            // 禁止签发无 userId 的永久 ADMIN legacy 会话
            return null;
        }
        return null;
    }
}
