package org.yu.flow.module.rbac.config;

import cn.hutool.core.util.StrUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.yu.flow.config.YuFlowProperties;
import org.yu.flow.module.rbac.domain.SysRoleDO;
import org.yu.flow.module.rbac.domain.SysUserDO;
import org.yu.flow.module.rbac.domain.SysUserRoleDO;
import org.yu.flow.module.rbac.repository.SysRoleRepository;
import org.yu.flow.module.rbac.repository.SysUserRepository;
import org.yu.flow.module.rbac.repository.SysUserRoleRepository;
import org.yu.flow.module.rbac.support.RbacPasswordUtil;

import jakarta.annotation.Resource;
import java.time.LocalDateTime;

/**
 * 启动时确保 yml 管理员账号落库并绑定 ADMIN（幂等）。
 */
@Slf4j
@Component
@Order(50)
public class RbacAdminBootstrap implements ApplicationRunner {

    @Resource
    private YuFlowProperties yuFlowProperties;
    @Resource
    private SysUserRepository userRepository;
    @Resource
    private SysRoleRepository roleRepository;
    @Resource
    private SysUserRoleRepository userRoleRepository;

    @Override
    public void run(ApplicationArguments args) {
        try {
            String username = yuFlowProperties.getUsername();
            String password = yuFlowProperties.getPassword();
            if (StrUtil.isBlank(username) || StrUtil.isBlank(password)) {
                return;
            }
            SysRoleDO adminRole = roleRepository.findByRoleCode("ADMIN").orElse(null);
            if (adminRole == null) {
                log.warn("[RBAC] ADMIN 角色尚未初始化（Flyway 未执行？），跳过管理员引导");
                return;
            }

            SysUserDO user = userRepository.findByUsername(username.trim()).orElse(null);
            LocalDateTime now = LocalDateTime.now();
            if (user == null) {
                user = SysUserDO.builder()
                        .username(username.trim())
                        .passwordHash(RbacPasswordUtil.hash(password))
                        .displayName(username.trim())
                        .status(1)
                        .isBuiltin(1)
                        .remark("由 yu.flow.username 引导创建")
                        .createTime(now)
                        .updateTime(now)
                        .build();
                user = userRepository.save(user);
                userRoleRepository.save(new SysUserRoleDO(user.getId(), adminRole.getId()));
                log.info("[RBAC] 已引导创建管理员用户: {}", username);
            } else {
                // 保持内置标记；不自动覆盖密码（避免覆盖运维在 UI 改的密）
                if (user.getIsBuiltin() == null || user.getIsBuiltin() != 1) {
                    user.setIsBuiltin(1);
                    user.setUpdateTime(now);
                    userRepository.save(user);
                }
                boolean hasAdmin = userRoleRepository.findByUserId(user.getId()).stream()
                        .anyMatch(ur -> adminRole.getId().equals(ur.getRoleId()));
                if (!hasAdmin) {
                    userRoleRepository.save(new SysUserRoleDO(user.getId(), adminRole.getId()));
                    log.info("[RBAC] 已为用户 {} 补绑 ADMIN", username);
                }
            }
        } catch (Exception e) {
            log.warn("[RBAC] 管理员引导失败（可稍后手动创建）: {}", e.getMessage());
        }
    }
}
