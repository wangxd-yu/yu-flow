package org.yu.flow.module.responsetemplate.cache;

import lombok.extern.slf4j.Slf4j;
import org.yu.flow.module.responsetemplate.domain.ResponseTemplateDO;
import org.yu.flow.module.responsetemplate.repository.ResponseTemplateRepository;
import org.yu.flow.config.response.ResponseTransformer;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * API 响应模板缓存管理器（L1 本地缓存）
 *
 * <p>网关在组装返回值时属于极高频操作，每次请求都会命中模板包装逻辑，
 * 因此使用 ConcurrentHashMap 作为 L1 缓存，保证 O(1) 无锁读取性能。</p>
 *
 * <h3>缓存策略</h3>
 * <ul>
 *   <li>ID 索引：templateId → ResponseTemplateDO</li>
 *   <li>默认索引：固定 key "__DEFAULT__" → 全局默认 ResponseTemplateDO</li>
 *   <li>写穿透：CUD 操作后立即重载全量缓存</li>
 * </ul>
 */
@Slf4j
@Component
public class ResponseTemplateCacheManager {

    /** 默认模板的缓存 Key */
    private static final String DEFAULT_KEY = "__DEFAULT__";

    /**
     * L1 本地缓存：templateId / "__DEFAULT__" → ResponseTemplateDO
     */
    private volatile Map<String, ResponseTemplateDO> TEMPLATE_CACHE = new ConcurrentHashMap<>(32);

    @Resource
    private ResponseTemplateRepository responseTemplateRepository;

    @Resource
    private ResponseTransformer responseTransformer;

    @PostConstruct
    public void init() {
        log.info("[ResponseTemplateCacheManager] 应用启动，开始全量加载响应模板缓存...");
        reloadAll();
    }

    /**
     * 全量重载缓存（写穿透 + 启动预热）
     */
    public void reloadAll() {
        try {
            List<ResponseTemplateDO> allTemplates = responseTemplateRepository.findAll();

            Map<String, ResponseTemplateDO> newCache = new ConcurrentHashMap<>(allTemplates.size() * 2 + 2);

            for (ResponseTemplateDO template : allTemplates) {
                newCache.put(template.getId(), template);
                // 如果是默认模板，同时放入默认索引
                if (template.getIsDefault() != null && template.getIsDefault() == 1) {
                    newCache.put(DEFAULT_KEY, template);
                }
            }

            this.TEMPLATE_CACHE = newCache;

            // 同时清空 Transformer 中的解析缓存，确保最新模板生效
            responseTransformer.clearCache();

            log.info("[ResponseTemplateCacheManager] 缓存重载完成。加载模板数={}, 存在默认模板={}",
                    allTemplates.size(), newCache.containsKey(DEFAULT_KEY));
        } catch (Exception e) {
            log.error("[ResponseTemplateCacheManager] 全量重载缓存异常，保留旧缓存继续服务。", e);
        }
    }

    /**
     * 获取全局默认响应模板
     *
     * @return 默认模板，如果未设置则返回 empty
     */
    public Optional<ResponseTemplateDO> getDefaultTemplate() {
        return Optional.ofNullable(TEMPLATE_CACHE.get(DEFAULT_KEY));
    }

    /**
     * 根据 ID 获取响应模板
     *
     * @param templateId 模板 ID
     * @return 模板实体，如果不存在则返回 empty
     */
    public Optional<ResponseTemplateDO> getTemplateById(String templateId) {
        return Optional.ofNullable(TEMPLATE_CACHE.get(templateId));
    }

    /**
     * 获取缓存中的模板数量（不含 DEFAULT_KEY 索引）
     */
    public int getCacheSize() {
        int size = TEMPLATE_CACHE.size();
        // 扣除可能存在的 DEFAULT_KEY
        if (TEMPLATE_CACHE.containsKey(DEFAULT_KEY)) {
            size--;
        }
        return size;
    }
}
