package org.yu.flow.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.yu.flow.auto.service.FlowApiExecutionService;
import org.yu.flow.config.response.ResponseStrategyResolver;
import org.yu.flow.config.response.ResponseTransformer;
import org.yu.flow.config.response.ResponseWrapperContext;
import org.yu.flow.module.api.cache.ApiResponseCacheService;
import org.yu.flow.module.api.domain.FlowApiDO;
import org.yu.flow.module.api.support.ApiInterceptMode;
import org.yu.flow.module.metrics.AssetMetricsRecorder;
import org.yu.flow.module.metrics.MetricsAssetType;
import org.yu.flow.module.metrics.MetricsOutcome;
import org.yu.flow.module.open.auth.HostAuthenticationProbe;
import org.yu.flow.module.open.auth.OpenAuthContext;
import org.yu.flow.module.open.auth.OpenAuthException;
import org.yu.flow.module.open.auth.OpenAuthService;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 开放入口 / 直连 path / 宿主 Probe 的网关 Filter 级集成测（不启 Spring / Redis）。
 */
@ExtendWith(MockitoExtension.class)
class FlowApiGatewayOpenFilterTest {

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

    private YuFlowProperties props;
    private FlowApiGatewayFilter filter;

    @BeforeEach
    void setUp() {
        props = new YuFlowProperties();
        props.setEnabled(true);
        props.getOpen().setEnabled(true);
        props.getOpen().setEntryPrefix("/flow-api/open");
        props.getOpen().setAllowDirectPath(false);
        props.getOpen().setRequireHostAuth(false);
        props.getOpen().setIncludeBodyHash(false);

        filter = new FlowApiGatewayFilter(
                props, flowApiService, flowApiCacheManager, schemaValidatorService,
                contractParamTypeConverter, responseStrategyResolver, responseTransformer,
                apiResponseCacheService, openAuthService, assetMetricsRecorder, hostAuthenticationProbe,
                null, null, null, null, null);
    }

    @Test
    void openEntry_disabled_returns404() throws Exception {
        props.getOpen().setEnabled(false);
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/flow-api/open/demo/hello");
        MockHttpServletResponse res = new MockHttpServletResponse();

        filter.doFilter(req, res, new MockFilterChain());

        assertEquals(404, res.getStatus());
        assertTrue(res.getContentAsString().contains("开放入口未启用"));
        verifyNoInteractions(openAuthService);
    }

    @Test
    void openEntry_authMissing_returns401() throws Exception {
        when(openAuthService.authenticate(any(), eq("/demo/hello"), eq("GET")))
                .thenThrow(OpenAuthException.missing());
        when(openAuthService.peekByAppKey("yf_x")).thenReturn(
                OpenAuthContext.builder().platformId("p1").appKey("yf_x").build());

        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/flow-api/open/demo/hello");
        req.addHeader(OpenAuthService.HDR_APP_KEY, "yf_x");
        MockHttpServletResponse res = new MockHttpServletResponse();

        filter.doFilter(req, res, new MockFilterChain());

        assertEquals(401, res.getStatus());
        assertTrue(res.getContentAsString().contains("OPEN_AUTH_MISSING"));
        verify(assetMetricsRecorder).record(eq(MetricsAssetType.PLATFORM), eq("p1"),
                eq(MetricsOutcome.AUTH_FAIL), anyLong());
    }

    @Test
    void openEntry_notGranted_returns403() throws Exception {
        OpenAuthContext ctx = OpenAuthContext.builder()
                .platformId("p1").appKey("yf_x").build();
        when(openAuthService.authenticate(any(), eq("/demo/hello"), eq("GET"))).thenReturn(ctx);
        when(flowApiCacheManager.getExactMatch("GET", "/demo/hello")).thenReturn(sampleApi());
        doThrow(OpenAuthException.denied("未授权访问该接口"))
                .when(openAuthService).assertApiGranted(eq(ctx), eq("api1"), eq("GET"));

        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/flow-api/open/demo/hello");
        MockHttpServletResponse res = new MockHttpServletResponse();

        filter.doFilter(req, res, new MockFilterChain());

        assertEquals(403, res.getStatus());
        assertTrue(res.getContentAsString().contains("OPEN_AUTH_DENIED"));
    }

    @Test
    void openEntry_methodDenied_returns403() throws Exception {
        OpenAuthContext ctx = OpenAuthContext.builder()
                .platformId("p1").appKey("yf_x").build();
        when(openAuthService.authenticate(any(), eq("/demo/hello"), eq("GET"))).thenReturn(ctx);
        when(flowApiCacheManager.getExactMatch("GET", "/demo/hello")).thenReturn(sampleApi());
        doThrow(OpenAuthException.methodDenied("未授权使用 GET"))
                .when(openAuthService).assertApiGranted(eq(ctx), eq("api1"), eq("GET"));

        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/flow-api/open/demo/hello");
        MockHttpServletResponse res = new MockHttpServletResponse();

        filter.doFilter(req, res, new MockFilterChain());

        assertEquals(403, res.getStatus());
        assertTrue(res.getContentAsString().contains("OPEN_AUTH_METHOD_DENIED"));
    }

    @Test
    void openEntry_success_executesAndMetersPlatform() throws Exception {
        OpenAuthContext ctx = OpenAuthContext.builder()
                .platformId("p1").appKey("yf_x").build();
        FlowApiDO api = sampleApi();
        when(openAuthService.authenticate(any(), eq("/demo/hello"), eq("GET"))).thenReturn(ctx);
        when(flowApiCacheManager.getExactMatch("GET", "/demo/hello")).thenReturn(api);
        doNothing().when(openAuthService).assertApiGranted(eq(ctx), eq("api1"), eq("GET"));
        stubExecuteSuccess();
        when(flowApiService.executeApi(eq(api), anyMap(), any(), any())).thenReturn(java.util.Map.of("ok", true));

        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/flow-api/open/demo/hello");
        MockHttpServletResponse res = new MockHttpServletResponse();

        filter.doFilter(req, res, new MockFilterChain());

        assertEquals(200, res.getStatus());
        assertTrue(res.getContentAsString().contains("ok"));
        verify(flowApiService).executeApi(eq(api), anyMap(), any(), any());
        verify(assetMetricsRecorder).record(eq(MetricsAssetType.PLATFORM), eq("p1"),
                eq(MetricsOutcome.SUCCESS), anyLong());
    }

    @Test
    void openEntry_wrap_rewritesPathAndForwardsHost() throws Exception {
        OpenAuthContext ctx = OpenAuthContext.builder()
                .platformId("p1").appKey("yf_x").build();
        FlowApiDO api = wrapApi("api-wrap-open", "/yu-demo/host-ping", "GET");
        when(openAuthService.authenticate(any(), eq("/yu-demo/host-ping"), eq("GET"))).thenReturn(ctx);
        when(flowApiCacheManager.getExactMatch("GET", "/yu-demo/host-ping")).thenReturn(api);
        doNothing().when(openAuthService).assertApiGranted(eq(ctx), eq("api-wrap-open"), eq("GET"));

        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/flow-api/open/yu-demo/host-ping");
        MockHttpServletResponse res = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain() {
            @Override
            public void doFilter(jakarta.servlet.ServletRequest request, jakarta.servlet.ServletResponse response)
                    throws IOException, ServletException {
                HttpServletRequest http = (HttpServletRequest) request;
                assertEquals("/yu-demo/host-ping", http.getRequestURI());
                assertEquals(Boolean.TRUE, http.getAttribute("yu.flow.host-wrap.forwarded"));
                ((HttpServletResponse) response).setStatus(200);
                response.getWriter().write("{\"pong\":true}");
            }
        };

        filter.doFilter(req, res, chain);

        assertEquals(200, res.getStatus());
        assertTrue(res.getContentAsString().contains("pong"));
        verify(flowApiService, never()).executeApi(any(), any(), any(), any());
        verify(assetMetricsRecorder).record(eq(MetricsAssetType.PLATFORM), eq("p1"),
                eq(MetricsOutcome.SUCCESS), anyLong());
        verify(assetMetricsRecorder).record(eq(MetricsAssetType.API), eq("api-wrap-open"),
                eq(MetricsOutcome.SUCCESS), anyLong(), anyString());
    }

    @Test
    void openEntry_wrap_export_unsupported() throws Exception {
        OpenAuthContext ctx = OpenAuthContext.builder()
                .platformId("p1").appKey("yf_x").build();
        FlowApiDO api = wrapApi("api-wrap-export", "/yu-demo/host-ping", "GET");
        // 验签使用完整 realPath（含 /export），与网关实现约定一致
        when(openAuthService.authenticate(any(), eq("/yu-demo/host-ping/export"), eq("GET"))).thenReturn(ctx);
        when(flowApiCacheManager.getExactMatch("GET", "/yu-demo/host-ping")).thenReturn(api);
        doNothing().when(openAuthService).assertApiGranted(eq(ctx), eq("api-wrap-export"), eq("GET"));

        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/flow-api/open/yu-demo/host-ping/export");
        MockHttpServletResponse res = new MockHttpServletResponse();

        filter.doFilter(req, res, new MockFilterChain());

        assertEquals(400, res.getStatus());
        assertTrue(res.getContentAsString().contains("不支持"));
        verify(flowApiService, never()).executeApi(any(), any(), any(), any());
    }

    @Test
    void directPath_withAppKey_whenEnabled() throws Exception {
        props.getOpen().setAllowDirectPath(true);
        OpenAuthContext ctx = OpenAuthContext.builder()
                .platformId("p1").appKey("yf_x").build();
        FlowApiDO api = sampleApi();
        when(flowApiCacheManager.getExactMatch("GET", "/demo/hello")).thenReturn(api);
        when(openAuthService.authenticate(any(), eq("/demo/hello"), eq("GET"))).thenReturn(ctx);
        doNothing().when(openAuthService).assertApiGranted(eq(ctx), eq("api1"), eq("GET"));
        stubExecuteSuccess();
        when(flowApiService.executeApi(eq(api), anyMap(), any(), any())).thenReturn(java.util.Map.of("v", 1));

        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/demo/hello");
        req.addHeader(OpenAuthService.HDR_APP_KEY, "yf_x");
        MockHttpServletResponse res = new MockHttpServletResponse();

        filter.doFilter(req, res, new MockFilterChain());

        assertEquals(200, res.getStatus());
        verify(openAuthService).authenticate(any(), eq("/demo/hello"), eq("GET"));
        verify(assetMetricsRecorder).record(eq(MetricsAssetType.PLATFORM), eq("p1"),
                eq(MetricsOutcome.SUCCESS), anyLong());
    }

    @Test
    void publishedApi_requireHostAuth_rejectsAnonymous() throws Exception {
        props.getOpen().setRequireHostAuth(true);
        when(flowApiCacheManager.getExactMatch("GET", "/demo/hello")).thenReturn(sampleApi());

        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/demo/hello");
        MockHttpServletResponse res = new MockHttpServletResponse();

        filter.doFilter(req, res, new MockFilterChain());

        // REPLACE 在未启用 ingress 时的安全默认：管理端 JWT 先行，匿名直接 401（宿主鉴权断言在其后）
        assertEquals(401, res.getStatus());
        assertTrue(res.getContentAsString().contains("token 不能为空"));
        verifyNoInteractions(flowApiService);
    }

    private void stubExecuteSuccess() {
        when(apiResponseCacheService.parseConfig(any())).thenReturn(null);
        when(apiResponseCacheService.isEnabled(any())).thenReturn(false);
        when(responseStrategyResolver.resolve(any())).thenReturn(new ResponseWrapperContext());
        when(responseTransformer.transform(any(), any())).thenAnswer(inv -> inv.getArgument(0));
    }

    private static FlowApiDO sampleApi() {
        return FlowApiDO.builder()
                .id("api1")
                .name("demo")
                .url("/demo/hello")
                .method("GET")
                .publishStatus(1)
                .responseType("OBJECT")
                .build();
    }

    private static FlowApiDO wrapApi(String id, String url, String method) {
        FlowApiDO api = new FlowApiDO();
        api.setId(id);
        api.setName("wrap-open");
        api.setUrl(url);
        api.setMethod(method);
        api.setPublishStatus(1);
        api.setServiceType(ApiInterceptMode.SERVICE_TYPE_HOST);
        api.setInterceptMode(ApiInterceptMode.WRAP);
        return api;
    }
}
