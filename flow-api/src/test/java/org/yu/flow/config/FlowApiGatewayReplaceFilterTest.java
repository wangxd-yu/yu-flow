package org.yu.flow.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.yu.flow.auto.service.FlowApiExecutionService;
import org.yu.flow.auto.util.JwtTokenUtil;
import org.yu.flow.config.response.ResponseStrategyResolver;
import org.yu.flow.config.response.ResponseTransformer;
import org.yu.flow.config.response.ResponseWrapperContext;
import org.yu.flow.exception.SchemaValidationException;
import org.yu.flow.module.api.cache.ApiCacheConfig;
import org.yu.flow.module.api.cache.ApiResponseCacheService;
import org.yu.flow.module.api.domain.FlowApiDO;
import org.yu.flow.module.api.security.EffectiveSecurity;
import org.yu.flow.module.api.security.IngressSecurityResolver;
import org.yu.flow.module.metrics.AssetMetricsRecorder;
import org.yu.flow.module.metrics.MetricsAssetType;
import org.yu.flow.module.metrics.MetricsOutcome;
import org.yu.flow.module.open.auth.HostAuthenticationProbe;
import org.yu.flow.module.open.auth.OpenAuthService;
import org.yu.flow.module.rbac.dto.AuthMeDTO;
import org.yu.flow.module.rbac.service.RbacService;

import jakarta.servlet.http.HttpServletRequest;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * REPLACE（已发布动态 API 接管真实 path）关键路径的网关 Filter 级测试（不启 Spring / Redis）：
 * 覆盖匿名 401、幽灵用户 401、方法不匹配 405、契约校验 400、响应缓存 HIT、
 * 成功执行 200 与硬超时 504。
 */
@ExtendWith(MockitoExtension.class)
class FlowApiGatewayReplaceFilterTest {

    @Mock FlowApiExecutionService flowApiService;
    @Mock FlowApiCacheManager flowApiCacheManager;
    @Mock SchemaValidatorService schemaValidatorService;
    @Mock ContractParamTypeConverter contractParamTypeConverter;
    @Mock ResponseStrategyResolver responseStrategyResolver;
    @Mock ResponseTransformer responseTransformer;
    @Mock ApiResponseCacheService apiResponseCacheService;
    @Mock OpenAuthService openAuthService;
    @Mock AssetMetricsRecorder assetMetricsRecorder;
    @Mock HostAuthenticationProbe hostAuthenticationProbe;
    @Mock RbacService rbacService;
    @Mock IngressSecurityResolver ingressSecurityResolver;

    private YuFlowProperties props;
    private FlowApiGatewayFilter filter;

    @BeforeEach
    void setUp() {
        props = new YuFlowProperties();
        props.setEnabled(true);
        // 关闭开放入口，聚焦 REPLACE 分支（ingress 关闭 → 管理端 JWT 兜底）
        props.getOpen().setEnabled(false);
        props.getOpen().setRequireHostAuth(false);
        filter = buildFilter(null);
    }

    private FlowApiGatewayFilter buildFilter(IngressSecurityResolver resolver) {
        return new FlowApiGatewayFilter(
                props, flowApiService, flowApiCacheManager, schemaValidatorService,
                contractParamTypeConverter, responseStrategyResolver, responseTransformer,
                apiResponseCacheService, openAuthService, assetMetricsRecorder, hostAuthenticationProbe,
                resolver, null, null, null, rbacService);
    }

    /** 管理端 JWT 通过：resolveToken/validateToken/getUsername 全部放行，会话主体启用 */
    private MockedStatic<JwtTokenUtil> authedJwt() {
        MockedStatic<JwtTokenUtil> jwt = mockStatic(JwtTokenUtil.class);
        jwt.when(() -> JwtTokenUtil.resolveToken(any(HttpServletRequest.class))).thenReturn("tok");
        jwt.when(() -> JwtTokenUtil.getUsername("tok")).thenReturn("admin");
        when(rbacService.buildMe("admin")).thenReturn(mock(AuthMeDTO.class));
        return jwt;
    }

    private void stubExecuteSuccess() {
        when(apiResponseCacheService.parseConfig(any())).thenReturn(null);
        when(apiResponseCacheService.isEnabled(any())).thenReturn(false);
        when(responseStrategyResolver.resolve(any())).thenReturn(new ResponseWrapperContext());
        when(responseTransformer.transform(any(), any())).thenAnswer(inv -> inv.getArgument(0));
    }

    private static FlowApiDO replaceApi() {
        return FlowApiDO.builder()
                .id("api1")
                .name("demo")
                .url("/demo/hello")
                .method("GET")
                .publishStatus(1)
                .responseType("OBJECT")
                .build();
    }

    // ==================== 鉴权关口 ====================

    @Test
    void anonymous_returns401TokenEmpty() throws Exception {
        when(flowApiCacheManager.getExactMatch("GET", "/demo/hello")).thenReturn(replaceApi());

        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/demo/hello");
        MockHttpServletResponse res = new MockHttpServletResponse();

        filter.doFilter(req, res, new MockFilterChain());

        assertEquals(401, res.getStatus());
        assertTrue(res.getContentAsString().contains("token 不能为空"));
        verifyNoInteractions(flowApiService);
    }

    @Test
    void ghostOrDisabledUser_returns401() throws Exception {
        when(flowApiCacheManager.getExactMatch("GET", "/demo/hello")).thenReturn(replaceApi());
        when(rbacService.buildMe("ghost")).thenReturn(null);

        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/demo/hello");
        MockHttpServletResponse res = new MockHttpServletResponse();

        try (MockedStatic<JwtTokenUtil> jwt = mockStatic(JwtTokenUtil.class)) {
            jwt.when(() -> JwtTokenUtil.resolveToken(any(HttpServletRequest.class))).thenReturn("tok");
            jwt.when(() -> JwtTokenUtil.getUsername("tok")).thenReturn("ghost");

            filter.doFilter(req, res, new MockFilterChain());
        }

        assertEquals(401, res.getStatus());
        assertTrue(res.getContentAsString().contains("用户不存在或已禁用"));
        verifyNoInteractions(flowApiService);
    }

    // ==================== 方法 / 契约 ====================

    @Test
    void methodMismatch_returns405() throws Exception {
        when(flowApiCacheManager.getExactMatch("POST", "/demo/hello")).thenReturn(replaceApi());

        MockHttpServletRequest req = new MockHttpServletRequest("POST", "/demo/hello");
        MockHttpServletResponse res = new MockHttpServletResponse();

        try (MockedStatic<JwtTokenUtil> jwt = authedJwt()) {
            filter.doFilter(req, res, new MockFilterChain());
        }

        assertEquals(405, res.getStatus());
        assertTrue(res.getContentAsString().contains("不支持 POST"));
        verifyNoInteractions(flowApiService);
    }

    @Test
    void contractViolation_returns400AndMetersFail() throws Exception {
        FlowApiDO api = replaceApi();
        api.setContract("{\"query\":{\"type\":\"object\"}}");
        when(flowApiCacheManager.getExactMatch("GET", "/demo/hello")).thenReturn(api);
        when(contractParamTypeConverter.convertSection(anyString(), anyString(), any()))
                .thenReturn(new LinkedHashMap<>());
        doThrow(new SchemaValidationException("query.age: 必须为整数"))
                .when(schemaValidatorService).validateFromContract(anyString(), any(), any(), any(), any());

        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/demo/hello");
        req.addParameter("age", "abc");
        MockHttpServletResponse res = new MockHttpServletResponse();

        try (MockedStatic<JwtTokenUtil> jwt = authedJwt()) {
            filter.doFilter(req, res, new MockFilterChain());
        }

        assertEquals(400, res.getStatus());
        assertTrue(res.getContentAsString().contains("必须为整数"));
        verifyNoInteractions(flowApiService);
        verify(assetMetricsRecorder).record(eq(MetricsAssetType.API), eq("api1"),
                eq(MetricsOutcome.FAIL), anyLong(), anyString());
    }

    // ==================== 缓存 / 成功 / 超时 ====================

    @Test
    void responseCacheHit_returns200WithoutExecution() throws Exception {
        FlowApiDO api = replaceApi();
        ApiCacheConfig cacheConfig = mock(ApiCacheConfig.class);
        when(flowApiCacheManager.getExactMatch("GET", "/demo/hello")).thenReturn(api);
        when(apiResponseCacheService.parseConfig(any())).thenReturn(cacheConfig);
        when(apiResponseCacheService.isEnabled(cacheConfig)).thenReturn(true);
        when(apiResponseCacheService.buildCacheKey(eq("api1"), eq(cacheConfig),
                any(), any(), any(), any(), any())).thenReturn("ck");
        when(apiResponseCacheService.get("ck")).thenReturn("{\"cached\":true}");

        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/demo/hello");
        MockHttpServletResponse res = new MockHttpServletResponse();

        try (MockedStatic<JwtTokenUtil> jwt = authedJwt()) {
            filter.doFilter(req, res, new MockFilterChain());
        }

        assertEquals(200, res.getStatus());
        assertEquals("HIT", res.getHeader("ss-flow-cache"));
        assertTrue(res.getContentAsString().contains("cached"));
        verifyNoInteractions(flowApiService);
    }

    @Test
    void success_executesAndWritesJson() throws Exception {
        FlowApiDO api = replaceApi();
        when(flowApiCacheManager.getExactMatch("GET", "/demo/hello")).thenReturn(api);
        stubExecuteSuccess();
        when(flowApiService.executeApi(eq(api), anyMap(), any(), any()))
                .thenReturn(Map.of("hello", "world"));

        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/demo/hello");
        MockHttpServletResponse res = new MockHttpServletResponse();

        try (MockedStatic<JwtTokenUtil> jwt = authedJwt()) {
            filter.doFilter(req, res, new MockFilterChain());
        }

        assertEquals(200, res.getStatus());
        assertEquals("yes", res.getHeader("ss-flow"));
        assertTrue(res.getContentAsString().contains("world"));
        verify(flowApiService).executeApi(eq(api), anyMap(), any(), any());
    }

    @Test
    void hardTimeout_returns504() throws Exception {
        filter = buildFilter(ingressSecurityResolver);
        FlowApiDO api = replaceApi();
        when(flowApiCacheManager.getExactMatch("GET", "/demo/hello")).thenReturn(api);
        when(apiResponseCacheService.parseConfig(any())).thenReturn(null);
        when(apiResponseCacheService.isEnabled(any())).thenReturn(false);
        // guard 未装配 → 不走 ingress 分支，但执行侧仍会读取超时配置（100ms 硬超时）
        when(ingressSecurityResolver.resolve(api))
                .thenReturn(EffectiveSecurity.trustHost(100));
        when(flowApiService.executeApi(eq(api), anyMap(), any(), any())).thenAnswer(inv -> {
            Thread.sleep(2_000);
            return Map.of("late", true);
        });

        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/demo/hello");
        MockHttpServletResponse res = new MockHttpServletResponse();

        try (MockedStatic<JwtTokenUtil> jwt = authedJwt()) {
            filter.doFilter(req, res, new MockFilterChain());
        }

        assertEquals(504, res.getStatus());
        assertTrue(res.getContentAsString().contains("接口执行超时"));
    }
}
