package org.yu.flow.cache;

import org.yu.flow.module.api.domain.FlowApiDO;
import org.yu.flow.module.api.service.FlowApiCrudService;
import org.springframework.context.annotation.DependsOn;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
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
                String url = api.getUrl() == null ? "/" : api.getUrl().trim();
                if (!url.startsWith("/")) {
                    url = "/" + url;
                }
                String hashKey = api.getMethod() + "-" + url;
                FlowRedisUtil.hset(API_TEMP_CACHE_KEY, hashKey, api);
            }
            // 原子替换原键
            FlowRedisUtil.delete(API_CACHE_KEY);
            FlowRedisUtil.rename(API_TEMP_CACHE_KEY, API_CACHE_KEY);
        } else {
            // 无数据时清空原缓存
            FlowRedisUtil.delete(API_CACHE_KEY);
        }
    }

    // 常量定义
    /**
     * 接口map
     */
    private static final String API_CACHE_KEY = "flow:api:map";
    /**
     * 接口临时map，更新时暂存使用
     */
    private static final String API_TEMP_CACHE_KEY = "flow:api:map:temp";

    /**
     * flow-ui 前端管理页面是否开启
     */
    private static final String UI_ENABLED = "flow:uiEnable";
}
