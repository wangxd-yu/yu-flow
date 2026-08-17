package org.yu.flow.module.assetversion;

import cn.hutool.core.util.StrUtil;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.yu.flow.module.api.domain.FlowApiDO;
import org.yu.flow.module.mqtask.domain.FlowMqTaskDO;
import org.yu.flow.module.serviceflow.domain.FlowServiceFlowDO;
import org.yu.flow.module.task.domain.FlowTaskDO;

/**
 * 判断已发布资产的草稿相对 {@code publishedSnapshot} 是否有未发布内容变更。
 * <p>不依赖 updateTime/publishTime，避免 enable/log/cache 等运营字段误报「待更新发布」。</p>
 */
public final class UnpublishedChangeDetector {

    private static final ObjectMapper MAPPER = org.yu.flow.util.FlowObjectMapperUtil.flowObjectMapper();

    private UnpublishedChangeDetector() {
    }

    public static boolean apiHasUnpublishedChanges(FlowApiDO api) {
        if (!isPublished(api.getPublishStatus(), api.getPublishedSnapshot())) {
            return false;
        }
        try {
            JsonNode snap = MAPPER.readTree(api.getPublishedSnapshot());
            return differs(api.getName(), snap, "name")
                    || differs(api.getUrl(), snap, "url")
                    || differs(api.getMethod(), snap, "method")
                    || differs(api.getInfo(), snap, "info")
                    || differs(api.getTags(), snap, "tags")
                    || differs(api.getVersion(), snap, "version")
                    || differs(api.getServiceType(), snap, "serviceType")
                    || differsInterceptMode(api.getInterceptMode(), snap)
                    || differs(api.getHostBinding(), snap, "hostBinding")
                    || differs(api.getDslContent(), snap, "dslContent")
                    || differs(api.getSqlContent(), snap, "sqlContent")
                    || differs(api.getJsonContent(), snap, "jsonContent")
                    || differs(api.getTextContent(), snap, "textContent")
                    || differs(api.getDatasource(), snap, "datasource")
                    || differs(api.getResponseType(), snap, "responseType")
                    || differs(api.getContract(), snap, "contract")
                    || differs(api.getTemplateId(), snap, "templateId")
                    || differs(api.getCustomSuccessWrapper(), snap, "customSuccessWrapper")
                    || differs(api.getCustomPageWrapper(), snap, "customPageWrapper")
                    || differs(api.getCustomFailWrapper(), snap, "customFailWrapper")
                    || differs(api.getSecurityConfig(), snap, "securityConfig")
                    || differs(api.getPrivacyConfig(), snap, "privacyConfig")
                    || differsInt(api.getLevel(), snap, "level");
            // logEnabled / cacheConfig 视为运营配置，不计入「待更新发布」
        } catch (Exception e) {
            return true;
        }
    }

    public static boolean taskHasUnpublishedChanges(FlowTaskDO task) {
        if (!isPublished(task.getPublishStatus(), task.getPublishedSnapshot())) {
            return false;
        }
        try {
            JsonNode snap = MAPPER.readTree(task.getPublishedSnapshot());
            return differs(task.getName(), snap, "name")
                    || differs(task.getCron(), snap, "cron")
                    || differs(task.getDslContent(), snap, "dslContent")
                    || differs(task.getInfo(), snap, "info")
                    || differs(task.getTags(), snap, "tags");
            // enabled / logEnabled 不计入
        } catch (Exception e) {
            return true;
        }
    }

    public static boolean mqTaskHasUnpublishedChanges(FlowMqTaskDO task) {
        if (!isPublished(task.getPublishStatus(), task.getPublishedSnapshot())) {
            return false;
        }
        try {
            JsonNode snap = MAPPER.readTree(task.getPublishedSnapshot());
            return differs(task.getName(), snap, "name")
                    || differs(task.getConnectionCode(), snap, "connectionCode")
                    || differs(task.getTopic(), snap, "topic")
                    || differs(task.getConsumerGroup(), snap, "consumerGroup")
                    || differsInt(task.getConcurrency(), snap, "concurrency")
                    || differs(task.getDslContent(), snap, "dslContent")
                    || differs(task.getInfo(), snap, "info")
                    || differs(task.getTags(), snap, "tags");
            // enabled / logEnabled 不计入
        } catch (Exception e) {
            return true;
        }
    }

    public static boolean serviceHasUnpublishedChanges(FlowServiceFlowDO entity) {
        if (!isPublished(entity.getPublishStatus(), entity.getPublishedSnapshot())) {
            return false;
        }
        try {
            JsonNode snap = MAPPER.readTree(entity.getPublishedSnapshot());
            return differs(entity.getName(), snap, "name")
                    || differs(entity.getDslContent(), snap, "dslContent")
                    || differs(entity.getContract(), snap, "contract")
                    || differs(entity.getInfo(), snap, "info")
                    || differs(entity.getTags(), snap, "tags");
            // enabled / logEnabled 不计入
        } catch (Exception e) {
            return true;
        }
    }

    private static boolean isPublished(Integer publishStatus, String publishedSnapshot) {
        return publishStatus != null
                && publishStatus == 1
                && StrUtil.isNotBlank(publishedSnapshot);
    }

    private static boolean differs(String draft, JsonNode snap, String field) {
        return !norm(draft).equals(norm(textOf(snap, field)));
    }

    private static boolean differsInt(Integer draft, JsonNode snap, String field) {
        Integer snapVal = null;
        if (snap != null && snap.has(field) && !snap.get(field).isNull()) {
            snapVal = snap.get(field).asInt();
        }
        int d = draft != null ? draft : 0;
        int s = snapVal != null ? snapVal : 0;
        return d != s;
    }

    private static String textOf(JsonNode snap, String field) {
        if (snap == null || !snap.has(field) || snap.get(field).isNull()) {
            return null;
        }
        JsonNode n = snap.get(field);
        return n.isValueNode() ? n.asText() : n.toString();
    }

    private static String norm(String v) {
        return v == null ? "" : v.trim();
    }

    /** 草稿/快照缺省均视为 REPLACE，兼容旧快照无该字段 */
    private static boolean differsInterceptMode(String draft, JsonNode snap) {
        String d = StrUtil.isBlank(draft) ? "REPLACE" : draft.trim().toUpperCase();
        String s = textOf(snap, "interceptMode");
        if (StrUtil.isBlank(s)) {
            s = "REPLACE";
        } else {
            s = s.trim().toUpperCase();
        }
        return !d.equals(s);
    }
}
