package org.yu.flow.module.host;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.yu.flow.module.rbac.service.RbacService;

/**
 * 宿主身份 SPI 缺省装配：内置 JWT 实现。
 */
@Configuration
public class FlowHostAuthConfiguration {

    @Bean
    @ConditionalOnMissingBean(FlowHostPrincipalProvider.class)
    public FlowHostPrincipalProvider flowHostPrincipalProvider() {
        return new BuiltinJwtFlowHostPrincipalProvider();
    }

    @Bean
    @ConditionalOnMissingBean(FlowHostDataScopeProvider.class)
    public FlowHostDataScopeProvider flowHostDataScopeProvider(RbacService rbacService) {
        return new BuiltinJwtFlowHostDataScopeProvider(rbacService);
    }
}
