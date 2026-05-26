package org.yu.flow.module.openapi;

import org.springframework.http.MediaType;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.yu.flow.annotation.YuFlowApi;

import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.PrintWriter;

/**
 * 内置 Swagger UI 页面
 *
 * <p>基于 Swagger UI CDN 构建的轻量级内嵌页面，无需引入额外依赖。
 * 访问 {@code /flow-api/swagger-ui.html} 即可在线浏览所有动态 API 的文档。</p>
 *
 * @author yu-flow
 */
@YuFlowApi
@Controller
@RequestMapping("/flow-api")
public class SwaggerUiController {

    @GetMapping(value = "/swagger-ui.html", produces = MediaType.TEXT_HTML_VALUE)
    public void swaggerUi(HttpServletResponse response) throws IOException {
        response.setContentType("text/html;charset=UTF-8");
        response.setHeader("Cache-Control", "no-cache");
        PrintWriter writer = response.getWriter();
        writer.write(SWAGGER_UI_HTML);
        writer.flush();
    }

    private static final String SWAGGER_UI_HTML = "<!DOCTYPE html>\n" +
            "<html lang=\"zh-CN\">\n" +
            "<head>\n" +
            "  <meta charset=\"UTF-8\">\n" +
            "  <meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">\n" +
            "  <title>Yu Flow · API 文档</title>\n" +
            "  <link rel=\"stylesheet\" href=\"https://cdn.jsdelivr.net/npm/swagger-ui-dist@5/swagger-ui.css\">\n" +
            "  <style>\n" +
            "    body { margin: 0; background: #fafafa; }\n" +
            "    .swagger-ui .topbar { display: none; }\n" +
            "    .yu-flow-header {\n" +
            "      background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);\n" +
            "      color: #fff;\n" +
            "      padding: 20px 32px;\n" +
            "      font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif;\n" +
            "    }\n" +
            "    .yu-flow-header h1 {\n" +
            "      margin: 0 0 4px 0;\n" +
            "      font-size: 22px;\n" +
            "      font-weight: 600;\n" +
            "      letter-spacing: 0.5px;\n" +
            "    }\n" +
            "    .yu-flow-header p {\n" +
            "      margin: 0;\n" +
            "      font-size: 13px;\n" +
            "      opacity: 0.85;\n" +
            "    }\n" +
            "  </style>\n" +
            "</head>\n" +
            "<body>\n" +
            "  <div class=\"yu-flow-header\">\n" +
            "    <h1>Yu Flow · API 文档</h1>\n" +
            "    <p>由动态 API 引擎自动生成的 OpenAPI 3.0 契约文档，实时反映所有已发布接口。</p>\n" +
            "  </div>\n" +
            "  <div id=\"swagger-ui\"></div>\n" +
            "  <script src=\"https://cdn.jsdelivr.net/npm/swagger-ui-dist@5/swagger-ui-bundle.js\"></script>\n" +
            "  <script>\n" +
            "    SwaggerUIBundle({\n" +
            "      url: 'v3/api-docs',\n" +
            "      dom_id: '#swagger-ui',\n" +
            "      deepLinking: true,\n" +
            "      presets: [\n" +
            "        SwaggerUIBundle.presets.apis,\n" +
            "        SwaggerUIBundle.SwaggerUIStandalonePreset\n" +
            "      ],\n" +
            "      layout: 'BaseLayout',\n" +
            "      defaultModelsExpandDepth: 2,\n" +
            "      defaultModelExpandDepth: 2,\n" +
            "      docExpansion: 'list',\n" +
            "      filter: true,\n" +
            "      showExtensions: true,\n" +
            "      showCommonExtensions: true,\n" +
            "      tryItOutEnabled: false\n" +
            "    });\n" +
            "  </script>\n" +
            "</body>\n" +
            "</html>";
}
