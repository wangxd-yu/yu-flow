package org.yu.flow.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.yu.flow.engine.evaluator.expression.ExpressionEngine;
import org.yu.flow.engine.evaluator.expression.SafeSpelExpressionEngine;
import org.yu.flow.engine.evaluator.expression.SimpleExpressionEngine;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.core.Ordered;
import org.yu.flow.auto.service.FlowApiExecutionService;
import org.yu.flow.config.response.ResponseStrategyResolver;
import org.yu.flow.config.response.ResponseTransformer;
import org.yu.flow.module.api.cache.ApiResponseCacheService;

/**
 * yu-flow 自动装配入口。
 *
 * <p>职责：</p>
 * <ul>
 *   <li>激活 {@link YuFlowProperties} 配置树</li>
 *   <li>扫描 {@code org.yu.flow} 包下的所有组件</li>
 *   <li>注册引擎核心 Bean（ExpressionEngine、ObjectMapper 等）</li>
 * </ul>
 *
 * @author yu-flow
 * @since 1.0
 */
@AutoConfiguration
@ConditionalOnProperty(prefix = "yu.flow", name = "enabled", havingValue = "true", matchIfMissing = true)
@ComponentScan(
        basePackages = "org.yu.flow",
        excludeFilters = @ComponentScan.Filter(
                type = FilterType.REGEX,
                pattern = "org\\.yu\\.flow\\.module\\.oss\\..*"
        )
)
@EnableConfigurationProperties(YuFlowProperties.class)
@EnableJpaRepositories(basePackages = {"org.yu.flow"})
@EntityScan(basePackages = "org.yu.flow")
public class FlowAutoConfiguration {

    /**
     * 全局 ObjectMapper（支持 Java 8 时间类型序列化）。
     */
    @Bean(name = "flowObjectMapper")
    public ObjectMapper flowObjectMapper() {
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        return objectMapper;
    }

    /**
     * 根据配置选择表达式引擎实现。
     *
     * <ul>
     *   <li>{@code yu.flow.engine.expression-engine=simple} → {@link SimpleExpressionEngine}（默认、推荐）</li>
     *   <li>{@code yu.flow.engine.expression-engine=spel} → {@link SafeSpelExpressionEngine}</li>
     * </ul>
     *
     * @param properties 统一配置属性
     * @return 表达式引擎实例
     */
    @Bean
    public ExpressionEngine expressionEngine(YuFlowProperties properties) {
        String type = properties.getEngine().getExpressionEngine();
        if ("spel".equalsIgnoreCase(type)) {
            return new SafeSpelExpressionEngine();
        }
        return new SimpleExpressionEngine();
    }

    @Bean
    public FilterRegistrationBean<FlowApiGatewayFilter> flowApiGatewayFilterRegistration(
            YuFlowProperties flowProperties,
            FlowApiExecutionService flowApiService,
            FlowApiCacheManager flowApiCacheManager,
            SchemaValidatorService schemaValidatorService,
            ContractParamTypeConverter contractParamTypeConverter,
            ResponseStrategyResolver responseStrategyResolver,
            ResponseTransformer responseTransformer,
            ApiResponseCacheService apiResponseCacheService,
            org.yu.flow.module.open.auth.OpenAuthService openAuthService,
            org.yu.flow.module.metrics.AssetMetricsRecorder assetMetricsRecorder,
            org.yu.flow.module.open.auth.HostAuthenticationProbe hostAuthenticationProbe,
            org.yu.flow.module.api.security.IngressSecurityResolver ingressSecurityResolver,
            org.yu.flow.module.api.security.IngressSecurityGuard ingressSecurityGuard,
            org.yu.flow.module.sysconfig.support.YuFlowRuntimeSettings yuFlowRuntimeSettings,
            org.yu.flow.module.api.service.ApiDataViewService apiDataViewService,
            org.yu.flow.module.rbac.service.RbacService rbacService,
            org.yu.flow.log.execution.service.FlowExecutionLogService flowExecutionLogService,
            org.yu.flow.module.api.privacy.PrivacyFieldInterceptor privacyFieldInterceptor) {

        FlowApiGatewayFilter filter = new FlowApiGatewayFilter(flowProperties, flowApiService, flowApiCacheManager,
                schemaValidatorService, contractParamTypeConverter, responseStrategyResolver, responseTransformer,
                apiResponseCacheService, openAuthService, assetMetricsRecorder, hostAuthenticationProbe,
                ingressSecurityResolver, ingressSecurityGuard, yuFlowRuntimeSettings, apiDataViewService,
                rbacService, flowExecutionLogService, privacyFieldInterceptor);

        FilterRegistrationBean<FlowApiGatewayFilter> registration = new FilterRegistrationBean<>(filter);
        registration.addUrlPatterns("/*");
        registration.setName("flowApiGatewayFilter");
        // LOWEST_PRECEDENCE - 10：晚于宿主 LoginHolder Filter（SSP 为 LOWEST_PRECEDENCE - 20）。
        // 已发布 API 在本 Filter 内短路写出，必须先有宿主主体，否则出站隐私按匿名 MASK。
        registration.setOrder(Ordered.LOWEST_PRECEDENCE - 10);
        return registration;
    }
}
