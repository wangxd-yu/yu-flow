package org.yu.flow.module.api.support;

import cn.hutool.core.util.StrUtil;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.yu.flow.module.api.domain.FlowApiDO;
import org.yu.flow.util.FlowObjectMapperUtil;

/**
 * 已发布 API 的运行时视图：优先从 {@code publishedSnapshot} 读取契约 / 路由元数据，
 * 避免草稿列污染线上网关校验与 OpenAPI 文档。
 */
@Slf4j
public final class PublishedApiSnapshot {

    private static final ObjectMapper MAPPER = FlowObjectMapperUtil.flowObjectMapper();

    private PublishedApiSnapshot() {
    }

    public static String resolveContract(FlowApiDO api) {
        String fromSnap = textFromSnapshot(api, "contract");
        return fromSnap != null ? fromSnap : api.getContract();
    }

    public static String resolveUrl(FlowApiDO api) {
        String fromSnap = textFromSnapshot(api, "url");
        return fromSnap != null ? fromSnap : api.getUrl();
    }

    public static String resolveMethod(FlowApiDO api) {
        String fromSnap = textFromSnapshot(api, "method");
        return fromSnap != null ? fromSnap : api.getMethod();
    }

    /**
     * 同名拦截模式：优先快照；缺省 {@link ApiInterceptMode#REPLACE}（兼容旧数据）。
     */
    public static String resolveInterceptMode(FlowApiDO api) {
        String fromSnap = textFromSnapshot(api, "interceptMode");
        if (fromSnap != null) {
            return ApiInterceptMode.normalize(fromSnap);
        }
        if (api != null && StrUtil.isNotBlank(api.getInterceptMode())) {
            return ApiInterceptMode.normalize(api.getInterceptMode());
        }
        // 历史：serviceType=HOST 视为 WRAP
        String st = resolveServiceType(api);
        if (ApiInterceptMode.SERVICE_TYPE_HOST.equalsIgnoreCase(st)) {
            return ApiInterceptMode.WRAP;
        }
        return ApiInterceptMode.REPLACE;
    }

    public static String resolveServiceType(FlowApiDO api) {
        String fromSnap = textFromSnapshot(api, "serviceType");
        if (fromSnap != null) {
            return fromSnap;
        }
        return api == null ? null : api.getServiceType();
    }

    public static String resolveHostBinding(FlowApiDO api) {
        String fromSnap = textFromSnapshot(api, "hostBinding");
        return fromSnap != null ? fromSnap : (api == null ? null : api.getHostBinding());
    }

    public static boolean isWrap(FlowApiDO api) {
        return ApiInterceptMode.isWrap(resolveInterceptMode(api));
    }

    public static String resolveName(FlowApiDO api) {
        String fromSnap = textFromSnapshot(api, "name");
        return fromSnap != null ? fromSnap : api.getName();
    }

    public static String resolveInfo(FlowApiDO api) {
        String fromSnap = textFromSnapshot(api, "info");
        return fromSnap != null ? fromSnap : api.getInfo();
    }

    public static String resolveTags(FlowApiDO api) {
        String fromSnap = textFromSnapshot(api, "tags");
        return fromSnap != null ? fromSnap : api.getTags();
    }

    /**
     * 入站防护配置：已发布时只读快照（缺字段 / null → 全继承全局，不读草稿列）。
     * <p>未发布时回退草稿列（管理端预览用）。</p>
     */
    public static String resolveSecurityConfig(FlowApiDO api) {
        JsonNode snap = parseSnapshot(api);
        if (snap != null) {
            if (!snap.has("securityConfig") || snap.get("securityConfig").isNull()) {
                return null;
            }
            String value = snap.get("securityConfig").asText(null);
            return StrUtil.isBlank(value) ? null : value;
        }
        return api == null ? null : api.getSecurityConfig();
    }

    private static String textFromSnapshot(FlowApiDO api, String field) {
        JsonNode snap = parseSnapshot(api);
        if (snap == null || !snap.has(field) || snap.get(field).isNull()) {
            return null;
        }
        String value = snap.get(field).asText(null);
        return StrUtil.isBlank(value) ? null : value;
    }

    private static JsonNode parseSnapshot(FlowApiDO api) {
        if (api == null
                || api.getPublishStatus() == null
                || api.getPublishStatus() != 1
                || StrUtil.isBlank(api.getPublishedSnapshot())) {
            return null;
        }
        try {
            return MAPPER.readTree(api.getPublishedSnapshot());
        } catch (Exception e) {
            log.warn("[PublishedApiSnapshot] 解析 publishedSnapshot 失败。apiId={}", api.getId(), e);
            return null;
        }
    }
}
