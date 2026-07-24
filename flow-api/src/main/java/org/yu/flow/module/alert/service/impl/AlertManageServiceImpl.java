package org.yu.flow.module.alert.service.impl;

import cn.hutool.core.util.StrUtil;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.yu.flow.auto.dto.PageBean;
import org.yu.flow.config.DemoModeGuard;
import org.yu.flow.module.alert.AlertEmailSender;
import org.yu.flow.module.alert.AlertWebhookSender;
import org.yu.flow.module.alert.domain.AlertChannelDO;
import org.yu.flow.module.alert.domain.AlertRuleDO;
import org.yu.flow.module.alert.dto.*;
import org.yu.flow.module.alert.query.AlertChannelQueryDTO;
import org.yu.flow.module.alert.query.AlertEventQueryDTO;
import org.yu.flow.module.alert.query.AlertRuleQueryDTO;
import org.yu.flow.module.alert.repository.AlertChannelRepository;
import org.yu.flow.module.alert.repository.AlertEventRepository;
import org.yu.flow.module.alert.repository.AlertRuleRepository;
import org.yu.flow.module.alert.service.AlertDispatchService;
import org.yu.flow.module.alert.service.AlertManageService;
import org.yu.flow.util.FlowObjectMapperUtil;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.annotation.Resource;
import jakarta.persistence.criteria.Predicate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class AlertManageServiceImpl implements AlertManageService {

    @Resource
    private AlertChannelRepository alertChannelRepository;
    @Resource
    private AlertRuleRepository alertRuleRepository;
    @Resource
    private AlertEventRepository alertEventRepository;
    @Resource
    private AlertDispatchService alertDispatchService;
    @Resource
    private AlertWebhookSender alertWebhookSender;
    @Resource
    private AlertEmailSender alertEmailSender;
    @Resource
    private DemoModeGuard demoModeGuard;

    @Override
    public PageBean<AlertChannelDTO> pageChannels(AlertChannelQueryDTO query) {
        int page = Math.max(query.getPage() - 1, 0);
        Pageable pageable = PageRequest.of(page, query.getSize(),
                Sort.by(Sort.Direction.DESC, "updateTime"));
        Specification<AlertChannelDO> spec = (root, q, cb) -> {
            List<Predicate> ps = new ArrayList<>();
            if (StrUtil.isNotBlank(query.getName())) {
                ps.add(cb.like(root.get("name"), "%" + query.getName() + "%"));
            }
            if (StrUtil.isNotBlank(query.getType())) {
                ps.add(cb.equal(root.get("type"), query.getType().toUpperCase()));
            }
            if (query.getEnabled() != null) {
                ps.add(cb.equal(root.get("enabled"), query.getEnabled()));
            }
            return cb.and(ps.toArray(new Predicate[0]));
        };
        Page<AlertChannelDO> result = alertChannelRepository.findAll(spec, pageable);
        List<AlertChannelDTO> content = result.getContent().stream()
                .map(AlertChannelDTO::fromDO).collect(Collectors.toList());
        return new PageBean<>(content, result.getNumber() + 1, result.getSize(),
                result.getTotalPages(), result.getTotalElements());
    }

    @Override
    public List<AlertChannelDTO> listChannels() {
        return alertChannelRepository.findAll(Sort.by(Sort.Direction.ASC, "name")).stream()
                .map(AlertChannelDTO::fromDO).collect(Collectors.toList());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AlertChannelDO createChannel(SaveAlertChannelDTO dto) {
        demoModeGuard.checkModifyOrDelete("alert-channel", "告警通道");
        validateChannel(dto);
        LocalDateTime now = LocalDateTime.now();
        AlertChannelDO entity = AlertChannelDO.builder()
                .name(dto.getName().trim())
                .type(dto.getType().trim().toUpperCase())
                .configJson(dto.getConfigJson())
                .enabled(dto.getEnabled() == null ? 1 : dto.getEnabled())
                .createTime(now)
                .updateTime(now)
                .build();
        return alertChannelRepository.save(entity);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AlertChannelDO updateChannel(String id, SaveAlertChannelDTO dto) {
        demoModeGuard.checkModifyOrDelete(id, "告警通道");
        AlertChannelDO existing = alertChannelRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("通道不存在: " + id));
        if (StrUtil.isNotBlank(dto.getName())) {
            existing.setName(dto.getName().trim());
        }
        if (StrUtil.isNotBlank(dto.getType())) {
            existing.setType(dto.getType().trim().toUpperCase());
        }
        if (dto.getConfigJson() != null) {
            existing.setConfigJson(dto.getConfigJson());
        }
        if (dto.getEnabled() != null) {
            existing.setEnabled(dto.getEnabled());
        }
        SaveAlertChannelDTO check = new SaveAlertChannelDTO();
        check.setName(existing.getName());
        check.setType(existing.getType());
        check.setConfigJson(existing.getConfigJson());
        check.setEnabled(existing.getEnabled());
        validateChannel(check);
        existing.setUpdateTime(LocalDateTime.now());
        return alertChannelRepository.save(existing);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteChannel(String id) {
        demoModeGuard.checkModifyOrDelete(id, "告警通道");
        alertChannelRepository.deleteById(id);
    }

    @Override
    public boolean testChannel(String id) {
        AlertChannelDO ch = alertChannelRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("通道不存在: " + id));
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("source", "yu-flow");
        payload.put("type", "alert.test");
        payload.put("ts", System.currentTimeMillis());
        payload.put("msgtype", "text");
        Map<String, Object> text = new LinkedHashMap<>();
        text.put("content", "【Yu Flow】告警通道测试消息");
        payload.put("text", text);

        String type = StrUtil.blankToDefault(ch.getType(), "").toUpperCase();
        if ("WEBHOOK".equals(type)) {
            String url = readUrl(ch.getConfigJson());
            return alertWebhookSender.postJson(url, payload);
        }
        if ("EMAIL".equals(type)) {
            return alertEmailSender.send(ch.getConfigJson(), "【Yu Flow】告警通道测试",
                    "这是一条来自 Yu Flow 告警通道的测试邮件。");
        }
        throw new RuntimeException("未知通道类型: " + type);
    }

    @Override
    public PageBean<AlertRuleDTO> pageRules(AlertRuleQueryDTO query) {
        int page = Math.max(query.getPage() - 1, 0);
        Pageable pageable = PageRequest.of(page, query.getSize(),
                Sort.by(Sort.Direction.DESC, "updateTime"));
        Specification<AlertRuleDO> spec = (root, q, cb) -> {
            List<Predicate> ps = new ArrayList<>();
            if (StrUtil.isNotBlank(query.getName())) {
                ps.add(cb.like(root.get("name"), "%" + query.getName() + "%"));
            }
            if (query.getEnabled() != null) {
                ps.add(cb.equal(root.get("enabled"), query.getEnabled()));
            }
            return cb.and(ps.toArray(new Predicate[0]));
        };
        Page<AlertRuleDO> result = alertRuleRepository.findAll(spec, pageable);
        List<AlertRuleDTO> content = result.getContent().stream()
                .map(AlertRuleDTO::fromDO).collect(Collectors.toList());
        return new PageBean<>(content, result.getNumber() + 1, result.getSize(),
                result.getTotalPages(), result.getTotalElements());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AlertRuleDO createRule(SaveAlertRuleDTO dto) {
        demoModeGuard.checkModifyOrDelete("alert-rule", "告警规则");
        LocalDateTime now = LocalDateTime.now();
        AlertRuleDO entity = AlertRuleDO.builder()
                .name(StrUtil.blankToDefault(dto.getName(), "未命名规则").trim())
                .enabled(dto.getEnabled() == null ? 1 : dto.getEnabled())
                .scopeAssetTypes(dto.getScopeAssetTypes())
                .window(StrUtil.blankToDefault(dto.getWindow(), "24h"))
                .minHealth("warn".equalsIgnoreCase(StrUtil.trim(dto.getMinHealth())) ? "warn" : "error")
                .topN(clamp(dto.getTopN(), 1, 100, 10))
                .channelIds(dto.getChannelIds())
                .intervalMinutes(clamp(dto.getIntervalMinutes(), 1, 1440, 15))
                .dedupMinutes(clamp(dto.getDedupMinutes(), 1, 10080, 60))
                .createTime(now)
                .updateTime(now)
                .build();
        return alertRuleRepository.save(entity);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AlertRuleDO updateRule(String id, SaveAlertRuleDTO dto) {
        demoModeGuard.checkModifyOrDelete(id, "告警规则");
        AlertRuleDO existing = alertRuleRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("规则不存在: " + id));
        if (StrUtil.isNotBlank(dto.getName())) {
            existing.setName(dto.getName().trim());
        }
        if (dto.getEnabled() != null) {
            existing.setEnabled(dto.getEnabled());
        }
        if (dto.getScopeAssetTypes() != null) {
            existing.setScopeAssetTypes(dto.getScopeAssetTypes());
        }
        if (StrUtil.isNotBlank(dto.getWindow())) {
            existing.setWindow(dto.getWindow());
        }
        if (StrUtil.isNotBlank(dto.getMinHealth())) {
            existing.setMinHealth("warn".equalsIgnoreCase(dto.getMinHealth().trim()) ? "warn" : "error");
        }
        if (dto.getTopN() != null) {
            existing.setTopN(clamp(dto.getTopN(), 1, 100, 10));
        }
        if (dto.getChannelIds() != null) {
            existing.setChannelIds(dto.getChannelIds());
        }
        if (dto.getIntervalMinutes() != null) {
            existing.setIntervalMinutes(clamp(dto.getIntervalMinutes(), 1, 1440, 15));
        }
        if (dto.getDedupMinutes() != null) {
            existing.setDedupMinutes(clamp(dto.getDedupMinutes(), 1, 10080, 60));
        }
        existing.setUpdateTime(LocalDateTime.now());
        return alertRuleRepository.save(existing);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteRule(String id) {
        demoModeGuard.checkModifyOrDelete(id, "告警规则");
        alertRuleRepository.deleteById(id);
    }

    @Override
    public void runRuleOnce(String id) {
        AlertRuleDO rule = alertRuleRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("规则不存在: " + id));
        alertDispatchService.runRule(rule, true);
    }

    @Override
    public PageBean<AlertEventDTO> pageEvents(AlertEventQueryDTO query) {
        int page = Math.max(query.getPage() - 1, 0);
        Pageable pageable = PageRequest.of(page, query.getSize(),
                Sort.by(Sort.Direction.DESC, "firedAt"));
        Specification<org.yu.flow.module.alert.domain.AlertEventDO> spec = (root, q, cb) -> {
            List<Predicate> ps = new ArrayList<>();
            if (StrUtil.isNotBlank(query.getRuleId())) {
                ps.add(cb.equal(root.get("ruleId"), query.getRuleId()));
            }
            if (StrUtil.isNotBlank(query.getStatus())) {
                ps.add(cb.equal(root.get("status"), query.getStatus()));
            }
            if (StrUtil.isNotBlank(query.getAssetType())) {
                ps.add(cb.equal(root.get("assetType"), query.getAssetType()));
            }
            if (query.getFrom() != null) {
                ps.add(cb.greaterThanOrEqualTo(root.get("firedAt"), query.getFrom()));
            }
            if (query.getTo() != null) {
                ps.add(cb.lessThanOrEqualTo(root.get("firedAt"), query.getTo()));
            }
            return cb.and(ps.toArray(new Predicate[0]));
        };
        Page<org.yu.flow.module.alert.domain.AlertEventDO> result = alertEventRepository.findAll(spec, pageable);
        List<AlertEventDTO> content = result.getContent().stream()
                .map(AlertEventDTO::fromDO).collect(Collectors.toList());
        return new PageBean<>(content, result.getNumber() + 1, result.getSize(),
                result.getTotalPages(), result.getTotalElements());
    }

    @Override
    public List<AlertRuleDO> listEnabledRules() {
        return alertRuleRepository.findByEnabled(1);
    }

    private void validateChannel(SaveAlertChannelDTO dto) {
        if (StrUtil.isBlank(dto.getName())) {
            throw new RuntimeException("通道名称不能为空");
        }
        String type = StrUtil.blankToDefault(dto.getType(), "").trim().toUpperCase();
        if (!"WEBHOOK".equals(type) && !"EMAIL".equals(type)) {
            throw new RuntimeException("通道类型仅支持 WEBHOOK / EMAIL");
        }
        if ("WEBHOOK".equals(type) && StrUtil.isBlank(readUrl(dto.getConfigJson()))) {
            throw new RuntimeException("Webhook 通道需配置 config.url");
        }
        if ("EMAIL".equals(type)) {
            try {
                JsonNode node = FlowObjectMapperUtil.flowObjectMapper().readTree(
                        StrUtil.blankToDefault(dto.getConfigJson(), "{}"));
                if (StrUtil.isBlank(node.path("to").asText(""))) {
                    throw new RuntimeException("Email 通道需配置 config.to");
                }
            } catch (RuntimeException e) {
                throw e;
            } catch (Exception e) {
                throw new RuntimeException("Email 通道 config_json 无效");
            }
        }
    }

    private static String readUrl(String configJson) {
        try {
            JsonNode node = FlowObjectMapperUtil.flowObjectMapper().readTree(
                    StrUtil.blankToDefault(configJson, "{}"));
            return node.path("url").asText("");
        } catch (Exception e) {
            return "";
        }
    }

    private static int clamp(Integer v, int min, int max, int def) {
        int n = v == null ? def : v;
        return Math.min(max, Math.max(min, n));
    }
}
