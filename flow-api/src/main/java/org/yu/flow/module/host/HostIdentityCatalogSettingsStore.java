package org.yu.flow.module.host;

import cn.hutool.core.util.StrUtil;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.yu.flow.exception.FlowException;
import org.yu.flow.module.sysconfig.cache.SysConfigCacheManager;
import org.yu.flow.module.sysconfig.domain.SysConfigDO;
import org.yu.flow.module.sysconfig.repository.SysConfigRepository;
import org.yu.flow.util.FlowObjectMapperUtil;

import jakarta.annotation.Resource;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Iterator;
import java.util.Map;

/**
 * 读写 {@code HOST_IDENTITY_CATALOG}，不走通用系统配置页。
 */
@Slf4j
@Component
public class HostIdentityCatalogSettingsStore {

    private static final ObjectMapper MAPPER = FlowObjectMapperUtil.flowObjectMapper();
    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");

    @Resource
    private SysConfigRepository sysConfigRepository;
    @Resource
    private SysConfigCacheManager sysConfigCacheManager;

    public boolean exists() {
        return sysConfigRepository.existsByConfigKey(HostCatalogReserved.SETTINGS_KEY);
    }

    public HostIdentityCatalogSettings load() {
        String raw = sysConfigCacheManager.getStringConfig(HostCatalogReserved.SETTINGS_KEY, null);
        if (StrUtil.isBlank(raw)) {
            raw = sysConfigRepository.findByConfigKey(HostCatalogReserved.SETTINGS_KEY)
                    .map(SysConfigDO::getConfigValue)
                    .orElse(null);
        }
        return parse(raw);
    }

    /**
     * 鉴权热路径缓存重建时直接核对数据库，避免节点漏收 Pub/Sub 后永久使用旧开关。
     * 数据库不可用或 JSON 损坏时抛错，由上层 fail-closed。
     */
    public HostIdentityCatalogSettings loadFreshRequired() {
        SysConfigDO entity = sysConfigRepository.findByConfigKey(HostCatalogReserved.SETTINGS_KEY)
                .orElse(null);
        String raw = entity != null ? entity.getConfigValue() : null;
        if (StrUtil.isBlank(raw)) {
            return new HostIdentityCatalogSettings();
        }
        try {
            HostIdentityCatalogSettings parsed = parseRequired(raw);
            sysConfigCacheManager.putLocal(entity);
            return parsed;
        } catch (Exception e) {
            throw new FlowException("HOST_CATALOG_UNAVAILABLE", "宿主身份目录配置不可用");
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public HostIdentityCatalogSettings save(HostIdentityCatalogSettings incoming) {
        HostIdentityCatalogSettings normalized = incoming != null ? incoming : new HostIdentityCatalogSettings();
        String json;
        try {
            json = MAPPER.writeValueAsString(toJson(normalized));
        } catch (Exception e) {
            throw new FlowException("HOST_CATALOG_SETTINGS_INVALID", "无法序列化宿主目录配置");
        }
        LocalDateTime now = LocalDateTime.now(ZONE);
        SysConfigDO entity = sysConfigRepository.findByConfigKey(HostCatalogReserved.SETTINGS_KEY)
                .orElseGet(() -> SysConfigDO.builder()
                        .configKey(HostCatalogReserved.SETTINGS_KEY)
                        .valueType("JSON")
                        .configGroup("HOST")
                        .remark("宿主身份目录绑定（请从「宿主机配置」维护）")
                        .isBuiltin(1)
                        .status(1)
                        .sortOrder(900)
                        .createTime(now)
                        .build());
        entity.setConfigValue(json);
        entity.setUpdateTime(now);
        SysConfigDO saved = sysConfigRepository.save(entity);
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    sysConfigCacheManager.putLocal(saved);
                }
            });
        } else {
            sysConfigCacheManager.putLocal(saved);
        }
        sysConfigCacheManager.publishRefreshEvent();
        return normalized;
    }

    public static HostIdentityCatalogSettings parse(String raw) {
        if (StrUtil.isBlank(raw)) {
            return new HostIdentityCatalogSettings();
        }
        try {
            return parseRequired(raw);
        } catch (Exception ex) {
            log.warn("[HostCatalog] 解析 HOST_IDENTITY_CATALOG 失败，回退默认: {}", ex.getMessage());
            return new HostIdentityCatalogSettings();
        }
    }

    private static HostIdentityCatalogSettings parseRequired(String raw) throws Exception {
        HostIdentityCatalogSettings settings = new HostIdentityCatalogSettings();
        JsonNode root = MAPPER.readTree(raw);
        JsonNode dims = root.has("dimensions") ? root.get("dimensions") : root;
        if (dims == null || !dims.isObject()) {
            throw new IllegalArgumentException("dimensions 必须为对象");
        }
        Iterator<Map.Entry<String, JsonNode>> it = dims.fields();
        while (it.hasNext()) {
            Map.Entry<String, JsonNode> e = it.next();
            FlowHostCatalogDimension dim;
            try {
                dim = FlowHostCatalogDimension.parse(e.getKey());
            } catch (FlowException ignore) {
                continue;
            }
            settings.put(dim, readBinding(e.getValue()));
        }
        return settings;
    }

    public static ObjectNode toJson(HostIdentityCatalogSettings settings) {
        ObjectNode root = MAPPER.createObjectNode();
        ObjectNode dims = root.putObject("dimensions");
        for (FlowHostCatalogDimension d : FlowHostCatalogDimension.values()) {
            HostCatalogDimBinding b = settings.get(d);
            ObjectNode n = dims.putObject(d.name());
            n.put("enabled", b.isEnabled());
            n.put("valueField", b.resolvedValueField());
            n.put("labelField", b.resolvedLabelField());
            n.put("parentField", b.resolvedParentField());
            n.put("searchable", b.isSearchable());
        }
        return root;
    }

    private static HostCatalogDimBinding readBinding(JsonNode n) {
        HostCatalogDimBinding b = HostCatalogDimBinding.disabledDefault();
        if (n == null || !n.isObject()) {
            return b;
        }
        if (n.has("enabled")) {
            b.setEnabled(n.get("enabled").asBoolean(false));
        }
        if (n.has("valueField") && !n.get("valueField").isNull()) {
            b.setValueField(n.get("valueField").asText("value"));
        }
        if (n.has("labelField") && !n.get("labelField").isNull()) {
            b.setLabelField(n.get("labelField").asText("label"));
        }
        if (n.has("parentField") && !n.get("parentField").isNull()) {
            b.setParentField(n.get("parentField").asText("parentId"));
        }
        if (n.has("searchable")) {
            b.setSearchable(n.get("searchable").asBoolean(false));
        }
        return b;
    }
}
