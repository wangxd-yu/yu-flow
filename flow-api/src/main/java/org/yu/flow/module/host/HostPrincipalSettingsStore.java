package org.yu.flow.module.host;

import cn.hutool.core.util.StrUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
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

/**
 * 读写 {@code HOST_PRINCIPAL_RESOLVER}，不走通用系统配置页。
 */
@Slf4j
@Component
public class HostPrincipalSettingsStore {

    private static final ObjectMapper MAPPER = FlowObjectMapperUtil.flowObjectMapper();
    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");

    @Resource
    private SysConfigRepository sysConfigRepository;
    @Resource
    private SysConfigCacheManager sysConfigCacheManager;

    public boolean exists() {
        return sysConfigRepository.existsByConfigKey(HostPrincipalSettings.SETTINGS_KEY);
    }

    public HostPrincipalSettings load() {
        String raw = sysConfigCacheManager.getStringConfig(HostPrincipalSettings.SETTINGS_KEY, null);
        if (StrUtil.isBlank(raw)) {
            raw = sysConfigRepository.findByConfigKey(HostPrincipalSettings.SETTINGS_KEY)
                    .map(SysConfigDO::getConfigValue)
                    .orElse(null);
        }
        return parse(raw);
    }

    @Transactional(rollbackFor = Exception.class)
    public HostPrincipalSettings save(HostPrincipalSettings incoming) {
        HostPrincipalSettings normalized = incoming != null ? incoming : new HostPrincipalSettings();
        String json;
        try {
            json = MAPPER.writeValueAsString(normalized);
        } catch (Exception e) {
            throw new FlowException("HOST_PRINCIPAL_SETTINGS_INVALID", "无法序列化主体解析配置");
        }
        LocalDateTime now = LocalDateTime.now(ZONE);
        SysConfigDO entity = sysConfigRepository.findByConfigKey(HostPrincipalSettings.SETTINGS_KEY)
                .orElseGet(() -> SysConfigDO.builder()
                        .configKey(HostPrincipalSettings.SETTINGS_KEY)
                        .valueType("JSON")
                        .configGroup("HOST")
                        .remark("宿主主体解析（请从「宿主机配置」维护）")
                        .isBuiltin(1)
                        .status(1)
                        .sortOrder(901)
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

    public static HostPrincipalSettings parse(String raw) {
        if (StrUtil.isBlank(raw)) {
            return new HostPrincipalSettings();
        }
        try {
            HostPrincipalSettings parsed = MAPPER.readValue(raw, HostPrincipalSettings.class);
            return parsed != null ? parsed : new HostPrincipalSettings();
        } catch (Exception e) {
            // 解析失败按未启用处理：宁可回退内置 JWT，也不要让主体解析变成半配置状态
            log.warn("[HostPrincipal] 解析 HOST_PRINCIPAL_RESOLVER 失败，回退默认: {}", e.getMessage());
            return new HostPrincipalSettings();
        }
    }
}
