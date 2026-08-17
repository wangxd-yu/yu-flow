package org.yu.flow.module.mqtask.service.impl;

import cn.hutool.core.util.StrUtil;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
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
import org.yu.flow.module.directory.domain.FlowDirectoryDO;
import org.yu.flow.module.directory.repository.FlowDirectoryRepository;
import org.yu.flow.module.directory.service.FlowDirectoryService;
import org.yu.flow.module.mqtask.consumer.MqConsumerManager;
import org.yu.flow.module.mqtask.domain.FlowMqTaskDO;
import org.yu.flow.module.mqtask.dto.FlowMqTaskDTO;
import org.yu.flow.module.mqtask.query.FlowMqTaskQueryDTO;
import org.yu.flow.module.mqtask.repository.FlowMqTaskRepository;
import org.yu.flow.module.mqtask.service.FlowMqTaskService;

import jakarta.annotation.Resource;
import jakarta.persistence.criteria.Predicate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * MQ 任务业务服务实现
 *
 * <p>照 {@code FlowTaskServiceImpl} 模式：草稿 CRUD + 发布快照 + 订阅联动。
 * 草稿改订阅配置/DSL 不影响线上消费；发布侧变更由 publish / rollback 负责 resubscribe。</p>
 *
 * @author yu-flow
 */
@Slf4j
@Service
public class FlowMqTaskServiceImpl implements FlowMqTaskService {

    private static final ObjectMapper SNAPSHOT_MAPPER = org.yu.flow.util.FlowObjectMapperUtil.flowObjectMapper();

    /** 目录树业务域 */
    private static final String DIR_BIZ_TYPE = "mqtask";

    private static final String DEMO_TARGET = "MQ 任务";

    /** 分页每页上限 */
    private static final int MAX_PAGE_SIZE = 200;

    @Resource
    private FlowMqTaskRepository flowMqTaskRepository;

    @Resource
    private MqConsumerManager mqConsumerManager;

    @Resource
    private FlowDirectoryService flowDirectoryService;

    @Resource
    private FlowDirectoryRepository flowDirectoryRepository;

    @Resource
    private DemoModeGuard demoModeGuard;

    // ─────────────────────────────────────────────────────────────────────────
    // CRUD
    // ─────────────────────────────────────────────────────────────────────────

    @Override
    @Transactional
    public FlowMqTaskDO save(FlowMqTaskDO taskDO) {
        // 请求体直接绑定实体，发布态与主键必须由服务端接管：
        // 客户端自带 id 会让 save() 走 merge 覆盖同 ID 的存量任务；自带 publishStatus/publishedSnapshot
        // 则能跳过发布闸门直接让任意 DSL 上线消费。新建一律落为「未发布草稿」。
        taskDO.setId(null);
        taskDO.setPublishStatus(0);
        taskDO.setPublishedSnapshot(null);
        taskDO.setPublishTime(null);
        taskDO.setDeleted(0);
        validateSubscribeConfig(taskDO.getConnectionCode(), taskDO.getTopic());
        flowDirectoryService.assertDirectoryBizType(taskDO.getDirectoryId(), DIR_BIZ_TYPE);
        if (taskDO.getConcurrency() == null || taskDO.getConcurrency() < 1) taskDO.setConcurrency(1);
        if (taskDO.getEnabled() == null) taskDO.setEnabled(true);
        if (taskDO.getLogEnabled() == null) taskDO.setLogEnabled(false);
        if (StrUtil.isBlank(taskDO.getLogMode())) taskDO.setLogMode("SYSTEM_DEFAULT");
        if (StrUtil.isBlank(taskDO.getLogPayloadMode())) taskDO.setLogPayloadMode("SYSTEM_DEFAULT");
        if (taskDO.getRetryMax() == null) taskDO.setRetryMax(0);
        if (taskDO.getRetryBackoffMs() == null) taskDO.setRetryBackoffMs(1000);
        // 保留天数 <0 视为未配置（跟随系统）
        if (taskDO.getLogRetentionDays() != null && taskDO.getLogRetentionDays() < 0) taskDO.setLogRetentionDays(null);
        LocalDateTime now = LocalDateTime.now();
        taskDO.setCreateTime(now);
        taskDO.setUpdateTime(now);
        // 新建恒为未发布草稿，不注册订阅；订阅由 publish / rollback 负责
        return flowMqTaskRepository.save(taskDO);
    }

    @Override
    @Transactional
    public FlowMqTaskDO update(FlowMqTaskDO taskDO) {
        demoModeGuard.checkModifyOrDelete(taskDO.getId(), DEMO_TARGET);
        FlowMqTaskDO existing = flowMqTaskRepository.findById(taskDO.getId())
                .orElseThrow(() -> new RuntimeException("MQ 任务不存在: " + taskDO.getId()));

        boolean wasSubscribable = isSubscribable(existing);

        if (taskDO.getName() != null) existing.setName(taskDO.getName());
        if (taskDO.getConnectionCode() != null) existing.setConnectionCode(taskDO.getConnectionCode());
        if (taskDO.getTopic() != null) existing.setTopic(taskDO.getTopic());
        if (taskDO.getConsumerGroup() != null) existing.setConsumerGroup(taskDO.getConsumerGroup());
        if (taskDO.getConcurrency() != null) existing.setConcurrency(Math.max(1, taskDO.getConcurrency()));
        if (taskDO.getEnabled() != null) existing.setEnabled(taskDO.getEnabled());
        if (taskDO.getLogEnabled() != null) existing.setLogEnabled(taskDO.getLogEnabled());
        if (taskDO.getLogMode() != null) existing.setLogMode(taskDO.getLogMode());
        if (StrUtil.isBlank(existing.getLogMode())) existing.setLogMode("SYSTEM_DEFAULT");
        if (taskDO.getLogPayloadMode() != null) existing.setLogPayloadMode(taskDO.getLogPayloadMode());
        if (StrUtil.isBlank(existing.getLogPayloadMode())) existing.setLogPayloadMode("SYSTEM_DEFAULT");
        if (taskDO.getRetryMax() != null) existing.setRetryMax(Math.max(0, taskDO.getRetryMax()));
        if (taskDO.getRetryBackoffMs() != null) existing.setRetryBackoffMs(Math.max(0, taskDO.getRetryBackoffMs()));
        if (taskDO.getDeadLetterTopic() != null) existing.setDeadLetterTopic(taskDO.getDeadLetterTopic());
        // 保留天数：-1=清除任务级配置（回退系统），0=永久保留，>0=自定义天数
        if (taskDO.getLogRetentionDays() != null) {
            existing.setLogRetentionDays(taskDO.getLogRetentionDays() < 0 ? null : taskDO.getLogRetentionDays());
        }
        if (taskDO.getDslContent() != null) existing.setDslContent(taskDO.getDslContent());
        if (taskDO.getInfo() != null) existing.setInfo(taskDO.getInfo());
        if (taskDO.getTags() != null) existing.setTags(taskDO.getTags());
        if (taskDO.getDirectoryId() != null) {
            flowDirectoryService.assertDirectoryBizType(
                    StrUtil.isBlank(taskDO.getDirectoryId()) ? null : taskDO.getDirectoryId(), DIR_BIZ_TYPE);
            existing.setDirectoryId(taskDO.getDirectoryId());
        }
        validateSubscribeConfig(existing.getConnectionCode(), existing.getTopic());
        existing.setUpdateTime(LocalDateTime.now());

        FlowMqTaskDO updated = flowMqTaskRepository.save(existing);
        // 草稿改订阅配置/DSL 不影响线上消费；仅启停导致可订阅性变化时调整订阅。
        // 发布侧订阅变更由 publish / rollback 负责 resubscribe。
        boolean nowSubscribable = isSubscribable(updated);
        if (!nowSubscribable) {
            if (wasSubscribable) {
                mqConsumerManager.cancel(updated.getId());
            }
        } else if (!wasSubscribable) {
            mqConsumerManager.subscribe(updated);
        }
        return updated;
    }

    @Override
    @Transactional
    public void delete(String id) {
        demoModeGuard.checkModifyOrDelete(id, DEMO_TARGET);
        mqConsumerManager.cancel(id);
        flowMqTaskRepository.deleteById(id);
    }

    @Override
    @Transactional
    public void batchDelete(List<String> ids) {
        if (ids == null || ids.isEmpty()) {
            return;
        }
        ids.forEach(id -> demoModeGuard.checkModifyOrDelete(id, DEMO_TARGET));
        ids.forEach(mqConsumerManager::cancel);
        flowMqTaskRepository.logicDeleteByIds(ids);
    }

    @Override
    public FlowMqTaskDO findById(String id) {
        return flowMqTaskRepository.findById(id).orElse(null);
    }

    @Override
    public PageBean<FlowMqTaskDTO> findPage(FlowMqTaskQueryDTO queryDTO) {
        // 分页参数来自 query string，需夹取到合法区间：负页码会让 PageRequest 直接抛异常，
        // 超大 size 则会把整表连同 MEDIUMTEXT 的 DSL 一次性拉进内存
        int pageNo = Math.max(0, queryDTO.getPage());
        int pageSize = Math.min(Math.max(1, queryDTO.getSize()), MAX_PAGE_SIZE);
        Pageable pageable = PageRequest.of(pageNo, pageSize, Sort.by(Sort.Direction.DESC, "createTime"));

        Specification<FlowMqTaskDO> spec = (root, cq, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (StrUtil.isNotBlank(queryDTO.getDirectoryId())) {
                List<String> dirIds = flowDirectoryService.getAllChildIds(queryDTO.getDirectoryId());
                if (!dirIds.isEmpty()) {
                    predicates.add(root.get("directoryId").in(dirIds));
                } else {
                    predicates.add(cb.equal(root.get("directoryId"), "-1"));
                }
            }
            if (StrUtil.isNotBlank(queryDTO.getName())) {
                predicates.add(cb.like(root.get("name"), "%" + queryDTO.getName() + "%"));
            }
            if (StrUtil.isNotBlank(queryDTO.getConnectionCode())) {
                predicates.add(cb.equal(root.get("connectionCode"), queryDTO.getConnectionCode()));
            }
            if (queryDTO.getEnabled() != null) {
                predicates.add(cb.equal(root.get("enabled"), queryDTO.getEnabled()));
            }
            if (queryDTO.getPublishStatus() != null) {
                predicates.add(cb.equal(root.get("publishStatus"), queryDTO.getPublishStatus()));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };

        Page<FlowMqTaskDO> page = flowMqTaskRepository.findAll(spec, pageable);
        List<FlowMqTaskDTO> items = page.getContent().stream()
                .map(FlowMqTaskDTO::fromDO)
                .map(FlowMqTaskDTO::stripHeavyFields)
                .collect(Collectors.toList());
        enrichDirectoryNames(items);
        return new PageBean<>(items, page.getNumber(), page.getSize(),
                page.getTotalPages(), page.getTotalElements());
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 启用 / 停用
    // ─────────────────────────────────────────────────────────────────────────

    @Override
    @Transactional
    public FlowMqTaskDO enable(String id) {
        demoModeGuard.checkModifyOrDelete(id, DEMO_TARGET);
        FlowMqTaskDO task = requireTask(id);
        task.setEnabled(true);
        task.setUpdateTime(LocalDateTime.now());
        FlowMqTaskDO saved = flowMqTaskRepository.save(task);
        if (isSubscribable(saved)) {
            mqConsumerManager.subscribe(saved);
        } else {
            mqConsumerManager.cancel(id);
        }
        return saved;
    }

    @Override
    @Transactional
    public FlowMqTaskDO disable(String id) {
        demoModeGuard.checkModifyOrDelete(id, DEMO_TARGET);
        FlowMqTaskDO task = requireTask(id);
        task.setEnabled(false);
        task.setUpdateTime(LocalDateTime.now());
        FlowMqTaskDO saved = flowMqTaskRepository.save(task);
        mqConsumerManager.cancel(id);
        return saved;
    }

    @Override
    @Transactional
    public FlowMqTaskDO updateLogEnabled(String id, boolean logEnabled) {
        demoModeGuard.checkModifyOrDelete(id, DEMO_TARGET);
        FlowMqTaskDO task = requireTask(id);
        task.setLogEnabled(logEnabled);
        task.setUpdateTime(LocalDateTime.now());
        return flowMqTaskRepository.save(task);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 发布 / 下线 / 回滚草稿
    // ─────────────────────────────────────────────────────────────────────────

    @Override
    @Transactional(rollbackFor = Exception.class)
    public FlowMqTaskDO publish(String id) {
        demoModeGuard.checkModifyOrDelete(id, DEMO_TARGET);
        FlowMqTaskDO task = requireTask(id);
        if (StrUtil.isBlank(task.getDslContent())) {
            throw new RuntimeException("任务 DSL 为空，无法发布");
        }
        validateSubscribeConfig(task.getConnectionCode(), task.getTopic());
        String snapshot = buildSnapshot(task);
        LocalDateTime now = LocalDateTime.now();
        task.setPublishedSnapshot(snapshot);
        task.setPublishStatus(1);
        task.setPublishTime(now);
        task.setUpdateTime(now);
        FlowMqTaskDO saved = flowMqTaskRepository.save(task);
        if (isSubscribable(saved)) {
            mqConsumerManager.resubscribe(saved);
        }
        return saved;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public FlowMqTaskDO unpublish(String id) {
        demoModeGuard.checkModifyOrDelete(id, DEMO_TARGET);
        FlowMqTaskDO task = requireTask(id);
        task.setPublishStatus(0);
        task.setPublishedSnapshot(null);
        task.setUpdateTime(LocalDateTime.now());
        FlowMqTaskDO saved = flowMqTaskRepository.save(task);
        mqConsumerManager.cancel(id);
        return saved;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public FlowMqTaskDO rollbackToPublished(String id) {
        demoModeGuard.checkModifyOrDelete(id, DEMO_TARGET);
        FlowMqTaskDO task = requireTask(id);
        if (StrUtil.isBlank(task.getPublishedSnapshot())) {
            throw new RuntimeException("该任务没有发布快照，无法回滚");
        }
        applySnapshotToDraft(task, task.getPublishedSnapshot());
        if (task.getPublishTime() != null) {
            task.setUpdateTime(task.getPublishTime());
        } else {
            task.setUpdateTime(LocalDateTime.now());
        }
        FlowMqTaskDO saved = flowMqTaskRepository.save(task);
        if (isSubscribable(saved)) {
            mqConsumerManager.resubscribe(saved);
        } else {
            mqConsumerManager.cancel(id);
        }
        return saved;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 工具方法
    // ─────────────────────────────────────────────────────────────────────────

    private static boolean isSubscribable(FlowMqTaskDO task) {
        return task != null
                && Boolean.TRUE.equals(task.getEnabled())
                && task.getPublishStatus() != null
                && task.getPublishStatus() == 1
                && StrUtil.isNotBlank(task.getPublishedSnapshot());
    }

    /** 完整快照：DSL + 订阅配置/启停/日志/基础信息 */
    private String buildSnapshot(FlowMqTaskDO task) {
        try {
            ObjectNode snap = SNAPSHOT_MAPPER.createObjectNode();
            snap.put("name", task.getName());
            snap.put("connectionCode", task.getConnectionCode());
            snap.put("topic", task.getTopic());
            snap.put("consumerGroup", task.getConsumerGroup());
            snap.put("concurrency", task.getConcurrency() != null ? task.getConcurrency() : 1);
            snap.put("dslContent", task.getDslContent());
            snap.put("info", task.getInfo());
            snap.put("tags", task.getTags());
            if (task.getEnabled() != null) {
                snap.put("enabled", task.getEnabled());
            }
            if (task.getLogEnabled() != null) {
                snap.put("logEnabled", task.getLogEnabled());
            }
            if (StrUtil.isNotBlank(task.getLogMode())) {
                snap.put("logMode", task.getLogMode());
            }
            if (StrUtil.isNotBlank(task.getLogPayloadMode())) {
                snap.put("logPayloadMode", task.getLogPayloadMode());
            }
            if (task.getRetryMax() != null) {
                snap.put("retryMax", task.getRetryMax());
            }
            if (task.getRetryBackoffMs() != null) {
                snap.put("retryBackoffMs", task.getRetryBackoffMs());
            }
            if (StrUtil.isNotBlank(task.getDeadLetterTopic())) {
                snap.put("deadLetterTopic", task.getDeadLetterTopic());
            }
            return SNAPSHOT_MAPPER.writeValueAsString(snap);
        } catch (Exception e) {
            throw new RuntimeException("生成发布快照失败", e);
        }
    }

    /** 兼容旧快照：缺字段时不覆盖现有值。回滚草稿同时恢复 enabled/logEnabled。 */
    private void applySnapshotToDraft(FlowMqTaskDO task, String snapshotJson) {
        try {
            JsonNode snap = SNAPSHOT_MAPPER.readTree(snapshotJson);
            applyText(snap, "name", task::setName);
            applyText(snap, "connectionCode", task::setConnectionCode);
            applyText(snap, "topic", task::setTopic);
            applyText(snap, "consumerGroup", task::setConsumerGroup);
            if (snap.has("concurrency") && !snap.get("concurrency").isNull()) {
                task.setConcurrency(Math.max(1, snap.get("concurrency").asInt(1)));
            }
            applyText(snap, "dslContent", task::setDslContent);
            applyText(snap, "info", task::setInfo);
            applyText(snap, "tags", task::setTags);
            if (snap.has("enabled") && !snap.get("enabled").isNull()) {
                task.setEnabled(snap.get("enabled").asBoolean());
            }
            if (snap.has("logEnabled") && !snap.get("logEnabled").isNull()) {
                task.setLogEnabled(snap.get("logEnabled").asBoolean());
            }
            applyText(snap, "logMode", task::setLogMode);
            applyText(snap, "logPayloadMode", task::setLogPayloadMode);
            if (snap.has("retryMax") && !snap.get("retryMax").isNull()) {
                task.setRetryMax(Math.max(0, snap.get("retryMax").asInt(0)));
            }
            if (snap.has("retryBackoffMs") && !snap.get("retryBackoffMs").isNull()) {
                task.setRetryBackoffMs(Math.max(0, snap.get("retryBackoffMs").asInt(1000)));
            }
            applyText(snap, "deadLetterTopic", task::setDeadLetterTopic);
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("解析发布快照失败", e);
        }
    }

    private static void validateSubscribeConfig(String connectionCode, String topic) {
        if (StrUtil.isBlank(connectionCode)) {
            throw new RuntimeException("MQ 连接编码不能为空");
        }
        if (StrUtil.isBlank(topic)) {
            throw new RuntimeException("订阅 topic 不能为空");
        }
    }

    private static void applyText(JsonNode snap, String field, java.util.function.Consumer<String> setter) {
        if (!snap.has(field)) {
            return;
        }
        JsonNode node = snap.get(field);
        setter.accept(node == null || node.isNull() ? null : node.asText());
    }

    private FlowMqTaskDO requireTask(String id) {
        return flowMqTaskRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("MQ 任务不存在: " + id));
    }

    private void enrichDirectoryNames(List<FlowMqTaskDTO> dtoList) {
        Set<String> directoryIds = dtoList.stream()
                .map(FlowMqTaskDTO::getDirectoryId)
                .filter(StrUtil::isNotBlank)
                .collect(Collectors.toSet());
        if (directoryIds.isEmpty()) {
            return;
        }
        Map<String, String> dirMap = flowDirectoryRepository.findAllById(directoryIds).stream()
                .collect(Collectors.toMap(FlowDirectoryDO::getId, FlowDirectoryDO::getName));
        dtoList.forEach(dto -> dto.setDirectoryName(dirMap.get(dto.getDirectoryId())));
    }
}
