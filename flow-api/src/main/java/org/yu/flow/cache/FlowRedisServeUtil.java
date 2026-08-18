package org.yu.flow.cache;

import org.yu.flow.module.api.domain.FlowApiDO;
import org.yu.flow.module.api.service.FlowApiCrudService;
import org.springframework.context.annotation.DependsOn;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.Resource;
import java.util.List;
import java.util.concurrent.CompletableFuture;

@Component
@DependsOn({"flowApiServiceImpl", "flowRedisUtil"}) // 明确依赖的 Bean 名称
public class FlowRedisServeUtil {

    // 1. 保持注入的 service 为非静态
    @Resource
    private FlowApiCrudService flowApiService;

    // 2. 静态实例（用于静态方法访问）
    private static FlowRedisServeUtil instance;

    // 3. 初始化方法：确保实例和 service 都已赋值
    @PostConstruct
    public void init() {
        instance = this;
        // 验证 service 是否注入成功（调试用）
        if (instance.flowApiService == null) {
            throw new RuntimeException("FlowApiService 注入失败，请检查 Spring 配置");
        }
        // 异步刷新缓存，不阻塞启动
        CompletableFuture.runAsync(() -> refreshApi());
    }

    // 4. 静态方法中通过 instance 访问 service
    public static void refreshApi() {
        // 双重校验：避免 instance 或 service 为 null
        if (instance == null || instance.flowApiService == null) {
            throw new RuntimeException("FlowRedisServeUtil 未初始化或 FlowApiCrudService 注入失败");
        }

        List<FlowApiDO> publishApis = instance.flowApiService.findPublishApi();
        if (publishApis != null && !publishApis.isEmpty()) {
            // 先清空临时键（避免残留旧数据）
            FlowRedisUtil.delete(API_TEMP_CACHE_KEY);
            // 写入新数据到临时键
            for (FlowApiDO api : publishApis) {
                // 容错处理：去空格、确保以 / 开头
                FlowRedisUtil.hset(API_TEMP_CACHE_KEY, apiMapHashField(api.getMethod(), api.getUrl()), api);
            }
            // 原子替换原键
            FlowRedisUtil.delete(API_CACHE_KEY);
            FlowRedisUtil.rename(API_TEMP_CACHE_KEY, API_CACHE_KEY);
        } else {
            // 无数据时清空原缓存
            FlowRedisUtil.delete(API_CACHE_KEY);
        }
    }

    /** 已发布 API 路由 HASH（field = METHOD-/path） */
    public static final String API_CACHE_KEY = "flow:api:map";
    /** 刷新时暂存，rename 到 {@link #API_CACHE_KEY} */
    private static final String API_TEMP_CACHE_KEY = "flow:api:map:temp";

    /**
     * HASH field：与写入侧保持一致，路径必须以 {@code /} 开头。
     */
    public static String apiMapHashField(String method, String url) {
        String path = url == null ? "/" : url.trim();
        if (!path.startsWith("/")) {
            path = "/" + path;
        }
        return (method == null ? "" : method) + "-" + path;
    }
}
