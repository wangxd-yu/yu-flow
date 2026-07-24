package org.yu.flow.module.open.config;

import cn.hutool.core.exceptions.ValidateException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.yu.flow.auto.util.JwtTokenUtil;
import org.yu.flow.module.open.auth.HostAuthenticationProbe;

/**
 * 默认宿主登录探测：校验管理端 JWT（Flow-Authorization）。
 *
 * <p>宿主系统可提供自己的 {@link HostAuthenticationProbe} Bean 覆盖本默认实现。</p>
 */
@Configuration
public class HostAuthenticationProbeConfiguration {

    @Bean
    @ConditionalOnMissingBean(HostAuthenticationProbe.class)
    public HostAuthenticationProbe jwtHostAuthenticationProbe() {
        return request -> {
            String token = JwtTokenUtil.resolveToken(request);
            if (token == null || token.isBlank()) {
                return false;
            }
            try {
                JwtTokenUtil.validateToken(token);
                return true;
            } catch (ValidateException e) {
                return false;
            }
        };
    }
}
