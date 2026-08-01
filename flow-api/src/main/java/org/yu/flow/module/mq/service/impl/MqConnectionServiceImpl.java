package org.yu.flow.module.mq.service.impl;

import cn.hutool.core.util.StrUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.yu.flow.auto.dto.PageBean;
import org.yu.flow.config.DemoModeGuard;
import org.yu.flow.exception.FlowException;
import org.yu.flow.module.mq.domain.MqConnectionDO;
import org.yu.flow.module.mq.dto.MqConnectionDTO;
import org.yu.flow.module.mq.provider.MqConnectionSpec;
import org.yu.flow.module.mq.provider.MqProviderRegistry;
import org.yu.flow.module.mq.query.MqConnectionQueryDTO;
import org.yu.flow.module.mq.repository.MqConnectionRepository;
import org.yu.flow.module.mq.service.MqConnectionService;
import org.yu.flow.util.AesEncryptUtil;

import jakarta.annotation.Resource;
import jakarta.persistence.criteria.Predicate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * MQ 连接配置业务服务实现
 *
 * @author yu-flow
 */
@Slf4j
@Service
public class MqConnectionServiceImpl implements MqConnectionService {

    private static final ZoneId ZONE_SH = ZoneId.of("Asia/Shanghai");

    @Resource
    private MqConnectionRepository mqConnectionRepository;

    @Resource
    private MqProviderRegistry mqProviderRegistry;

    @Resource
    private AesEncryptUtil aesEncryptUtil;

    @Resource
    private DemoModeGuard demoModeGuard;

    @Resource
    private org.yu.flow.module.mqtask.repository.FlowMqTaskRepository flowMqTaskRepository;

    // ─────────────────────────────────────────────────────────────────────────
    // CRUD
    // ─────────────────────────────────────────────────────────────────────────

    @Override
    @Transactional
    public MqConnectionDO save(MqConnectionDO connectionDO) {
        validateBasic(connectionDO);
        if (mqConnectionRepository.existsByCode(connectionDO.getCode())) {
            throw new FlowException("MQ_CODE_DUPLICATED", "连接编码已存在: " + connectionDO.getCode());
        }
        if (connectionDO.getEnabled() == null) connectionDO.setEnabled(true);
        if (connectionDO.getDeleted() == null) connectionDO.setDeleted(0);
        connectionDO.setHealthStatus(MqConnectionDO.HEALTH_UNKNOWN);
        connectionDO.setPassword(encryptIfPresent(connectionDO.getPassword()));
        LocalDateTime now = LocalDateTime.now(ZONE_SH);
        connectionDO.setCreateTime(now);
        connectionDO.setUpdateTime(now);
        return mqConnectionRepository.save(connectionDO);
    }

    @Override
    @Transactional
    public MqConnectionDO update(MqConnectionDO connectionDO) {
        demoModeGuard.checkModifyOrDelete(connectionDO.getId(), "MQ 连接");
        MqConnectionDO existing = requireConnection(connectionDO.getId());

        if (connectionDO.getName() != null) existing.setName(connectionDO.getName());
        if (connectionDO.getCode() != null && !connectionDO.getCode().equals(existing.getCode())) {
            if (mqConnectionRepository.existsByCode(connectionDO.getCode())) {
                throw new FlowException("MQ_CODE_DUPLICATED", "连接编码已存在: " + connectionDO.getCode());
            }
            mqProviderRegistry.invalidate(existing.getCode());
            existing.setCode(connectionDO.getCode());
        }
        if (connectionDO.getMqType() != null) existing.setMqType(normalizeType(connectionDO.getMqType()));
        if (connectionDO.getServers() != null) existing.setServers(connectionDO.getServers());
        if (connectionDO.getVirtualHost() != null) existing.setVirtualHost(connectionDO.getVirtualHost());
        if (connectionDO.getUsername() != null) existing.setUsername(connectionDO.getUsername());
        // 密码留空 = 保持原密码
        if (StrUtil.isNotBlank(connectionDO.getPassword())) {
            existing.setPassword(aesEncryptUtil.encrypt(connectionDO.getPassword()));
        }
        if (connectionDO.getEnabled() != null) existing.setEnabled(connectionDO.getEnabled());
        if (connectionDO.getInfo() != null) existing.setInfo(connectionDO.getInfo());
        existing.setHealthStatus(MqConnectionDO.HEALTH_UNKNOWN);
        existing.setUpdateTime(LocalDateTime.now(ZONE_SH));

        MqConnectionDO updated = mqConnectionRepository.save(existing);
        // 连接参数可能变化，失效 Provider 客户端缓存
        mqProviderRegistry.invalidate(updated.getCode());
        return updated;
    }

    @Override
    @Transactional
    public void delete(String id) {
        demoModeGuard.checkModifyOrDelete(id, "MQ 连接");
        MqConnectionDO existing = mqConnectionRepository.findById(id).orElse(null);
        if (existing != null && flowMqTaskRepository.existsByConnectionCode(existing.getCode())) {
            throw new FlowException("MQ_CONNECTION_IN_USE",
                    "该连接已被 MQ 任务引用，请先删除或调整相关任务: " + existing.getCode());
        }
        mqConnectionRepository.deleteById(id);
        if (existing != null) {
            mqProviderRegistry.invalidate(existing.getCode());
        }
    }

    @Override
    @Transactional
    public void batchDelete(List<String> ids) {
        if (ids == null || ids.isEmpty()) {
            return;
        }
        ids.forEach(id -> demoModeGuard.checkModifyOrDelete(id, "MQ 连接"));
        for (String id : ids) {
            delete(id);
        }
    }

    @Override
    public MqConnectionDO findById(String id) {
        return mqConnectionRepository.findById(id).orElse(null);
    }

    @Override
    public PageBean<MqConnectionDTO> findPage(MqConnectionQueryDTO queryDTO) {
        Pageable pageable = PageRequest.of(
                queryDTO.getPage(), queryDTO.getSize(),
                Sort.by(Sort.Direction.DESC, "createTime")
        );

        Specification<MqConnectionDO> spec = (root, cq, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (StrUtil.isNotBlank(queryDTO.getName())) {
                predicates.add(cb.like(root.get("name"), "%" + queryDTO.getName() + "%"));
            }
            if (StrUtil.isNotBlank(queryDTO.getCode())) {
                predicates.add(cb.like(root.get("code"), "%" + queryDTO.getCode() + "%"));
            }
            if (StrUtil.isNotBlank(queryDTO.getMqType())) {
                predicates.add(cb.equal(root.get("mqType"), normalizeType(queryDTO.getMqType())));
            }
            if (queryDTO.getEnabled() != null) {
                predicates.add(cb.equal(root.get("enabled"), queryDTO.getEnabled()));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };

        Page<MqConnectionDO> page = mqConnectionRepository.findAll(spec, pageable);
        List<MqConnectionDTO> items = page.getContent().stream()
                .map(MqConnectionDTO::fromDO)
                .collect(Collectors.toList());
        return new PageBean<>(items, page.getNumber(), page.getSize(),
                page.getTotalPages(), page.getTotalElements());
    }

    @Override
    public List<MqConnectionDTO> listEnabled() {
        return mqConnectionRepository.findByEnabled(true).stream()
                .map(MqConnectionDTO::fromDO)
                .collect(Collectors.toList());
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 启用 / 停用
    // ─────────────────────────────────────────────────────────────────────────

    @Override
    @Transactional
    public MqConnectionDO enable(String id) {
        demoModeGuard.checkModifyOrDelete(id, "MQ 连接");
        MqConnectionDO connection = requireConnection(id);
        connection.setEnabled(true);
        connection.setUpdateTime(LocalDateTime.now(ZONE_SH));
        return mqConnectionRepository.save(connection);
    }

    @Override
    @Transactional
    public MqConnectionDO disable(String id) {
        demoModeGuard.checkModifyOrDelete(id, "MQ 连接");
        MqConnectionDO connection = requireConnection(id);
        connection.setEnabled(false);
        connection.setUpdateTime(LocalDateTime.now(ZONE_SH));
        MqConnectionDO saved = mqConnectionRepository.save(connection);
        mqProviderRegistry.invalidate(saved.getCode());
        return saved;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 测试连接 / 构造规格
    // ─────────────────────────────────────────────────────────────────────────

    @Override
    public void testConnection(MqConnectionDO probe) {
        validateBasic(probe);

        String password = probe.getPassword();
        // 密码留空且为已保存连接：回填库中密码
        if (StrUtil.isBlank(password) && StrUtil.isNotBlank(probe.getId())) {
            MqConnectionDO stored = mqConnectionRepository.findById(probe.getId()).orElse(null);
            if (stored != null && StrUtil.isNotBlank(stored.getPassword())) {
                password = aesEncryptUtil.decrypt(stored.getPassword());
            }
        }

        MqConnectionSpec spec = MqConnectionSpec.builder()
                .code(StrUtil.isNotBlank(probe.getCode()) ? probe.getCode() : "__test__")
                .mqType(normalizeType(probe.getMqType()))
                .servers(probe.getServers())
                .virtualHost(probe.getVirtualHost())
                .username(probe.getUsername())
                .password(password)
                .build();

        try {
            mqProviderRegistry.getProvider(spec.getMqType()).testConnection(spec);
            recordTestResult(probe.getId(), true, null);
        } catch (FlowException e) {
            recordTestResult(probe.getId(), false, e.getMessage());
            throw e;
        } catch (Exception e) {
            String msg = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
            recordTestResult(probe.getId(), false, msg);
            throw new FlowException("MQ_CONNECT_FAILED", "MQ 连接失败: " + msg);
        }
    }

    @Override
    public List<String> listTopics(String connectionCode, String keyword, int limit) {
        if (StrUtil.isBlank(connectionCode)) {
            return java.util.Collections.emptyList();
        }
        MqConnectionSpec spec = buildSpec(connectionCode);
        return mqProviderRegistry.getProvider(spec.getMqType()).listTopics(spec, keyword, limit);
    }

    @Override
    public MqConnectionSpec buildSpec(String code) {
        MqConnectionDO connection = mqConnectionRepository.findByCode(code)
                .orElseThrow(() -> new FlowException("MQ_CONNECTION_NOT_FOUND", "MQ 连接不存在: " + code));
        if (connection.getEnabled() == null || !connection.getEnabled()) {
            throw new FlowException("MQ_CONNECTION_DISABLED", "MQ 连接已停用: " + code);
        }
        String password = StrUtil.isNotBlank(connection.getPassword())
                ? aesEncryptUtil.decrypt(connection.getPassword()) : null;
        return MqConnectionSpec.builder()
                .code(connection.getCode())
                .mqType(normalizeType(connection.getMqType()))
                .servers(connection.getServers())
                .virtualHost(connection.getVirtualHost())
                .username(connection.getUsername())
                .password(password)
                .build();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 内部工具
    // ─────────────────────────────────────────────────────────────────────────

    /** 测试结果回写（仅针对已保存连接；失败不影响测试主流程） */
    private void recordTestResult(String id, boolean success, String errorMsg) {
        if (StrUtil.isBlank(id)) {
            return;
        }
        try {
            MqConnectionDO stored = mqConnectionRepository.findById(id).orElse(null);
            if (stored == null) {
                return;
            }
            stored.setHealthStatus(success ? MqConnectionDO.HEALTH_HEALTHY : MqConnectionDO.HEALTH_UNHEALTHY);
            stored.setLastErrorMsg(success ? null : StrUtil.maxLength(errorMsg, 1000));
            stored.setLastTestTime(LocalDateTime.now(ZONE_SH));
            mqConnectionRepository.save(stored);
        } catch (Exception e) {
            log.warn("[MQ] 回写连接测试结果失败 id={}: {}", id, e.getMessage());
        }
    }

    private void validateBasic(MqConnectionDO connectionDO) {
        if (connectionDO == null) {
            throw new FlowException("MQ_PARAM_REQUIRED", "连接配置不能为空");
        }
        if (StrUtil.isBlank(connectionDO.getCode())) {
            throw new FlowException("MQ_CODE_REQUIRED", "连接编码 code 不能为空");
        }
        if (StrUtil.isBlank(connectionDO.getServers())) {
            throw new FlowException("MQ_SERVERS_REQUIRED", "服务器地址 servers 不能为空");
        }
        String type = normalizeType(connectionDO.getMqType());
        if (!MqConnectionSpec.TYPE_RABBITMQ.equals(type) && !MqConnectionSpec.TYPE_KAFKA.equals(type)) {
            throw new FlowException("MQ_TYPE_UNSUPPORTED",
                    "不支持的 MQ 类型: " + connectionDO.getMqType() + "（当前支持 RABBITMQ / KAFKA）");
        }
        connectionDO.setMqType(type);
    }

    private String encryptIfPresent(String plainPassword) {
        return StrUtil.isNotBlank(plainPassword) ? aesEncryptUtil.encrypt(plainPassword) : null;
    }

    private static String normalizeType(String mqType) {
        return mqType == null ? null : mqType.trim().toUpperCase();
    }

    private MqConnectionDO requireConnection(String id) {
        return mqConnectionRepository.findById(id)
                .orElseThrow(() -> new FlowException("MQ_CONNECTION_NOT_FOUND", "MQ 连接不存在: " + id));
    }
}
