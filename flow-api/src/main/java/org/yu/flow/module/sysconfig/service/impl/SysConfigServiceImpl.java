package org.yu.flow.module.sysconfig.service.impl;
import org.yu.flow.config.DemoModeGuard;

import cn.hutool.core.util.StrUtil;
import org.yu.flow.auto.dto.PageBean;
import org.yu.flow.module.sysconfig.cache.SysConfigCacheManager;
import org.yu.flow.module.sysconfig.domain.SysConfigDO;
import org.yu.flow.module.sysconfig.dto.SaveSysConfigDTO;
import org.yu.flow.module.sysconfig.dto.SysConfigDTO;
import org.yu.flow.module.sysconfig.query.SysConfigQueryDTO;
import org.yu.flow.module.sysconfig.repository.SysConfigRepository;
import org.yu.flow.module.sysconfig.service.SysConfigService;
import org.yu.flow.log.audit.service.AuditLogService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.annotation.Resource;
import jakarta.persistence.criteria.Predicate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 系统配置 Service 实现
 */
@Service
public class SysConfigServiceImpl implements SysConfigService {

    /** 由「宿主机配置」独占维护的键，不在通用配置页出现，也不允许从这里增删改 */
    private static final List<String> HOST_MANAGED_KEYS = List.of(
            org.yu.flow.module.host.HostCatalogReserved.SETTINGS_KEY,
            org.yu.flow.module.host.HostPrincipalSettings.SETTINGS_KEY,
            org.yu.flow.module.host.HostPrivacyProfiles.SETTINGS_KEY);

    @Resource
    private SysConfigRepository sysConfigRepository;

    @Resource
    private SysConfigCacheManager sysConfigCacheManager;

    @Resource
    private DemoModeGuard demoModeGuard;

    @Resource
    private AuditLogService auditLogService;

    @Override
    public PageBean<SysConfigDTO> findPage(SysConfigQueryDTO queryDTO) {
        int page = Math.max(queryDTO.getPage() - 1, 0);
        Pageable pageable = PageRequest.of(page, queryDTO.getSize(),
                Sort.by(Sort.Direction.ASC, "sortOrder")
                        .and(Sort.by(Sort.Direction.ASC, "configKey")));

        Specification<SysConfigDO> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (StrUtil.isNotBlank(queryDTO.getConfigKey())) {
                predicates.add(cb.like(root.get("configKey"), "%" + queryDTO.getConfigKey() + "%"));
            }
            if (StrUtil.isNotBlank(queryDTO.getConfigGroup())) {
                predicates.add(cb.equal(root.get("configGroup"), queryDTO.getConfigGroup()));
            }
            if (queryDTO.getStatus() != null) {
                predicates.add(cb.equal(root.get("status"), queryDTO.getStatus()));
            }
            predicates.add(cb.not(root.get("configKey").in(HOST_MANAGED_KEYS)));

            return cb.and(predicates.toArray(new Predicate[0]));
        };

        Page<SysConfigDO> result = sysConfigRepository.findAll(spec, pageable);

        List<SysConfigDTO> content = result.getContent().stream()
                .map(SysConfigDTO::fromDO)
                .collect(Collectors.toList());

        return new PageBean<>(
                content,
                result.getNumber() + 1,
                result.getSize(),
                result.getTotalPages(),
                result.getTotalElements()
        );
    }

    @Override
    public SysConfigDTO findById(String id) {
        SysConfigDO entity = sysConfigRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("配置不存在，id: " + id));
        return SysConfigDTO.fromDO(entity);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public SysConfigDO create(SaveSysConfigDTO dto) {
        assertNotHostManaged(dto.getConfigKey());
        if (sysConfigRepository.existsByConfigKey(dto.getConfigKey())) {
            throw new RuntimeException("配置键已存在: " + dto.getConfigKey());
        }

        SysConfigDO entity = SysConfigDO.builder()
                .configKey(dto.getConfigKey())
                .configValue(dto.getConfigValue())
                .valueType(dto.getValueType())
                .configGroup(StrUtil.isNotBlank(dto.getConfigGroup()) ? dto.getConfigGroup() : "GENERAL")
                .remark(dto.getRemark())
                .isBuiltin(dto.getIsBuiltin() == null ? 0 : dto.getIsBuiltin())
                .status(dto.getStatus() == null ? 1 : dto.getStatus())
                .sortOrder(dto.getSortOrder() == null ? 100 : dto.getSortOrder())
                .createTime(LocalDateTime.now())
                .updateTime(LocalDateTime.now())
                .build();

        SysConfigDO saved = sysConfigRepository.save(entity);
        sysConfigCacheManager.publishRefreshEvent();
        return saved;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public SysConfigDO update(String id, SaveSysConfigDTO dto) {
        // [Demo 模式] 预置系统参数不可修改
        demoModeGuard.checkModifyOrDelete(id, "系统参数");

        SysConfigDO existing = sysConfigRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("配置不存在，id: " + id));
        assertNotHostManaged(existing.getConfigKey());

        String oldValue = existing.getConfigValue();
        String configKey = existing.getConfigKey();

        if (StrUtil.isNotBlank(dto.getConfigKey())
                && !dto.getConfigKey().equals(existing.getConfigKey())
                && sysConfigRepository.existsByConfigKeyAndIdNot(dto.getConfigKey(), id)) {
            throw new RuntimeException("配置键已被占用: " + dto.getConfigKey());
        }

        // 内置参数无法改变其 configKey，通常也无法改变分组或类型，但可以修改 value
        if (existing.getIsBuiltin() != null && existing.getIsBuiltin() == 1) {
            // 只允许修改值、备注、排序；密钥类回传 *** 时保留原值
            if (dto.getConfigValue() != null
                    && !isMaskedSecretUpdate(existing.getConfigKey(), dto.getConfigValue())) {
                existing.setConfigValue(dto.getConfigValue());
            }
            if (dto.getRemark() != null) {
                existing.setRemark(dto.getRemark());
            }
            if (dto.getSortOrder() != null) {
                existing.setSortOrder(dto.getSortOrder());
            }
            existing.setUpdateTime(LocalDateTime.now());
            SysConfigDO saved = sysConfigRepository.save(existing);
            sysConfigCacheManager.putLocal(saved);
            sysConfigCacheManager.publishRefreshEvent();
            if (dto.getConfigValue() != null && !StrUtil.equals(oldValue, saved.getConfigValue())) {
                auditConfigUpdate(configKey, oldValue, saved.getConfigValue());
            }
            return saved;
        }

        if (StrUtil.isNotBlank(dto.getConfigKey())) {
            existing.setConfigKey(dto.getConfigKey());
            configKey = dto.getConfigKey();
        }
        if (dto.getConfigValue() != null
                && !isMaskedSecretUpdate(configKey, dto.getConfigValue())) {
            existing.setConfigValue(dto.getConfigValue());
        }
        if (StrUtil.isNotBlank(dto.getValueType())) {
            existing.setValueType(dto.getValueType());
        }
        if (StrUtil.isNotBlank(dto.getConfigGroup())) {
            existing.setConfigGroup(dto.getConfigGroup());
        }
        if (dto.getStatus() != null) {
            existing.setStatus(dto.getStatus());
        }
        if (dto.getSortOrder() != null) {
            existing.setSortOrder(dto.getSortOrder());
        }
        existing.setRemark(dto.getRemark());
        existing.setUpdateTime(LocalDateTime.now());

        SysConfigDO saved = sysConfigRepository.save(existing);
        sysConfigCacheManager.putLocal(saved);
        sysConfigCacheManager.publishRefreshEvent();
        auditConfigUpdate(configKey, oldValue, saved.getConfigValue());
        return saved;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public SysConfigDO updateValueByKey(String configKey, String configValue) {
        if (StrUtil.isBlank(configKey)) {
            throw new RuntimeException("配置键不能为空");
        }
        SysConfigDO existing = sysConfigRepository.findByConfigKey(configKey.trim())
                .orElseThrow(() -> new RuntimeException("配置不存在: " + configKey));
        SaveSysConfigDTO dto = new SaveSysConfigDTO();
        dto.setConfigKey(existing.getConfigKey());
        dto.setConfigValue(configValue);
        dto.setValueType(existing.getValueType());
        dto.setConfigGroup(existing.getConfigGroup());
        dto.setRemark(existing.getRemark());
        dto.setIsBuiltin(existing.getIsBuiltin());
        dto.setStatus(existing.getStatus());
        dto.setSortOrder(existing.getSortOrder());
        return update(existing.getId(), dto);
    }

    private void auditConfigUpdate(String configKey, String oldValue, String newValue) {
        String key = StrUtil.nullToEmpty(configKey);
        boolean secret = SysConfigDTO.isSecretConfigKey(key);
        String ov = secret ? "***" : StrUtil.nullToEmpty(oldValue);
        String nv = secret ? "***" : StrUtil.nullToEmpty(newValue);
        if (ov.length() > 120) ov = ov.substring(0, 120) + "...";
        if (nv.length() > 120) nv = nv.substring(0, 120) + "...";
        auditLogService.record("SYS_CONFIG_UPDATE", "SYS_CONFIG", key,
                "{\"key\":\"" + key + "\",\"old\":\"" + ov.replace("\"", "'")
                        + "\",\"new\":\"" + nv.replace("\"", "'") + "\"}");
    }

    /**
     * 这些键有自己的表单校验（如请求头模式的部署前提确认），从通用配置页改会绕过校验。
     */
    private static void assertNotHostManaged(String configKey) {
        if (StrUtil.isNotBlank(configKey) && HOST_MANAGED_KEYS.contains(configKey)) {
            throw new RuntimeException("请从「平台设置 → 宿主机配置」维护该配置");
        }
    }

    /** 读接口脱敏后的 *** 回写时不得覆盖真实密钥 */
    private static boolean isMaskedSecretUpdate(String configKey, String value) {
        return SysConfigDTO.isSecretConfigKey(configKey)
                && SysConfigDTO.MASKED_VALUE.equals(value);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(String id) {
        // [Demo 模式] 预置系统参数不可删除
        demoModeGuard.checkModifyOrDelete(id, "系统参数");

        SysConfigDO existing = sysConfigRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("配置不存在，id: " + id));
        assertNotHostManaged(existing.getConfigKey());

        if (existing.getIsBuiltin() != null && existing.getIsBuiltin() == 1) {
            throw new RuntimeException("系统内置配置不允许删除: " + existing.getConfigKey());
        }

        sysConfigRepository.deleteById(id);
        sysConfigCacheManager.publishRefreshEvent();
    }
}
