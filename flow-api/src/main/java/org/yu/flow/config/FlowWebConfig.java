package org.yu.flow.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Flow 全局 Web MVC 配置
 *
 * <p>注册 API 拦截器和 UI 访问控制拦截器，并配置各自的路径匹配规则。</p>
 *
 * <h3>拦截器职责划分</h3>
 * <pre>
 *   请求进入
 *       │
 *       ├── /flow-api/**  → FlowApiInterceptor（后端接口鉴权）
 *       │
 *       └── /flow-ui/**   → FlowUiInterceptor（前端页面访问控制）
 *               │
 *               └── 排除静态资源（*.js, *.css, *.png 等），
 *                   这些由 FlowUiAutoConfiguration 的 ResourceHandler 直接处理
 * </pre>
 *
 * yu-flow
 * @see FlowApiInterceptor
 * @see FlowUiInterceptor
 */
@Configuration
public class FlowWebConfig implements WebMvcConfigurer {

    private final FlowApiInterceptor flowApiInterceptor;
    private final FlowUiInterceptor flowUiInterceptor;

    public FlowWebConfig(FlowApiInterceptor flowApiInterceptor, FlowUiInterceptor flowUiInterceptor) {
        this.flowApiInterceptor = flowApiInterceptor;
        this.flowUiInterceptor = flowUiInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // 1. 后端 API 拦截器（排除前端静态资源和页面路由）
        registry.addInterceptor(flowApiInterceptor)
                .addPathPatterns("/**")
                .excludePathPatterns("/flow-ui/**", "/flow-ui.html");

        // 2. 前端 UI 动态访问控制拦截器
        //    拦截所有 /flow-ui/** 路径，FlowUiInterceptor 内部会区分
        //    对外发布页面（preview/designer → 直接放行）和管理页面（检查 UI 开关）
        registry.addInterceptor(flowUiInterceptor)
                .addPathPatterns("/flow-ui/**");
    }
}
