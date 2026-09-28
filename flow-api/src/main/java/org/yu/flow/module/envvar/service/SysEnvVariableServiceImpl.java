package org.yu.flow.module.envvar.service;

import cn.hutool.core.util.StrUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.yu.flow.auto.util.JwtTokenUtil;
import org.yu.flow.config.DemoModeGuard;
import org.yu.flow.exception.FlowException;
import org.yu.flow.log.audit.AuditDetail;
import org.yu.flow.log.audit.service.AuditLogService;
import org.yu.flow.module.envvar.cache.EnvVariableCacheManager;
import org.yu.flow.module.envvar.domain.SysEnvVariableDO;
import org.yu.flow.module.envvar.dto.EnvVariableDictVO;
import org.yu.flow.module.envvar.dto.SaveSysEnvVariableDTO;
import org.yu.flow.module.envvar.dto.SysEnvVariableDTO;
import org.yu.flow.module.envvar.repository.SysEnvVariableRepository;
import org.yu.flow.module.envvar.support.EnvVarRefs;
import org.yu.flow.util.AesEncryptUtil;

import jakarta.annotation.Resource;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Locale;

@Slf4j
@Service
public class SysEnvVariableServiceImpl implements SysEnvVariableService {

    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");
    private static final int MAX_VALUE_LENGTH = 1500;
    /** 敏感变量存 AES-GCM 密文（Base64，约为明文字节数的 4/3 加 40），列宽 4000 */
    private static final int MAX_SECRET_BYTES = 2900;

    @Resource
    private SysEnvVariableRepository sysEnvVariableRepository;

    @Resource
    private EnvVariableCacheManager envVariableCacheManager;

    @Resource
    private AesEncryptUtil aesEncryptUtil;

    @Resource
    private DemoModeGuard demoModeGuard;

    @Resource
    private AuditLogService auditLogService;

    @Override
    @Transactional(readOnly = true)
    public List<SysEnvVariableDTO> list(String keyword) {
        String kw = StrUtil.trimToEmpty(keyword).toUpperCase(Locale.ROOT);
        return sysEnvVariableRepository.findAllByOrderByCodeAsc().stream()
                .filter(v -> kw.isEmpty()
                        || v.getCode().contains(kw)
                        || StrUtil.containsIgnoreCase(v.getRemark(), kw))
                .map(SysEnvVariableDTO::fromDO)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<EnvVariableDictVO> dictionary() {
        return sysEnvVariableRepository.findAllByOrderByCodeAsc().stream()
                .map(v -> new EnvVariableDictVO(v.getCode(), Boolean.TRUE.equals(v.getSecret()), v.getRemark()))
                .toList();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public SysEnvVariableDTO create(SaveSysEnvVariableDTO dto) {
        assertWritable();
        String code = StrUtil.trimToEmpty(dto == null ? null : dto.getCode());
        if (!EnvVarRefs.isValidCode(code)) {
            throw new FlowException("ENV_VAR_INVALID", "变量名须以大写字母开头，只能包含大写字母、数字、下划线，最长 64 位");
        }
        if (sysEnvVariableRepository.existsByCode(code)) {
            throw new FlowException("ENV_VAR_DUPLICATE", "变量名已存在: " + code);
        }
        boolean secret = Boolean.TRUE.equals(dto.getSecret());
        LocalDateTime now = LocalDateTime.now(ZONE);
        String user = JwtTokenUtil.currentUsername();
        SysEnvVariableDO entity = SysEnvVariableDO.builder()
                .code(code)
                .secret(secret)
                .value(storeValue(dto.getValue(), secret))
                .remark(StrUtil.trimToNull(dto.getRemark()))
                .createBy(user)
                .createTime(now)
                .updateBy(user)
                .updateTime(now)
                .build();
        SysEnvVariableDO saved = sysEnvVariableRepository.save(entity);
        afterWrite(saved, "ENV_VAR_CREATE");
        return SysEnvVariableDTO.fromDO(saved);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public SysEnvVariableDTO update(String id, SaveSysEnvVariableDTO dto) {
        assertWritable();
        SysEnvVariableDO entity = require(id);
        boolean wasSecret = Boolean.TRUE.equals(entity.getSecret());
        boolean secret = dto.getSecret() != null ? dto.getSecret() : wasSecret;
        // 敏感变量回显不带值，前端不改值时传 null；切换敏感标记时需按新标记重新存储原值
        String plain = dto.getValue() != null ? dto.getValue() : currentPlain(entity, wasSecret);
        entity.setSecret(secret);
        entity.setValue(storeValue(plain, secret));
        if (dto.getRemark() != null) {
            entity.setRemark(StrUtil.trimToNull(dto.getRemark()));
        }
        entity.setUpdateBy(JwtTokenUtil.currentUsername());
        entity.setUpdateTime(LocalDateTime.now(ZONE));
        SysEnvVariableDO saved = sysEnvVariableRepository.save(entity);
        afterWrite(saved, "ENV_VAR_UPDATE");
        return SysEnvVariableDTO.fromDO(saved);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(String id) {
        assertWritable();
        SysEnvVariableDO entity = require(id);
        sysEnvVariableRepository.delete(entity);
        envVariableCacheManager.removeLocal(entity.getCode());
        envVariableCacheManager.publishRefreshEvent();
        audit("ENV_VAR_DELETE", entity);
    }

    private void afterWrite(SysEnvVariableDO saved, String action) {
        envVariableCacheManager.putLocal(saved);
        envVariableCacheManager.publishRefreshEvent();
        audit(action, saved);
    }

    /** 审计只记变量名和敏感标记，不记值 */
    private void audit(String action, SysEnvVariableDO entity) {
        auditLogService.record(action, "ENV_VAR", entity.getId(),
                AuditDetail.of("code", entity.getCode(), "secret", Boolean.TRUE.equals(entity.getSecret())));
    }

    private String storeValue(String plain, boolean secret) {
        String value = plain == null ? "" : plain;
        if (value.length() > MAX_VALUE_LENGTH) {
            throw new FlowException("ENV_VAR_INVALID", "变量值不能超过 " + MAX_VALUE_LENGTH + " 个字符");
        }
        if (secret && value.getBytes(StandardCharsets.UTF_8).length > MAX_SECRET_BYTES) {
            throw new FlowException("ENV_VAR_INVALID", "敏感变量值过长（UTF-8 编码后不能超过 " + MAX_SECRET_BYTES + " 字节）");
        }
        if (secret && !value.isEmpty()) {
            return aesEncryptUtil.encrypt(value);
        }
        return value;
    }

    private String currentPlain(SysEnvVariableDO entity, boolean wasSecret) {
        String stored = entity.getValue();
        if (!wasSecret || StrUtil.isEmpty(stored)) {
            return stored;
        }
        return aesEncryptUtil.decrypt(stored);
    }

    private void assertWritable() {
        if (demoModeGuard.isDemoMode()) {
            throw new FlowException("DEMO_RESTRICTED", "演示模式下不允许修改环境变量");
        }
    }

    private SysEnvVariableDO require(String id) {
        return sysEnvVariableRepository.findById(id)
                .orElseThrow(() -> new FlowException("ENV_VAR_NOT_FOUND", "环境变量不存在: " + id));
    }
}
