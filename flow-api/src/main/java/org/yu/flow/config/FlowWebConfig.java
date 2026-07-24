package org.yu.flow.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.ArrayList;
import java.util.List;

/**
 * Flow 全局 Web MVC 配置
 *
 * <p>注册 UI 访问控制拦截器，并配置各自的路径匹配规则。</p>
 *
 * <h3>拦截器职责划分</h3>
 * <pre>
 *   请求进入
 *       │
 *       ├── /flow-ui/**   → FlowUiInterceptor（前端页面访问控制）
 *               │
 *               └── 排除静态资源（*.js, *.css, *.png 等），
 *                   这些由 FlowUiAutoConfiguration 的 ResourceHandler 直接处理
 * </pre>
 *
 * <p>注意：后端 API 网关已降维至 {@link FlowApiGatewayFilter} 进行拦截处理。</p>
 *
 * yu-flow
 * @see FlowUiInterceptor
 * @see FlowApiGatewayFilter
 */
@Configuration
public class FlowWebConfig implements WebMvcConfigurer {

    private final FlowUiInterceptor flowUiInterceptor;

    public FlowWebConfig(FlowUiInterceptor flowUiInterceptor) {
        this.flowUiInterceptor = flowUiInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // 前端 UI 动态访问控制拦截器
        // 拦截所有 /flow-ui/** 路径，FlowUiInterceptor 内部会区分
        // 对外发布页面（preview/designer → 直接放行）和管理页面（检查 UI 开关）
        registry.addInterceptor(flowUiInterceptor)
                .addPathPatterns("/flow-ui/**");
    }

    /**
     * 兼容浏览器 / umi 常见的 {@code application/json;charset=UTF-8}。
     * Spring 6 默认 Jackson 转换器仅声明 {@code application/json}，部分环境下
     * 带 charset 的 Content-Type 会触发 HttpMediaTypeNotSupportedException。
     */
    @Override
    public void extendMessageConverters(List<HttpMessageConverter<?>> converters) {
        MediaType jsonUtf8 = MediaType.valueOf("application/json;charset=UTF-8");
        for (HttpMessageConverter<?> converter : converters) {
            if (converter instanceof MappingJackson2HttpMessageConverter jackson) {
                List<MediaType> types = new ArrayList<>(jackson.getSupportedMediaTypes());
                if (!types.contains(jsonUtf8)) {
                    types.add(jsonUtf8);
                }
                if (!types.contains(MediaType.APPLICATION_JSON)) {
                    types.add(0, MediaType.APPLICATION_JSON);
                }
                jackson.setSupportedMediaTypes(types);
            }
        }
    }
}
