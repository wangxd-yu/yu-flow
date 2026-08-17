package org.yu.flow.module.host;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.yu.flow.module.rbac.service.RbacService;

/**
 * 宿主身份 SPI 缺省装配：配置式解析（宿主机配置）+ 内置 JWT 兜底。
 * <p>宿主注册自己的 {@code FlowHost*Provider} Bean 时，这里整体不装配。</p>
 */
@Configuration
public class FlowHostAuthConfiguration {

    @Bean
    @ConditionalOnMissingBean(FlowHostPrincipalProvider.class)
    public FlowHostPrincipalProvider flowHostPrincipalProvider(
            RbacService rbacService,
            HostPrincipalSettingsStore settingsStore,
            ObjectProvider<HostPrincipalResolver> resolver) {
        return new ConfigurableFlowHostPrincipalProvider(
                new BuiltinJwtFlowHostPrincipalProvider(rbacService), settingsStore, resolver);
    }

    @Bean
    @ConditionalOnMissingBean(FlowHostDataScopeProvider.class)
    public FlowHostDataScopeProvider flowHostDataScopeProvider(RbacService rbacService,
                                                               HostPrincipalSettingsStore settingsStore) {
        return new ConfigurableFlowHostDataScopeProvider(
                new BuiltinJwtFlowHostDataScopeProvider(rbacService), settingsStore);
    }
}
