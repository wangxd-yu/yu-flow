package org.yu.flow.module.api.controller;

import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.yu.flow.annotation.YuFlowApi;
import org.yu.flow.dto.R;
import org.yu.flow.module.api.domain.FlowApiDO;
import org.yu.flow.module.api.repository.FlowApiRepository;
import org.yu.flow.module.api.support.OpenApiSpecBuilder;
import org.yu.flow.module.rbac.support.RequirePerm;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * OpenAPI 3.0 规范导出控制器（Phase 3.3）。
 *
 * <p>提供两个端点：</p>
 * <ul>
 *   <li>{@code GET /flow-api/open-api/spec} — 返回 JSON 格式规范</li>
 *   <li>{@code GET /flow-api/open-api/spec.yaml} — 返回 YAML 格式规范（直接下载）</li>
 * </ul>
 *
 * <p>权限：需要 {@code API:VIEW} 权限（已登录管理员）。</p>
 */
@Slf4j
@YuFlowApi
@RestController
@RequestMapping("flow-api/open-api")
public class OpenApiYamlController {

    private static final YAMLMapper YAML_MAPPER = new YAMLMapper();

    @Resource
    private FlowApiRepository flowApiRepository;

    @Autowired
    private OpenApiSpecBuilder specBuilder;

    /**
     * 返回 OpenAPI 3.0 JSON 规范（供前端 Swagger UI 渲染）。
     *
     * @param title   文档标题（可选，默认 "Yu Flow API"）
     * @param version 文档版本（可选，默认 "1.0.0"）
     */
    @GetMapping("/spec")
    @RequirePerm("API:VIEW")
    public R<ObjectNode> getSpecJson(
            @RequestParam(defaultValue = "Yu Flow API") String title,
            @RequestParam(defaultValue = "1.0.0") String version,
            HttpServletRequest request) {

        List<FlowApiDO> apis = flowApiRepository.findByPublishStatus(1);
        String serverUrl = resolveServerUrl(request);

        ObjectNode spec = specBuilder.build(apis, serverUrl, title, version);
        log.info("[OpenApiYamlController] 生成 OpenAPI JSON 规范，已发布 API 数量={}", apis.size());
        return R.ok(spec);
    }

    /**
     * 返回 OpenAPI 3.0 YAML 规范（直接以 text/yaml 输出，可供 Swagger/Postman 导入）。
     */
    @GetMapping(value = "/spec.yaml", produces = "text/yaml;charset=UTF-8")
    @RequirePerm("API:VIEW")
    public void getSpecYaml(
            @RequestParam(defaultValue = "Yu Flow API") String title,
            @RequestParam(defaultValue = "1.0.0") String version,
            HttpServletRequest request,
            HttpServletResponse response) throws IOException {

        List<FlowApiDO> apis = flowApiRepository.findByPublishStatus(1);
        String serverUrl = resolveServerUrl(request);

        ObjectNode spec = specBuilder.build(apis, serverUrl, title, version);
        String yaml = YAML_MAPPER.writeValueAsString(spec);

        response.setContentType("text/yaml;charset=UTF-8");
        response.setHeader("Content-Disposition", "inline; filename=\"yu-flow-api-spec.yaml\"");
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write(yaml);

        log.info("[OpenApiYamlController] 生成 OpenAPI YAML 规范，已发布 API 数量={}", apis.size());
    }

    /**
     * 从请求中推断服务器基础 URL。
     */
    private String resolveServerUrl(HttpServletRequest request) {
        String scheme = request.getScheme();
        String host = request.getServerName();
        int port = request.getServerPort();
        String contextPath = request.getContextPath();

        if (("http".equals(scheme) && port == 80) || ("https".equals(scheme) && port == 443)) {
            return scheme + "://" + host + contextPath;
        }
        return scheme + "://" + host + ":" + port + contextPath;
    }
}
