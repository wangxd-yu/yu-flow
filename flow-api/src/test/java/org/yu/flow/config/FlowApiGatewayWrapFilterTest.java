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
import org.yu.flow.module.api.cache.ApiResponseCacheService;
import org.yu.flow.module.api.domain.FlowApiDO;
import org.yu.flow.module.api.support.ApiInterceptMode;
import org.yu.flow.module.metrics.AssetMetricsRecorder;
import org.yu.flow.module.metrics.MetricsAssetType;
import org.yu.flow.module.metrics.MetricsOutcome;
import org.yu.flow.module.open.auth.HostAuthenticationProbe;
import org.yu.flow.module.open.auth.OpenAuthService;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * WRAP 同名包裹：ingress 关时不强制管理 JWT；转发宿主并记计量。
 */
@ExtendWith(MockitoExtension.class)
class FlowApiGatewayWrapFilterTest {

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
        props.getIngress().setEnabled(false);
        props.getOpen().setRequireHostAuth(false);

        filter = new FlowApiGatewayFilter(
                props, flowApiService, flowApiCacheManager, schemaValidatorService,
                contractParamTypeConverter, responseStrategyResolver, responseTransformer,
                apiResponseCacheService, openAuthService, assetMetricsRecorder, hostAuthenticationProbe,
                null, null, null, null, null);
    }

    @Test
    void wrap_withoutJwt_forwardsToHostAndRecordsMetrics() throws Exception {
        FlowApiDO api = wrapApi("api-wrap-1", "/biz/orders", "GET");
        when(flowApiCacheManager.getExactMatch("GET", "/biz/orders")).thenReturn(api);

        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/biz/orders");
        MockHttpServletResponse res = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain() {
            @Override
            public void doFilter(jakarta.servlet.ServletRequest request, jakarta.servlet.ServletResponse response)
                    throws IOException, ServletException {
                ((HttpServletResponse) response).setStatus(200);
                response.getWriter().write("{\"ok\":true}");
            }
        };

        filter.doFilter(req, res, chain);

        assertEquals(200, res.getStatus());
        assertTrue(res.getContentAsString().contains("ok"));
        verify(flowApiService, never()).executeApi(any(), any(), any(), any());
        verify(assetMetricsRecorder).record(eq(MetricsAssetType.API), eq("api-wrap-1"),
                eq(MetricsOutcome.SUCCESS), anyLong(), anyString());
    }

    @Test
    void wrap_reentryAttribute_skipsSecondMatch() throws Exception {
        FlowApiDO api = wrapApi("api-wrap-2", "/biz/orders", "GET");
        when(flowApiCacheManager.getExactMatch("GET", "/biz/orders")).thenReturn(api);

        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/biz/orders");
        MockHttpServletResponse res = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain() {
            @Override
            public void doFilter(jakarta.servlet.ServletRequest request, jakarta.servlet.ServletResponse response)
                    throws IOException, ServletException {
                // 模拟链上再次进入网关时已带防重入标记
                assertEquals(Boolean.TRUE,
                        ((HttpServletRequest) request).getAttribute("yu.flow.host-wrap.forwarded"));
                ((HttpServletResponse) response).setStatus(204);
            }
        };

        filter.doFilter(req, res, chain);
        assertEquals(204, res.getStatus());
    }

    private static FlowApiDO wrapApi(String id, String url, String method) {
        FlowApiDO api = new FlowApiDO();
        api.setId(id);
        api.setName("wrap-demo");
        api.setUrl(url);
        api.setMethod(method);
        api.setServiceType(ApiInterceptMode.SERVICE_TYPE_HOST);
        api.setInterceptMode(ApiInterceptMode.WRAP);
        api.setPublishStatus(1);
        api.setLogEnabled(false);
        api.setPublishedSnapshot("{\"url\":\"" + url + "\",\"method\":\"" + method
                + "\",\"serviceType\":\"HOST\",\"interceptMode\":\"WRAP\",\"name\":\"wrap-demo\"}");
        return api;
    }
}
