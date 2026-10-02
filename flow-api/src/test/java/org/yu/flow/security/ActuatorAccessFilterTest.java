package org.yu.flow.security;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.yu.flow.config.YuFlowProperties;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.junit.jupiter.api.Assertions.*;

class ActuatorAccessFilterTest {

    private static ActuatorAccessFilter filter(String token) {
        YuFlowProperties props = new YuFlowProperties();
        props.getObservability().setMetricsToken(token);
        return new ActuatorAccessFilter(props, "/actuator");
    }

    private static MockHttpServletResponse call(ActuatorAccessFilter filter, String path, String auth) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/flow" + path);
        request.setContextPath("/flow");
        if (auth != null) {
            request.addHeader("Authorization", auth);
        }
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, new MockFilterChain());
        return response;
    }

    @Test
    void healthIsAnonymousOtherEndpointsNeedToken() throws Exception {
        ActuatorAccessFilter f = filter("s3cret");
        assertEquals(200, call(f, "/actuator/health", null).getStatus());
        assertEquals(200, call(f, "/actuator/health/liveness", null).getStatus());
        assertEquals(401, call(f, "/actuator/prometheus", null).getStatus());
        assertEquals(401, call(f, "/actuator/prometheus", "Bearer wrong").getStatus());
        assertEquals(401, call(f, "/actuator", null).getStatus());
        assertEquals(200, call(f, "/actuator/prometheus", "bearer s3cret").getStatus());
        // 不是 actuator 路径，不做任何处理
        assertEquals(200, call(f, "/actuatorx/prometheus", null).getStatus());
        assertEquals(200, call(f, "/flow-api/apis", null).getStatus());
    }

    @Test
    void endpointsHiddenWithoutConfiguredToken() throws Exception {
        ActuatorAccessFilter f = filter(" ");
        assertEquals(404, call(f, "/actuator/prometheus", "Bearer ").getStatus());
        assertEquals(200, call(f, "/actuator/health", null).getStatus());
    }

    /** 真起内嵌 Tomcat，验证暴露配置与过滤器在实际请求链上生效、Prometheus 指标确实输出 */
    @Nested
    @SpringBootTest(classes = EmbeddedServer.App.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
            properties = {
                    "server.servlet.context-path=/flow",
                    "management.endpoints.web.exposure.include=health,info,prometheus",
                    "management.endpoint.health.show-details=never",
                    "management.endpoint.health.probes.enabled=true",
                    "yu.flow.observability.metrics-token=t0ken"
            })
    class EmbeddedServer {

        @Configuration(proxyBeanMethods = false)
        @EnableConfigurationProperties(YuFlowProperties.class)
        @Import(ActuatorAccessFilter.class)
        @ImportAutoConfiguration({
                org.springframework.boot.autoconfigure.context.PropertyPlaceholderAutoConfiguration.class,
                org.springframework.boot.autoconfigure.availability.ApplicationAvailabilityAutoConfiguration.class,
                org.springframework.boot.jackson.autoconfigure.JacksonAutoConfiguration.class,
                org.springframework.boot.tomcat.autoconfigure.servlet.TomcatServletWebServerAutoConfiguration.class,
                org.springframework.boot.webmvc.autoconfigure.DispatcherServletAutoConfiguration.class,
                org.springframework.boot.webmvc.autoconfigure.WebMvcAutoConfiguration.class,
                org.springframework.boot.http.converter.autoconfigure.HttpMessageConvertersAutoConfiguration.class,
                org.springframework.boot.actuate.autoconfigure.endpoint.EndpointAutoConfiguration.class,
                org.springframework.boot.actuate.autoconfigure.endpoint.jackson.JacksonEndpointAutoConfiguration.class,
                org.springframework.boot.actuate.autoconfigure.endpoint.web.WebEndpointAutoConfiguration.class,
                org.springframework.boot.actuate.autoconfigure.web.server.ManagementContextAutoConfiguration.class,
                org.springframework.boot.servlet.autoconfigure.actuate.web.ServletManagementContextAutoConfiguration.class,
                org.springframework.boot.actuate.autoconfigure.info.InfoEndpointAutoConfiguration.class,
                org.springframework.boot.health.autoconfigure.contributor.HealthContributorAutoConfiguration.class,
                org.springframework.boot.health.autoconfigure.registry.HealthContributorRegistryAutoConfiguration.class,
                org.springframework.boot.health.autoconfigure.actuate.endpoint.HealthEndpointAutoConfiguration.class,
                org.springframework.boot.health.autoconfigure.actuate.endpoint.AvailabilityProbesAutoConfiguration.class,
                org.springframework.boot.health.autoconfigure.application.AvailabilityHealthContributorAutoConfiguration.class,
                org.springframework.boot.webmvc.autoconfigure.actuate.endpoint.web.WebMvcHealthEndpointExtensionAutoConfiguration.class,
                org.springframework.boot.micrometer.metrics.autoconfigure.MetricsAutoConfiguration.class,
                org.springframework.boot.micrometer.metrics.autoconfigure.export.prometheus.PrometheusMetricsExportAutoConfiguration.class,
                org.springframework.boot.micrometer.metrics.autoconfigure.jvm.JvmMetricsAutoConfiguration.class
        })
        static class App {
        }

        @LocalServerPort
        int port;

        private HttpResponse<String> get(String path, String token) throws Exception {
            HttpRequest.Builder b = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/flow" + path));
            if (token != null) {
                b.header("Authorization", "Bearer " + token);
            }
            return HttpClient.newHttpClient().send(b.build(), HttpResponse.BodyHandlers.ofString());
        }

        @Test
        void healthPublicWithoutDetailsPrometheusBehindToken() throws Exception {
            HttpResponse<String> health = get("/actuator/health", null);
            assertEquals(200, health.statusCode());
            assertTrue(health.body().contains("\"status\":\"UP\""), health.body());
            assertFalse(health.body().contains("components"), "health 不应返回明细: " + health.body());
            assertEquals(200, get("/actuator/health/liveness", null).statusCode());

            assertEquals(401, get("/actuator/prometheus", null).statusCode());
            HttpResponse<String> prom = get("/actuator/prometheus", "t0ken");
            assertEquals(200, prom.statusCode());
            assertTrue(prom.body().contains("jvm_memory_used_bytes"), "缺少 JVM 指标");

            // 未暴露的端点本来就不存在
            assertEquals(404, get("/actuator/env", "t0ken").statusCode());
        }
    }
}
