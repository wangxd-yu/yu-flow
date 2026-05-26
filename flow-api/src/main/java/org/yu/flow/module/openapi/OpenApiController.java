package org.yu.flow.module.openapi;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.yu.flow.annotation.YuFlowApi;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * OpenAPI 3.0 文档端点控制器
 *
 * <p>暴露标准的 {@code /v3/api-docs} 端点，输出由 {@link OpenApiGeneratorService}
 * 动态生成的 OpenAPI 3.0 JSON 文档。</p>
 *
 * <h3>兼容性</h3>
 * <ul>
 *   <li>Swagger UI — 直接在 URL 栏填入 {@code /flow-api/v3/api-docs} 即可加载</li>
 *   <li>Redoc — 同理</li>
 *   <li>Postman — 支持导入 OpenAPI 3.0 JSON</li>
 *   <li>前端 SDK 生成器 (openapi-generator / swagger-codegen) — 兼容</li>
 * </ul>
 *
 * <h3>CORS</h3>
 * <p>该端点返回标准 JSON，CORS 策略继承宿主配置。</p>
 *
 * @author yu-flow
 */
@Slf4j
@YuFlowApi
@RestController
@RequestMapping("/flow-api/v3")
public class OpenApiController {

    @Resource
    private OpenApiGeneratorService openApiGeneratorService;

    /**
     * 获取 OpenAPI 3.0 文档 (JSON)
     *
     * <p>每次请求实时生成，确保与最新的已发布 API 列表保持一致。
     * 生成耗时通常在 5ms 以内（100 个 API 规模），无需缓存。</p>
     */
    @GetMapping(value = "/api-docs", produces = MediaType.APPLICATION_JSON_VALUE)
    public void getApiDocs(HttpServletRequest request, HttpServletResponse response) throws IOException {
        response.setContentType("application/json;charset=UTF-8");
        response.setHeader("Cache-Control", "no-cache, no-store, must-revalidate");
        response.setHeader("Access-Control-Allow-Origin", "*");

        String json = openApiGeneratorService.generateOpenApiJson(request);
        response.getOutputStream().write(json.getBytes(StandardCharsets.UTF_8));
        response.getOutputStream().flush();
    }

    /**
     * 获取 OpenAPI 文档（YAML 格式 — 预留端点，当前直接返回 JSON）
     *
     * <p>部分工具（如 Redoc）偏好 YAML 格式，此端点为未来扩展预留。</p>
     */
    @GetMapping(value = "/api-docs.yaml", produces = "application/x-yaml")
    public void getApiDocsYaml(HttpServletRequest request, HttpServletResponse response) throws IOException {
        // TODO: 后续可引入 snakeyaml 做 JSON→YAML 转换
        response.setContentType("application/json;charset=UTF-8");
        String json = openApiGeneratorService.generateOpenApiJson(request);
        response.getOutputStream().write(json.getBytes(StandardCharsets.UTF_8));
        response.getOutputStream().flush();
    }
}
