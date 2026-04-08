package org.yu.flow.ui;

import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Controller;
import org.springframework.util.StreamUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import javax.annotation.PostConstruct;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Flow 前端 SPA 动态渲染控制器（仿 Swagger UI 实现）
 *
 * <h3>设计思路</h3>
 * <p>前端 UmiJS 以 History 路由模式打包后，index.html 中的资源引用为绝对路径：
 * <code>&lt;script src="/flow-ui/umi.xxx.js"&gt;</code>。
 * 当宿主系统配置了 <code>server.servlet.context-path=/flow</code> 时，
 * 浏览器会直接请求 <code>/flow-ui/umi.js</code>，丢失了 <code>/flow</code> 前缀，导致 404。</p>
 *
 * <h3>解决方案</h3>
 * <p>参考 Swagger UI / Knife4j 的经典做法：</p>
 * <ol>
 *   <li>启动时从 classpath 读取 index.html 模板并缓存到内存</li>
 *   <li>每次请求时获取运行时 contextPath，将 HTML 中的 /flow-ui/ 替换为 {contextPath}/flow-ui/</li>
 *   <li>同时注入全局变量 window.__CONTEXT_PATH__ 供前端 JS 使用</li>
 *   <li>以 text/html 直接响应，不走 forward</li>
 * </ol>
 *
 * <h3>性能说明</h3>
 * <p>HTML 模板仅约 400 字节，缓存在内存中。每次请求仅执行一次 String.replace，
 * 开销可忽略不计。</p>
 *
 * yu-flow
 */
@Slf4j
@Controller
public class FlowUiController {

    /** 前端 SPA 路由前缀（不含 contextPath） */
    private static final String UI_PATH_PREFIX = "/flow-ui";

    /** index.html 在 classpath 中的位置 */
    private static final String INDEX_HTML_CLASSPATH = "META-INF/resources/flow-ui/index.html";

    /**
     * 缓存的 index.html 原始内容（启动时加载一次）。
     * <p>如果加载失败则为 null，运行时会降级返回错误提示。</p>
     */
    private String indexHtmlTemplate;

    /**
     * 应用启动时从 classpath 加载 index.html 模板并缓存。
     * <p>此操作仅在 Bean 初始化时执行一次，后续所有请求复用缓存内容。</p>
     */
    @PostConstruct
    public void loadIndexHtml() {
        try {
            ClassPathResource resource = new ClassPathResource(INDEX_HTML_CLASSPATH);
            indexHtmlTemplate = StreamUtils.copyToString(resource.getInputStream(), StandardCharsets.UTF_8);
            log.info("[FlowUiController] 已加载 index.html 模板（{} 字节）", indexHtmlTemplate.length());
        } catch (IOException e) {
            log.warn("[FlowUiController] 加载 index.html 失败，前端 UI 将不可用。path={}", INDEX_HTML_CLASSPATH, e);
            indexHtmlTemplate = null;
        }
    }

    // ==================== SPA History 路由 Fallback ====================
    // 以下所有 @GetMapping 最终都调用 renderIndexHtml() 统一处理

    /**
     * 根路径入口：/flow-ui 和 /flow-ui/ 以及直接访问 /flow-ui/index.html
     */
    @GetMapping({"/flow-ui", "/flow-ui/", "/flow-ui/index.html"})
    public void forwardRoot(HttpServletRequest request, HttpServletResponse response) throws IOException {
        renderIndexHtml(request, response);
    }

    /**
     * 一级子路径 Fallback（排除含点号的静态资源路径）
     */
    @GetMapping("/flow-ui/{path:[^.]*}")
    public void forwardSingleLevel(HttpServletRequest request, HttpServletResponse response) throws IOException {
        renderIndexHtml(request, response);
    }

    /**
     * 多级子路径 Fallback（2~4 层深度，排除含点号的静态资源路径）
     *
     * <p>由于 PathPatternParser 禁止在路径中间使用 **，
     * 因此通过枚举 2~4 级来覆盖所有前端路由深度。</p>
     */
    @GetMapping({
            "/flow-ui/{p1:[^.]*}/{p2:[^.]*}",
            "/flow-ui/{p1:[^.]*}/{p2:[^.]*}/{p3:[^.]*}",
            "/flow-ui/{p1:[^.]*}/{p2:[^.]*}/{p3:[^.]*}/{p4:[^.]*}"
    })
    public void forwardMultiLevel(HttpServletRequest request, HttpServletResponse response) throws IOException {
        renderIndexHtml(request, response);
    }

    // ==================== 健康检查 ====================

    /**
     * 健康检查端点，供运维或负载均衡器探测前端 UI 模块是否已挂载。
     */
    @GetMapping("/flow-ui/api/health")
    @ResponseBody
    public String healthCheck() {
        return "{\"status\":\"UP\",\"module\":\"flow-ui\"}";
    }

    // ==================== 核心渲染逻辑 ====================

    /**
     * 动态渲染 index.html —— 注入运行时 contextPath
     *
     * <h4>替换规则</h4>
     * <pre>
     *   原始 HTML:
     *     &lt;script src="/flow-ui/umi.xxx.js"&gt;
     *
     *   contextPath = "" (无前缀):
     *     &lt;script src="/flow-ui/umi.xxx.js"&gt;   (不变)
     *
     *   contextPath = "/flow":
     *     &lt;script src="/flow/flow-ui/umi.xxx.js"&gt;
     * </pre>
     *
     * <h4>全局变量注入</h4>
     * <p>在 &lt;head&gt; 标签后注入：</p>
     * <pre>
     *   &lt;script&gt;window.__CONTEXT_PATH__='/flow';&lt;/script&gt;
     * </pre>
     * <p>前端 JS 可通过 window.__CONTEXT_PATH__ 获取当前运行时前缀，
     * 用于拼接 API 请求地址等场景。</p>
     */
    private void renderIndexHtml(HttpServletRequest request, HttpServletResponse response) throws IOException {
        // 模板加载失败的降级处理
        if (indexHtmlTemplate == null) {
            response.setContentType("text/html;charset=UTF-8");
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            response.getWriter().write("<h1>Flow UI 不可用</h1><p>index.html 模板加载失败，请检查 JAR 包是否包含前端资源。</p>");
            return;
        }

        // 获取运行时 contextPath（如 "/flow"，无前缀时为 ""）
        String contextPath = request.getContextPath();

        String html = indexHtmlTemplate;

        // 仅当 contextPath 不为空时执行替换（无前缀时 HTML 原样输出，零开销）
        if (contextPath != null && !contextPath.isEmpty()) {
            // 核心替换：将所有 /flow-ui/ 前缀替换为 {contextPath}/flow-ui/
            // 这会覆盖 <script src="...">, <link href="..."> 等所有引用
            html = html.replace("/" + UI_PATH_PREFIX.substring(1) + "/", contextPath + UI_PATH_PREFIX + "/");
        }

        // 在 <head> 标签后注入全局变量，供前端 JS 读取
        // 1. window.__CONTEXT_PATH__: 供前端代码（如 app.ts 的 modifyClientRenderOpts）读取 contextPath
        // 2. window.publicPath: 供 UmiJS 的 runtimePublicPath 机制读取，确保异步 chunk 加载路径正确
        // 即使 contextPath 为空也注入，保证前端代码可以统一访问这些变量
        String runtimePublicPath = contextPath + UI_PATH_PREFIX + "/";
        String injectedScript = "<script>"
                + "window.__CONTEXT_PATH__='" + contextPath + "';"
                + "window.publicPath='" + runtimePublicPath + "';"
                + "</script>";
        html = html.replace("<head>", "<head>" + injectedScript);

        // 输出最终 HTML
        response.setContentType("text/html;charset=UTF-8");
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write(html);
    }
}
