package org.yu.flow.module.api.dataview;

import cn.hutool.core.util.StrUtil;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.yu.flow.config.ContractParamTypeConverter;
import org.yu.flow.exception.ValidationException;
import org.yu.flow.module.api.domain.FlowApiDO;
import org.yu.flow.module.api.dto.ViewExportConfigDTO;
import org.yu.flow.module.api.privacy.EffectivePrivacy;
import org.yu.flow.module.api.privacy.PrivacyDecision;
import org.yu.flow.module.api.privacy.PrivacyFieldInterceptor;
import org.yu.flow.module.api.repository.FlowApiRepository;
import org.yu.flow.module.host.FlowHostPrincipal;
import org.yu.flow.module.host.FlowHostPrincipalProvider;
import org.yu.flow.util.FlowObjectMapperUtil;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 数据查看与导出共用：取工作版本（草稿或发布快照）、校验是否可查询、绑定参数、读导出配置、隐私脱敏。
 */
@Slf4j
@Component
public class ApiDataViewSupport {

    static final ObjectMapper MAPPER = FlowObjectMapperUtil.flowObjectMapper();

    private final FlowApiRepository flowApiRepository;
    private final ContractParamTypeConverter contractParamTypeConverter;
    private final PrivacyFieldInterceptor privacyFieldInterceptor;
    private final FlowHostPrincipalProvider flowHostPrincipalProvider;

    public ApiDataViewSupport(FlowApiRepository flowApiRepository,
                              ContractParamTypeConverter contractParamTypeConverter,
                              PrivacyFieldInterceptor privacyFieldInterceptor,
                              FlowHostPrincipalProvider flowHostPrincipalProvider) {
        this.flowApiRepository = flowApiRepository;
        this.contractParamTypeConverter = contractParamTypeConverter;
        this.privacyFieldInterceptor = privacyFieldInterceptor;
        this.flowHostPrincipalProvider = flowHostPrincipalProvider;
    }

    public FlowApiDO requireApi(String apiId) {
        return flowApiRepository.findById(apiId)
                .orElseThrow(() -> new ValidationException("API 不存在"));
    }

    /** 草稿直接用；否则要求已发布，并以发布快照里的字段为准 */
    public FlowApiDO workingApi(String apiId, boolean useDraft) {
        FlowApiDO api = requireApi(apiId);
        if (useDraft) {
            return api;
        }
        if (api.getPublishStatus() == null || api.getPublishStatus() != 1
                || StrUtil.isBlank(api.getPublishedSnapshot())) {
            throw new ValidationException("接口未发布，请勾选「使用草稿」或先发布");
        }
        return fromSnapshot(api);
    }

    private static FlowApiDO fromSnapshot(FlowApiDO api) {
        try {
            JsonNode snap = MAPPER.readTree(api.getPublishedSnapshot());
            FlowApiDO w = new FlowApiDO();
            w.setId(api.getId());
            w.setDirectoryId(text(snap, "directoryId", api.getDirectoryId()));
            w.setName(text(snap, "name", api.getName()));
            w.setUrl(text(snap, "url", api.getUrl()));
            w.setMethod(text(snap, "method", api.getMethod()));
            w.setServiceType(text(snap, "serviceType", api.getServiceType()));
            w.setSqlContent(text(snap, "sqlContent", api.getSqlContent()));
            w.setDslContent(text(snap, "dslContent", api.getDslContent()));
            w.setDatasource(text(snap, "datasource", api.getDatasource()));
            w.setResponseType(text(snap, "responseType", api.getResponseType()));
            w.setContract(text(snap, "contract", api.getContract()));
            w.setPrivacyConfig(text(snap, "privacyConfig", api.getPrivacyConfig()));
            w.setViewExportConfig(text(snap, "viewExportConfig", api.getViewExportConfig()));
            w.setPublishStatus(1);
            w.setPublishedSnapshot(api.getPublishedSnapshot());
            return w;
        } catch (Exception e) {
            throw new ValidationException("解析发布快照失败");
        }
    }

    private static String text(JsonNode snap, String field, String fallback) {
        if (snap != null && snap.has(field) && !snap.get(field).isNull()) {
            String v = snap.get(field).asText(null);
            if (StrUtil.isNotBlank(v)) {
                return v;
            }
        }
        return fallback;
    }

    public static void assertDbQueryable(FlowApiDO api) {
        if (!"DB".equalsIgnoreCase(api.getServiceType())) {
            throw new ValidationException("P0 仅支持 DB 模式接口的数据查看/导出（当前: "
                    + StrUtil.blankToDefault(api.getServiceType(), "-") + "）");
        }
        if (StrUtil.isBlank(api.getSqlContent())) {
            throw new ValidationException("SQL 为空，无法查询");
        }
        if (StrUtil.isBlank(api.getDatasource())) {
            throw new ValidationException("未配置数据源");
        }
        if (!Set.of("PAGE", "LIST", "OBJECT").contains(responseType(api))) {
            throw new ValidationException("仅支持 PAGE / LIST / OBJECT 响应类型");
        }
    }

    /** 大写的响应类型，未配置按 LIST */
    public static String responseType(FlowApiDO api) {
        return StrUtil.blankToDefault(api.getResponseType(), "LIST").toUpperCase(Locale.ROOT);
    }

    /** 动态 SQL 参数：有契约时按契约做类型转换，并按网关的键名布局放好 */
    public Map<String, Object> execParams(String contract, Map<String, String> queryParams,
                                          Map<String, Object> bodyParams, Map<String, String> pathParams) {
        Map<String, Object> q = toObjMap(queryParams);
        Map<String, Object> b = bodyParams == null ? Map.of() : bodyParams;
        Map<String, Object> p = toObjMap(pathParams);

        Map<String, Object> result = new LinkedHashMap<>();
        if (StrUtil.isNotBlank(contract)) {
            Map<String, Object> query = contractParamTypeConverter.convertSection(contract, "query", q);
            Map<String, Object> body = contractParamTypeConverter.convertSection(contract, "body", b);
            Map<String, Object> path = contractParamTypeConverter.convertSection(contract, "pathParams", p);
            result.put("@QP", query);
            result.put("@BP", body);
            result.put("@PP", path);
            result.put("params", query);
            result.put("queryParams", query);
            result.put("body", body);
            result.put("bodyParams", body);
            result.put("pathParams", path);
            result.putAll(query);
            result.putAll(body);
            result.putAll(path);
        } else {
            result.putAll(q);
            result.putAll(b);
            result.putAll(p);
            result.put("queryParams", q);
            result.put("bodyParams", b);
            result.put("pathParams", p);
        }
        return result;
    }

    private static Map<String, Object> toObjMap(Map<String, String> in) {
        Map<String, Object> out = new LinkedHashMap<>();
        if (in != null) {
            in.forEach(out::put);
        }
        return out;
    }

    /** 配置为空或损坏时返回默认配置（导出开启、动态表头） */
    public static ViewExportConfigDTO parseConfig(String json) {
        if (StrUtil.isBlank(json)) {
            return ViewExportConfigDTO.builder().build();
        }
        try {
            ViewExportConfigDTO cfg = MAPPER.readValue(json, ViewExportConfigDTO.class);
            return cfg == null ? ViewExportConfigDTO.builder().build() : cfg;
        } catch (Exception e) {
            log.warn("[ApiDataView] 解析 viewExportConfig 失败: {}", e.getMessage());
            return ViewExportConfigDTO.builder().build();
        }
    }

    // ─── 隐私 ─────────────────────────────────────────────────────────────

    public EffectivePrivacy privacy(FlowApiDO api, boolean useDraft) {
        return privacyFieldInterceptor == null
                ? EffectivePrivacy.disabled()
                : privacyFieldInterceptor.resolveConfig(api, useDraft);
    }

    /** 按当前调用方身份决定脱敏级别 */
    public PrivacyDecision decide(EffectivePrivacy privacy) {
        return privacyFieldInterceptor == null
                ? PrivacyDecision.mask()
                : privacyFieldInterceptor.resolveDecision(currentPrincipal(), privacy);
    }

    public List<Map<String, Object>> maskRows(List<Map<String, Object>> rows,
                                              EffectivePrivacy privacy, PrivacyDecision decision) {
        if (privacyFieldInterceptor == null || privacy == null || !privacy.isEnabled()
                || rows == null || rows.isEmpty()) {
            return rows;
        }
        Object walked = privacyFieldInterceptor.apply(rows, privacy, decision, null, false);
        if (!(walked instanceof List<?> list)) {
            return rows;
        }
        List<Map<String, Object>> out = new ArrayList<>(list.size());
        for (Object item : list) {
            if (item instanceof Map<?, ?> m) {
                out.add(stringKeys(m));
            }
        }
        return out;
    }

    public Map<String, Object> maskObject(Map<String, Object> object,
                                          EffectivePrivacy privacy, PrivacyDecision decision) {
        if (privacyFieldInterceptor == null || privacy == null || !privacy.isEnabled() || object == null) {
            return object;
        }
        Object walked = privacyFieldInterceptor.apply(object, privacy, decision, null, false);
        return walked instanceof Map<?, ?> m ? stringKeys(m) : object;
    }

    private FlowHostPrincipal currentPrincipal() {
        ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        HttpServletRequest req = attrs == null ? null : attrs.getRequest();
        FlowHostPrincipal bound = org.yu.flow.module.host.FlowHostRequestAttrs.getPrincipal(req);
        if (bound != null) {
            return bound;
        }
        if (flowHostPrincipalProvider == null || req == null) {
            return null;
        }
        return flowHostPrincipalProvider.resolve(req).orElse(null);
    }

    // ─── 查询结果 ──────────────────────────────────────────────────────────

    public static Map<String, Object> objectRow(Object raw) {
        if (raw == null) {
            return null;
        }
        if (raw instanceof Map<?, ?> m) {
            return stringKeys(m);
        }
        throw new ValidationException("OBJECT 查询结果不是对象: " + raw.getClass().getSimpleName());
    }

    public static List<Map<String, Object>> rows(Object result) {
        if (result instanceof List<?> list) {
            List<Map<String, Object>> rows = new ArrayList<>();
            for (Object o : list) {
                if (o instanceof Map<?, ?> m) {
                    rows.add(stringKeys(m));
                }
            }
            return rows;
        }
        if (result instanceof Map<?, ?> m) {
            return new ArrayList<>(List.of(stringKeys(m)));
        }
        return new ArrayList<>();
    }

    private static Map<String, Object> stringKeys(Map<?, ?> m) {
        Map<String, Object> row = new LinkedHashMap<>();
        m.forEach((k, v) -> row.put(String.valueOf(k), v));
        return row;
    }
}
