package org.yu.flow.config;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.yu.flow.ui.FlowUiController;

/**
 * Flow 前端 UI 静态资源自动配置
 *
 * <p>将打包在 JAR 内 {@code META-INF/resources/flow-ui/} 目录中的前端 SPA 应用
 * 暴露为 {@code /flow-ui/**} 路径的静态资源。</p>
 *
 * <h3>按需装配</h3>
 * <p>仅当宿主项目在 {@code application.yml} 中配置了 {@code yu.flow.enable-ui=true} 时才生效，
 * 避免纯后端微服务节点被强制挂载前端资源。</p>
 *
 * <h3>SPA History 路由支持</h3>
 * <p>SPA 子路由的 Fallback 转发逻辑已迁移至 {@link FlowUiController}，
 * 该控制器使用正则路径变量排除静态资源，将所有页面级请求 forward 到 {@code index.html}。
 * 本配置类仅负责静态资源映射，不再注册 ViewController。</p>
 *
 * yu-flow
 * @see FlowUiController
 */
@AutoConfiguration
@ConditionalOnProperty(prefix = "yu.flow", name = "enable-ui", havingValue = "true")
public class FlowUiAutoConfiguration implements WebMvcConfigurer {

    private static final String UI_RESOURCE_LOCATION = "classpath:/META-INF/resources/flow-ui/";

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/flow-ui/**")
                .addResourceLocations(UI_RESOURCE_LOCATION)
                .setCachePeriod(3600);
    }
}
