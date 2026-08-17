package org.yu.flow.module.host;

import cn.hutool.core.util.StrUtil;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import org.yu.flow.auto.service.FlowApiExecutionService;
import org.yu.flow.module.api.domain.FlowApiDO;
import org.yu.flow.module.api.repository.FlowApiRepository;
import org.yu.flow.module.api.support.PublishedApiSnapshot;
import org.yu.flow.module.host.dto.HostIdentityCatalogItemDTO;

import jakarta.annotation.Resource;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 进程内执行保留接口，映射为身份目录选项。预览可走草稿；策略表单只消费已发布。
 */
@Slf4j
@Component
public class HostCatalogApiExecutor {

    @Resource
    private FlowApiRepository flowApiRepository;
    @Resource
    private FlowApiExecutionService flowApiExecutionService;
    @Resource
    private HostIdentityCatalogSettingsStore settingsStore;

    public boolean isPublished(FlowHostCatalogDimension dimension) {
        HostCatalogReserved.Spec spec = HostCatalogReserved.spec(dimension);
        if (spec == null) {
            return false;
        }
        return flowApiRepository.findById(spec.id())
                .map(api -> api.getPublishStatus() != null && api.getPublishStatus() == 1)
                .orElse(false);
    }

    public List<HostIdentityCatalogItemDTO> list(FlowHostCatalogDimension dimension,
                                                 String keyword,
                                                 Integer limit,
                                                 boolean draft) {
        HostCatalogReserved.Spec spec = HostCatalogReserved.spec(dimension);
        if (spec == null) {
            return List.of();
        }
        FlowApiDO api = flowApiRepository.findById(spec.id()).orElse(null);
        if (api == null) {
            return List.of();
        }
        if (!draft && (api.getPublishStatus() == null || api.getPublishStatus() != 1)) {
            return List.of();
        }
        return execute(dimension, spec, api, keyword, limit, draft);
    }

    /**
     * 已发布则读发布快照，否则读草稿；只查询一次保留 API。
     */
    public List<HostIdentityCatalogItemDTO> listPreferPublished(FlowHostCatalogDimension dimension,
                                                                String keyword,
                                                                Integer limit) {
        HostCatalogReserved.Spec spec = HostCatalogReserved.spec(dimension);
        if (spec == null) {
            return List.of();
        }
        FlowApiDO api = flowApiRepository.findById(spec.id()).orElse(null);
        if (api == null) {
            return List.of();
        }
        boolean draft = api.getPublishStatus() == null || api.getPublishStatus() != 1;
        return execute(dimension, spec, api, keyword, limit, draft);
    }

    private List<HostIdentityCatalogItemDTO> execute(FlowHostCatalogDimension dimension,
                                                      HostCatalogReserved.Spec spec,
                                                      FlowApiDO api,
                                                      String keyword,
                                                      Integer limit,
                                                      boolean draft) {
        FlowApiDO exec;
        try {
            exec = draft ? draftClone(api) : publishedClone(api);
            assertReadOnly(exec);
        } catch (Exception e) {
            log.warn("[HostCatalog] 物化保留接口 {} 发布快照失败: {}", spec.id(), e.getMessage());
            return List.of();
        }
        HostCatalogDimBinding binding = settingsStore.load().get(dimension);
        int cap = limit != null && limit > FlowHostIdentityCatalogService.MAX_LIMIT
                ? FlowHostIdentityCatalogService.clampTreeLimit(limit)
                : FlowHostIdentityCatalogService.clampLimit(limit);
        Map<String, Object> params = new HashMap<>();
        if (StrUtil.isNotBlank(keyword)) {
            params.put("keyword", keyword.trim());
        }
        params.put("limit", cap);
        Object raw;
        try {
            raw = flowApiExecutionService.executeApi(exec, params, PageRequest.of(0, cap), null);
        } catch (Exception e) {
            log.warn("[HostCatalog] 执行保留接口 {} 失败: {}", spec.id(), e.getMessage());
            return List.of();
        }
        return HostCatalogResultMapper.map(
                raw, binding.resolvedValueField(), binding.resolvedLabelField(),
                binding.resolvedParentField(), keyword, cap);
    }

    /**
     * 强制走草稿字段：{@code publishStatus=0} 让 resolveContent 不读发布快照。
     */
    static FlowApiDO draftClone(FlowApiDO src) {
        FlowApiDO copy = new FlowApiDO();
        copy.setId(src.getId());
        copy.setName(src.getName());
        copy.setUrl(src.getUrl());
        copy.setMethod(src.getMethod());
        copy.setServiceType(src.getServiceType());
        copy.setInterceptMode(src.getInterceptMode());
        copy.setResponseType(src.getResponseType());
        copy.setDatasource(src.getDatasource());
        copy.setDslContent(src.getDslContent());
        copy.setSqlContent(src.getSqlContent());
        copy.setJsonContent(src.getJsonContent());
        copy.setTextContent(src.getTextContent());
        copy.setContract(src.getContract());
        copy.setTemplateId(src.getTemplateId());
        copy.setCustomSuccessWrapper(src.getCustomSuccessWrapper());
        copy.setCustomPageWrapper(src.getCustomPageWrapper());
        copy.setCustomFailWrapper(src.getCustomFailWrapper());
        copy.setLogEnabled(false);
        copy.setLogMode("OFF");
        copy.setPublishStatus(0);
        copy.setPublishedSnapshot(null);
        return copy;
    }

    /**
     * 将发布快照物化为独立执行对象，避免 serviceType/数据源/响应类型读取草稿字段。
     */
    static FlowApiDO publishedClone(FlowApiDO src) {
        JsonNode snap = PublishedApiSnapshot.resolveSnapshotNode(src);
        if (snap == null) {
            throw new IllegalStateException("发布快照不存在或损坏");
        }
        FlowApiDO copy = draftClone(src);
        applyText(snap, "name", copy::setName);
        applyText(snap, "url", copy::setUrl);
        applyText(snap, "method", copy::setMethod);
        applyText(snap, "serviceType", copy::setServiceType);
        applyText(snap, "interceptMode", copy::setInterceptMode);
        applyText(snap, "responseType", copy::setResponseType);
        applyText(snap, "datasource", copy::setDatasource);
        applyText(snap, "dslContent", copy::setDslContent);
        applyText(snap, "sqlContent", copy::setSqlContent);
        applyText(snap, "jsonContent", copy::setJsonContent);
        applyText(snap, "textContent", copy::setTextContent);
        applyText(snap, "contract", copy::setContract);
        applyText(snap, "templateId", copy::setTemplateId);
        applyText(snap, "customSuccessWrapper", copy::setCustomSuccessWrapper);
        applyText(snap, "customPageWrapper", copy::setCustomPageWrapper);
        applyText(snap, "customFailWrapper", copy::setCustomFailWrapper);
        if (snap.has("level") && !snap.get("level").isNull()) {
            copy.setLevel(snap.get("level").asInt());
        }
        return copy;
    }

    private static void applyText(JsonNode snap, String field,
                                  java.util.function.Consumer<String> setter) {
        if (!snap.has(field)) {
            return;
        }
        JsonNode node = snap.get(field);
        setter.accept(node == null || node.isNull() ? null : node.asText());
    }

    private static void assertReadOnly(FlowApiDO api) {
        if (!"DB".equalsIgnoreCase(api.getServiceType())) {
            return;
        }
        String responseType = StrUtil.blankToDefault(api.getResponseType(), "").trim();
        if (!"LIST".equalsIgnoreCase(responseType) && !"PAGE".equalsIgnoreCase(responseType)) {
            throw new IllegalStateException("DB 身份目录只允许 LIST / PAGE");
        }
    }
}
