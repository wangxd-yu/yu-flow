package org.yu.flow.module.api.controller;

import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.yu.flow.exception.YuFlowExceptionHandler;
import org.yu.flow.module.api.domain.FlowApiDO;
import org.yu.flow.module.api.repository.FlowApiRepository;
import org.yu.flow.module.api.support.OpenApiSpecBuilder;

import java.util.List;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * OpenApiYamlController 独立 Web 接口测试（Phase 3.3）。
 */
@DisplayName("Phase 3.3 OpenAPI 控制器接口测试")
class OpenApiYamlControllerTest {

    private final FlowApiRepository flowApiRepository = mock(FlowApiRepository.class);
    private final OpenApiSpecBuilder specBuilder = new OpenApiSpecBuilder();

    private final OpenApiYamlController controller = createController();

    private OpenApiYamlController createController() {
        OpenApiYamlController c = new OpenApiYamlController();
        try {
            var repoField = OpenApiYamlController.class.getDeclaredField("flowApiRepository");
            repoField.setAccessible(true);
            repoField.set(c, flowApiRepository);

            var builderField = OpenApiYamlController.class.getDeclaredField("specBuilder");
            builderField.setAccessible(true);
            builderField.set(c, specBuilder);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return c;
    }

    private final MockMvc mockMvc = MockMvcBuilders
            .standaloneSetup(controller)
            .setControllerAdvice(new YuFlowExceptionHandler())
            .build();

    @Test
    @DisplayName("GET /flow-api/open-api/spec → 返回 200 + ok=true + OpenAPI JSON 规范")
    void getSpecJson_returns200AndJsonSpec() throws Exception {
        FlowApiDO api = FlowApiDO.builder()
                .id("api-001")
                .name("测试接口")
                .url("/api/test")
                .method("GET")
                .publishStatus(1)
                .build();
        when(flowApiRepository.findByPublishStatus(1)).thenReturn(List.of(api));

        mockMvc.perform(get("/flow-api/open-api/spec"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ok").value(true))
                .andExpect(jsonPath("$.data.openapi").value("3.0.3"))
                .andExpect(jsonPath("$.data.paths['/api/test']").exists());
    }

    @Test
    @DisplayName("GET /flow-api/open-api/spec.yaml → 返回 200 + text/yaml 格式内容")
    void getSpecYaml_returns200AndYamlContent() throws Exception {
        FlowApiDO api = FlowApiDO.builder()
                .id("api-002")
                .name("订单接口")
                .url("/api/orders")
                .method("POST")
                .publishStatus(1)
                .build();
        when(flowApiRepository.findByPublishStatus(1)).thenReturn(List.of(api));

        mockMvc.perform(get("/flow-api/open-api/spec.yaml"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "text/yaml;charset=UTF-8"))
                .andExpect(header().string("Content-Disposition", "inline; filename=\"yu-flow-api-spec.yaml\""))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("openapi: \"3.0.3\"")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("/api/orders:")));
    }
}
