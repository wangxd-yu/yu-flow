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
