package org.yu.flow.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
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
import org.yu.flow.module.api.cache.ApiResponseCacheService;
import org.yu.flow.module.api.security.IngressSecurityResolver;
import org.yu.flow.module.metrics.AssetMetricsRecorder;
import org.yu.flow.module.open.auth.HostAuthenticationProbe;
import org.yu.flow.module.open.auth.OpenAuthService;
import org.yu.flow.module.rbac.dto.AuthMeDTO;
import org.yu.flow.module.rbac.service.RbacService;

import jakarta.servlet.http.HttpServletRequest;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * 管理端双层鉴权：/flow-api/** 在 JWT 通过后再校验 HostAuthenticationProbe。
 */
@ExtendWith(MockitoExtension.class)
class FlowApiGatewayManagementHostAuthTest {

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

    private YuFlowProperties props;
    private FlowApiGatewayFilter filter;

    @BeforeEach
    void setUp() {
        props = new YuFlowProperties();
        props.setEnabled(true);
        props.getOpen().setEnabled(false);
        props.getSecurity().setManagementRequireHostAuth(true);
        filter = new FlowApiGatewayFilter(
                props, flowApiService, flowApiCacheManager, schemaValidatorService,
                contractParamTypeConverter, responseStrategyResolver, responseTransformer,
                apiResponseCacheService, openAuthService, assetMetricsRecorder, hostAuthenticationProbe,
                null, null, null, null, rbacService);
    }

    private MockedStatic<JwtTokenUtil> authedJwt() {
        MockedStatic<JwtTokenUtil> jwt = mockStatic(JwtTokenUtil.class);
        jwt.when(() -> JwtTokenUtil.resolveToken(any(HttpServletRequest.class))).thenReturn("tok");
        jwt.when(() -> JwtTokenUtil.getUsername("tok")).thenReturn("admin");
        when(rbacService.buildMe("admin")).thenReturn(mock(AuthMeDTO.class));
        return jwt;
    }

    @Test
    @DisplayName("JWT 通过但宿主 Probe 失败 → 401 FLOW_HOST_AUTH_REQUIRED")
    void managementApi_jwtOk_hostProbeRejects() throws Exception {
        when(hostAuthenticationProbe.isAuthenticated(any())).thenReturn(false);

        try (MockedStatic<JwtTokenUtil> ignored = authedJwt()) {
            MockHttpServletRequest req = new MockHttpServletRequest("GET", "/flow-api/auth/me");
            MockHttpServletResponse res = new MockHttpServletResponse();
            filter.doFilter(req, res, new MockFilterChain());
            assertEquals(401, res.getStatus());
            assertTrue(res.getContentAsString().contains("FLOW_HOST_AUTH_REQUIRED"));
        }
    }

    @Test
    @DisplayName("JWT + 宿主 Probe 均通过 → 放行到后续链")
    void managementApi_jwtAndHostProbeOk() throws Exception {
        when(hostAuthenticationProbe.isAuthenticated(any())).thenReturn(true);
        when(flowApiCacheManager.getExactMatch(anyString(), anyString())).thenReturn(null);
        when(flowApiCacheManager.getPatternMatch(anyString(), anyString(), any())).thenReturn(null);

        try (MockedStatic<JwtTokenUtil> ignored = authedJwt()) {
            MockHttpServletRequest req = new MockHttpServletRequest("GET", "/flow-api/auth/me");
            MockHttpServletResponse res = new MockHttpServletResponse();
            MockFilterChain chain = new MockFilterChain();
            filter.doFilter(req, res, chain);
            assertEquals(200, res.getStatus());
            assertNotNull(chain.getRequest());
        }
    }

    @Test
    @DisplayName("关闭 management-require-host-auth 时不调用 Probe")
    void managementApi_hostAuthDisabled_skipsProbe() throws Exception {
        props.getSecurity().setManagementRequireHostAuth(false);
        when(flowApiCacheManager.getExactMatch(anyString(), anyString())).thenReturn(null);
        when(flowApiCacheManager.getPatternMatch(anyString(), anyString(), any())).thenReturn(null);

        try (MockedStatic<JwtTokenUtil> ignored = authedJwt()) {
            MockHttpServletRequest req = new MockHttpServletRequest("GET", "/flow-api/auth/me");
            MockHttpServletResponse res = new MockHttpServletResponse();
            filter.doFilter(req, res, new MockFilterChain());
            assertEquals(200, res.getStatus());
            verify(hostAuthenticationProbe, never()).isAuthenticated(any());
        }
    }

    @Test
    @DisplayName("/flow-api/oss/** 不强制 Flow 管理 JWT，交由 MVC 内访问规则 / RequirePerm")
    void ossPaths_skipManagementJwtAndProbe() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest("POST", "/flow-api/oss/upload");
        MockHttpServletResponse res = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();
        filter.doFilter(req, res, chain);
        assertEquals(200, res.getStatus());
        assertNotNull(chain.getRequest());
        verify(hostAuthenticationProbe, never()).isAuthenticated(any());
        verify(flowApiCacheManager, never()).getExactMatch(anyString(), anyString());
    }

    @Test
    @DisplayName("OSS 管理 CRUD 同样跳过网关 JWT（由 @RequirePerm 把关）")
    void ossConnections_skipManagementJwt() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/flow-api/oss/connections");
        MockHttpServletResponse res = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();
        filter.doFilter(req, res, chain);
        assertEquals(200, res.getStatus());
        assertNotNull(chain.getRequest());
        verify(hostAuthenticationProbe, never()).isAuthenticated(any());
    }

    @Test
    @DisplayName("flow-ui 静态 CSS 不进动态路由（含 context-path 未剥离）")
    void flowUiCss_skipsDynamicRouting() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/ssp-flow/flow-ui/umi.25396eee.css");
        MockHttpServletResponse res = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();
        filter.doFilter(req, res, chain);
        assertEquals(200, res.getStatus());
        assertNotNull(chain.getRequest());
        verify(flowApiCacheManager, never()).getExactMatch(anyString(), anyString());
        verify(flowApiCacheManager, never()).getPatternMatch(anyString(), anyString(), any());
    }
}
