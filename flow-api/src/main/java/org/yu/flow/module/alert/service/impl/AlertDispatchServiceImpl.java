package org.yu.flow.module.alert.service.impl;

import cn.hutool.core.util.StrUtil;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.yu.flow.cache.FlowRedisUtil;
import org.yu.flow.module.alert.AlertEmailSender;
import org.yu.flow.module.alert.AlertSettings;
import org.yu.flow.module.alert.AlertWebhookSender;
import org.yu.flow.module.alert.domain.AlertChannelDO;
import org.yu.flow.module.alert.domain.AlertEventDO;
import org.yu.flow.module.alert.domain.AlertRuleDO;
import org.yu.flow.module.alert.repository.AlertChannelRepository;
import org.yu.flow.module.alert.repository.AlertEventRepository;
import org.yu.flow.module.alert.service.AlertDispatchService;
import org.yu.flow.module.metrics.MetricsWindow;
import org.yu.flow.module.metrics.dto.AssetMetricsRankItemDTO;
import org.yu.flow.module.metrics.service.MetricsQueryService;
import org.yu.flow.util.FlowObjectMapperUtil;

import jakarta.annotation.Resource;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Slf4j
@Service
public class AlertDispatchServiceImpl implements AlertDispatchService {

    private static final String DEDUP_PREFIX = "flow:alert:dedup:";
    private static final String THROTTLE_PREFIX = "flow:alert:throttle:";
    private static final String LOCK_PREFIX = "flow:alert:lock:";

    @Resource
    private MetricsQueryService metricsQueryService;
    @Resource
    private AlertChannelRepository alertChannelRepository;
    @Resource
    private AlertEventRepository alertEventRepository;
    @Resource
    private AlertWebhookSender alertWebhookSender;
    @Resource
    private AlertEmailSender alertEmailSender;
    @Resource
    private AlertSettings alertSettings;

    @Override
    public void runRule(AlertRuleDO rule, boolean skipThrottle) {
        if (rule == null || rule.getEnabled() == null || rule.getEnabled() != 1) {
            return;
        }
        int intervalMin = Math.max(1, rule.getIntervalMinutes() == null ? 15 : rule.getIntervalMinutes());
        if (!skipThrottle) {
            String throttleKey = THROTTLE_PREFIX + "rule:" + rule.getId();
            try {
                Boolean ok = FlowRedisUtil.setIfAbsent(throttleKey, "1", intervalMin, TimeUnit.MINUTES);
                if (!Boolean.TRUE.equals(ok)) {
                    return;
                }
            } catch (Exception e) {
                log.warn("[AlertDispatch] 规则节流失败: {}", e.getMessage());
                return;
            }
        }

        String lockKey = LOCK_PREFIX + "rule:" + rule.getId();
        String lockVal = UUID.randomUUID().toString();
        boolean locked;
        try {
            locked = FlowRedisUtil.setIfAbsent(lockKey, lockVal, Math.max(30, intervalMin * 60L), TimeUnit.SECONDS);
        } catch (Exception e) {
            log.warn("[AlertDispatch] 抢锁失败: {}", e.getMessage());
            return;
        }
        if (!locked) {
            return;
        }
        try {
            dispatchRule(rule);
        } finally {
            FlowRedisUtil.unlock(lockKey, lockVal);
        }
    }

    @Override
    public void runSysConfigFallback() {
        if (!alertSettings.isEnabled()) {
            return;
        }
        String url = alertSettings.getWebhookUrl();
        if (StrUtil.isBlank(url)) {
            return;
        }
        int intervalMin = alertSettings.getIntervalMinutes();
        String throttleKey = THROTTLE_PREFIX + "sysconfig";
        try {
            Boolean ok = FlowRedisUtil.setIfAbsent(throttleKey, "1", intervalMin, TimeUnit.MINUTES);
            if (!Boolean.TRUE.equals(ok)) {
                return;
            }
        } catch (Exception e) {
            log.warn("[AlertDispatch] SysConfig 节流失败: {}", e.getMessage());
            return;
        }
        String lockKey = LOCK_PREFIX + "sysconfig";
        String lockVal = UUID.randomUUID().toString();
        boolean locked;
        try {
            locked = FlowRedisUtil.setIfAbsent(lockKey, lockVal, Math.max(30, intervalMin * 60L), TimeUnit.SECONDS);
        } catch (Exception e) {
            return;
        }
        if (!locked) {
            return;
        }
        try {
            AlertRuleDO fake = AlertRuleDO.builder()
                    .id(null)
                    .name("SysConfig兜底")
                    .enabled(1)
                    .window(alertSettings.getWindow())
                    .minHealth(alertSettings.getMinHealth())
                    .topN(alertSettings.getTopN())
                    .dedupMinutes(alertSettings.getDedupMinutes())
                    .channelIds(null)
                    .build();
            List<AssetMetricsRankItemDTO> fresh = scanFresh(fake);
            if (fresh.isEmpty()) {
                return;
            }
            Map<String, Object> payload = buildPayload(fake.getWindow(), fresh);
            boolean ok = alertWebhookSender.postJson(url, payload);
            String payloadJson = toJsonQuiet(payload);
            for (AssetMetricsRankItemDTO item : fresh) {
                saveEvent(null, "SysConfig兜底", fingerprint(item), item, fake.getWindow(),
                        "WEBHOOK", null, ok ? "SUCCESS" : "FAIL", payloadJson,
                        ok ? null : "webhook push failed");
            }
            log.info("[AlertDispatch] SysConfig 兜底推送{}: count={}", ok ? "成功" : "失败", fresh.size());
        } finally {
            FlowRedisUtil.unlock(lockKey, lockVal);
        }
    }

    private void dispatchRule(AlertRuleDO rule) {
        List<AssetMetricsRankItemDTO> fresh = scanFresh(rule);
        if (fresh.isEmpty()) {
            return;
        }
        List<AlertChannelDO> channels = resolveChannels(rule.getChannelIds());
        if (channels.isEmpty()) {
            log.warn("[AlertDispatch] 规则 {} 无可用通道", rule.getName());
            for (AssetMetricsRankItemDTO item : fresh) {
                saveEvent(rule.getId(), rule.getName(), fingerprint(item), item, rule.getWindow(),
                        null, null, "FAIL", null, "no enabled channel");
            }
            return;
        }
        Map<String, Object> payload = buildPayload(rule.getWindow(), fresh);
        String payloadJson = toJsonQuiet(payload);
        String textBody = extractText(payload);

        for (AlertChannelDO ch : channels) {
            boolean ok;
            String err = null;
            try {
                ok = deliver(ch, payload, textBody);
                if (!ok) {
                    err = "deliver failed";
                }
            } catch (Exception e) {
                ok = false;
                err = StrUtil.maxLength(e.getMessage(), 400);
            }
            for (AssetMetricsRankItemDTO item : fresh) {
                saveEvent(rule.getId(), rule.getName(), fingerprint(item), item, rule.getWindow(),
                        ch.getType(), ch.getId(), ok ? "SUCCESS" : "FAIL", payloadJson, err);
            }
        }
        log.info("[AlertDispatch] 规则 {} 推送完成: items={}, channels={}",
                rule.getName(), fresh.size(), channels.size());
    }

    private List<AssetMetricsRankItemDTO> scanFresh(AlertRuleDO rule) {
        String window = StrUtil.blankToDefault(rule.getWindow(), "24h");
        int topN = rule.getTopN() == null ? 10 : Math.min(100, Math.max(1, rule.getTopN()));
        String minHealth = "warn".equalsIgnoreCase(StrUtil.trim(rule.getMinHealth())) ? "warn" : "error";
        Set<String> scope = parseScope(rule.getScopeAssetTypes());

        List<AssetMetricsRankItemDTO> all = metricsQueryService.anomalies(
                MetricsWindow.fromParam(window), topN);
        if (all == null || all.isEmpty()) {
            return Collections.emptyList();
        }
        List<AssetMetricsRankItemDTO> filtered = all.stream()
                .filter(i -> matchHealth(minHealth, i.getHealth()))
                .filter(i -> scope.isEmpty() || (i.getAssetType() != null && scope.contains(i.getAssetType().toUpperCase())))
                .collect(Collectors.toList());
        if (filtered.isEmpty()) {
            return Collections.emptyList();
        }

        int dedupMin = Math.max(1, rule.getDedupMinutes() == null ? 60 : rule.getDedupMinutes());
        String ruleKey = StrUtil.blankToDefault(rule.getId(), "sys");
        List<AssetMetricsRankItemDTO> fresh = new ArrayList<>();
        for (AssetMetricsRankItemDTO item : filtered) {
            String fp = fingerprint(item);
            String dedupKey = DEDUP_PREFIX + ruleKey + ":" + fp;
            try {
                Boolean first = FlowRedisUtil.setIfAbsent(dedupKey, "1", dedupMin, TimeUnit.MINUTES);
                if (Boolean.TRUE.equals(first)) {
                    fresh.add(item);
                }
            } catch (Exception e) {
                fresh.add(item);
            }
        }
        return fresh;
    }

    private boolean deliver(AlertChannelDO ch, Map<String, Object> payload, String textBody) {
        String type = StrUtil.blankToDefault(ch.getType(), "").toUpperCase();
        if ("WEBHOOK".equals(type)) {
            String url = readConfigField(ch.getConfigJson(), "url");
            return alertWebhookSender.postJson(url, payload);
        }
        if ("EMAIL".equals(type)) {
            return alertEmailSender.send(ch.getConfigJson(),
                    "【Yu Flow 运行告警】", textBody);
        }
        log.warn("[AlertDispatch] 未知通道类型: {}", type);
        return false;
    }

    private List<AlertChannelDO> resolveChannels(String channelIdsJson) {
        List<String> ids = parseIdList(channelIdsJson);
        if (ids.isEmpty()) {
            return Collections.emptyList();
        }
        return alertChannelRepository.findAllById(ids).stream()
                .filter(c -> c.getEnabled() != null && c.getEnabled() == 1)
                .collect(Collectors.toList());
    }

    private void saveEvent(String ruleId, String ruleName, String fp, AssetMetricsRankItemDTO item,
                           String window, String channelType, String channelId,
                           String status, String payloadJson, String errorMsg) {
        try {
            AlertEventDO ev = AlertEventDO.builder()
                    .ruleId(ruleId)
                    .ruleName(ruleName)
                    .fingerprint(fp)
                    .assetType(item.getAssetType())
                    .assetId(item.getAssetId())
                    .assetName(item.getAssetName())
                    .health(item.getHealth())
                    .errorRate(item.getErrorRate())
                    .failCount(item.getFailCount())
                    .window(window)
                    .channelType(channelType)
                    .channelId(channelId)
                    .status(status)
                    .payloadJson(payloadJson)
                    .errorMsg(errorMsg)
                    .firedAt(LocalDateTime.now())
                    .build();
            alertEventRepository.save(ev);
        } catch (Exception e) {
            log.warn("[AlertDispatch] 写事件失败: {}", e.getMessage());
        }
    }

    private static Map<String, Object> buildPayload(String window, List<AssetMetricsRankItemDTO> fresh) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("source", "yu-flow");
        payload.put("type", "runtime.anomalies");
        payload.put("window", window);
        payload.put("count", fresh.size());
        payload.put("ts", System.currentTimeMillis());
        payload.put("items", fresh);
        String summary = fresh.stream()
                .limit(5)
                .map(i -> String.format("[%s] %s %s fail=%d rate=%.2f",
                        i.getHealth(), i.getAssetType(), i.getAssetName(),
                        i.getFailCount(),
                        i.getErrorRate() == null ? 0 : i.getErrorRate()))
                .collect(Collectors.joining("\n"));
        payload.put("msgtype", "text");
        Map<String, Object> text = new LinkedHashMap<>();
        text.put("content", "【Yu Flow 运行告警】\n窗口=" + window + " 条数=" + fresh.size() + "\n" + summary);
        payload.put("text", text);
        return payload;
    }

    private static String extractText(Map<String, Object> payload) {
        Object text = payload.get("text");
        if (text instanceof Map<?, ?> m) {
            Object c = m.get("content");
            return c == null ? "" : String.valueOf(c);
        }
        return "";
    }

    private static String fingerprint(AssetMetricsRankItemDTO item) {
        return (item.getAssetType() == null ? "" : item.getAssetType())
                + ":" + (item.getAssetId() == null ? "" : item.getAssetId())
                + ":" + (item.getHealth() == null ? "" : item.getHealth());
    }

    private static boolean matchHealth(String minHealth, String health) {
        if ("warn".equals(minHealth)) {
            return "error".equals(health) || "warn".equals(health);
        }
        return "error".equals(health);
    }

    private static Set<String> parseScope(String csv) {
        if (StrUtil.isBlank(csv)) {
            return Collections.emptySet();
        }
        return java.util.Arrays.stream(csv.split(","))
                .map(String::trim)
                .filter(StrUtil::isNotBlank)
                .map(String::toUpperCase)
                .collect(Collectors.toSet());
    }

    private static List<String> parseIdList(String json) {
        if (StrUtil.isBlank(json)) {
            return Collections.emptyList();
        }
        String trimmed = json.trim();
        try {
            if (trimmed.startsWith("[")) {
                return FlowObjectMapperUtil.flowObjectMapper()
                        .readValue(trimmed, new TypeReference<List<String>>() {});
            }
            // 兼容逗号分隔
            return java.util.Arrays.stream(trimmed.split(","))
                    .map(String::trim)
                    .filter(StrUtil::isNotBlank)
                    .collect(Collectors.toList());
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }

    private static String readConfigField(String configJson, String field) {
        if (StrUtil.isBlank(configJson)) {
            return "";
        }
        try {
            JsonNode node = FlowObjectMapperUtil.flowObjectMapper().readTree(configJson);
            return node.path(field).asText("");
        } catch (Exception e) {
            return "";
        }
    }

    private static String toJsonQuiet(Object o) {
        try {
            return FlowObjectMapperUtil.flowObjectMapper().writeValueAsString(o);
        } catch (Exception e) {
            return null;
        }
    }
}
