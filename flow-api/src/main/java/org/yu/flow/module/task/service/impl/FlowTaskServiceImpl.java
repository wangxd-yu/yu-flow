package org.yu.flow.module.task.service.impl;

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
import org.springframework.scheduling.support.CronExpression;
import org.yu.flow.auto.dto.PageBean;
import org.yu.flow.config.DemoModeGuard;
import org.yu.flow.module.assetversion.AssetBizType;
import org.yu.flow.module.assetversion.domain.FlowAssetVersionDO;
import org.yu.flow.module.assetversion.dto.FlowAssetVersionDTO;
import org.yu.flow.module.assetversion.service.FlowAssetVersionService;
import org.yu.flow.module.directory.domain.FlowDirectoryDO;
import org.yu.flow.module.directory.repository.FlowDirectoryRepository;
import org.yu.flow.module.directory.service.FlowDirectoryService;
import org.yu.flow.auto.util.JwtTokenUtil;
import org.yu.flow.module.task.domain.FlowTaskDO;
import org.yu.flow.module.task.dto.FlowTaskDTO;
import org.yu.flow.module.task.query.FlowTaskQueryDTO;
import org.yu.flow.module.task.repository.FlowTaskRepository;
import org.yu.flow.module.task.scheduler.FlowTaskScheduler;
import org.yu.flow.module.task.service.FlowTaskService;
import org.yu.flow.module.assetref.FlowReferenceIndex;
import org.yu.flow.log.audit.service.AuditLogService;

import jakarta.annotation.Resource;
import jakarta.persistence.criteria.Predicate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 定时任务业务服务实现
 *
 * @author yu-flow
 */
@Slf4j
@Service
public class FlowTaskServiceImpl implements FlowTaskService {

    private static final ObjectMapper SNAPSHOT_MAPPER = org.yu.flow.util.FlowObjectMapperUtil.flowObjectMapper();

    @Resource
    private FlowTaskRepository flowTaskRepository;

    @Resource
    private FlowTaskScheduler flowTaskScheduler;

    @Resource
    private FlowDirectoryService flowDirectoryService;

    @Resource
    private FlowDirectoryRepository flowDirectoryRepository;

    @Resource
    private DemoModeGuard demoModeGuard;

    @Resource
    private FlowAssetVersionService flowAssetVersionService;

    @Resource
    private FlowReferenceIndex flowReferenceIndex;

    @Resource
    private AuditLogService auditLogService;

    @Resource
    private org.yu.flow.module.release.service.PublishGateService publishGateService;

    private void notifyRefIndex() {
        flowReferenceIndex.scheduleRebuildBroadcastAfterCommit();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // CRUD
    // ─────────────────────────────────────────────────────────────────────────

    @Override
    @Transactional
    public FlowTaskDO save(FlowTaskDO taskDO) {
        validateCronExpression(taskDO.getCron());
        flowDirectoryService.assertDirectoryBizType(taskDO.getDirectoryId(), "task");
        if (taskDO.getEnabled() == null) taskDO.setEnabled(true);
        if (taskDO.getLogEnabled() == null) taskDO.setLogEnabled(false);
        if (taskDO.getPublishStatus() == null) taskDO.setPublishStatus(0);
        if (taskDO.getDeleted() == null) taskDO.setDeleted(0);
        LocalDateTime now = LocalDateTime.now();
        taskDO.setCreateTime(now);
        taskDO.setUpdateTime(now);
        FlowTaskDO saved = flowTaskRepository.save(taskDO);
        // 仅「已启用且已发布」才注册调度
        if (isSchedulable(saved)) {
            flowTaskScheduler.schedule(saved);
        }
        notifyRefIndex();
        return saved;
    }

    @Override
    @Transactional
    public FlowTaskDO update(FlowTaskDO taskDO) {
        demoModeGuard.checkModifyOrDelete(taskDO.getId(), "定时任务");
        FlowTaskDO existing = flowTaskRepository.findById(taskDO.getId())
                .orElseThrow(() -> new RuntimeException("任务不存在: " + taskDO.getId()));

        boolean wasSchedulable = isSchedulable(existing);

        if (taskDO.getName() != null) existing.setName(taskDO.getName());
        if (taskDO.getCron() != null) {
            validateCronExpression(taskDO.getCron());
            existing.setCron(taskDO.getCron());
        }
        if (taskDO.getEnabled() != null) existing.setEnabled(taskDO.getEnabled());
        if (taskDO.getLogEnabled() != null) existing.setLogEnabled(taskDO.getLogEnabled());
        if (taskDO.getDslContent() != null) existing.setDslContent(taskDO.getDslContent());
        if (taskDO.getInfo() != null) existing.setInfo(taskDO.getInfo());
        if (taskDO.getTags() != null) existing.setTags(taskDO.getTags());
        if (taskDO.getDirectoryId() != null) {
            flowDirectoryService.assertDirectoryBizType(
                    StrUtil.isBlank(taskDO.getDirectoryId()) ? null : taskDO.getDirectoryId(), "task");
            existing.setDirectoryId(taskDO.getDirectoryId());
        }
        existing.setUpdateTime(LocalDateTime.now());

        FlowTaskDO updated = flowTaskRepository.save(existing);
        // 草稿改 Cron/DSL 不影响线上触发器；仅启停导致可调度性变化时调整调度。
        // 发布侧 Cron 变更由 publish / republish / restore / rollback 负责 reschedule。
        boolean nowSchedulable = isSchedulable(updated);
        if (!nowSchedulable) {
            if (wasSchedulable) {
                flowTaskScheduler.cancel(updated.getId());
            }
        } else if (!wasSchedulable) {
            flowTaskScheduler.schedule(updated);
        }
        notifyRefIndex();
        return updated;
    }

    @Override
    @Transactional
    public void delete(String id) {
        demoModeGuard.checkModifyOrDelete(id, "定时任务");
        flowTaskScheduler.cancel(id);
        flowTaskRepository.deleteById(id);
        notifyRefIndex();
    }

    @Override
    @Transactional
    public void batchDelete(List<String> ids) {
        if (ids == null || ids.isEmpty()) {
            return;
        }
        ids.forEach(id -> demoModeGuard.checkModifyOrDelete(id, "定时任务"));
        ids.forEach(flowTaskScheduler::cancel);
        flowTaskRepository.logicDeleteByIds(ids);
        notifyRefIndex();
    }

    @Override
    public FlowTaskDO findById(String id) {
        return flowTaskRepository.findById(id).orElse(null);
    }

    @Override
    public PageBean<FlowTaskDTO> findPage(FlowTaskQueryDTO queryDTO) {
        Pageable pageable = PageRequest.of(
                queryDTO.getPage(), queryDTO.getSize(),
                Sort.by(Sort.Direction.DESC, "createTime")
        );

        Specification<FlowTaskDO> spec = (root, cq, cb) -> {
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
            if (queryDTO.getEnabled() != null) {
                predicates.add(cb.equal(root.get("enabled"), queryDTO.getEnabled()));
            }
            if (queryDTO.getPublishStatus() != null) {
                predicates.add(cb.equal(root.get("publishStatus"), queryDTO.getPublishStatus()));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };

        Page<FlowTaskDO> page = flowTaskRepository.findAll(spec, pageable);
        List<FlowTaskDTO> items = page.getContent().stream()
                .map(FlowTaskDTO::fromDO)
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
    public FlowTaskDO enable(String id) {
        demoModeGuard.checkModifyOrDelete(id, "定时任务");
        FlowTaskDO task = requireTask(id);
        task.setEnabled(true);
        task.setUpdateTime(LocalDateTime.now());
        FlowTaskDO saved = flowTaskRepository.save(task);
        if (isSchedulable(saved)) {
            flowTaskScheduler.schedule(saved);
        } else {
            flowTaskScheduler.cancel(id);
        }
        return saved;
    }

    @Override
    @Transactional
    public FlowTaskDO disable(String id) {
        demoModeGuard.checkModifyOrDelete(id, "定时任务");
        FlowTaskDO task = requireTask(id);
        task.setEnabled(false);
        task.setUpdateTime(LocalDateTime.now());
        FlowTaskDO saved = flowTaskRepository.save(task);
        flowTaskScheduler.cancel(id);
        return saved;
    }

    @Override
    @Transactional
    public FlowTaskDO updateLogEnabled(String id, boolean logEnabled) {
        FlowTaskDO task = requireTask(id);
        task.setLogEnabled(logEnabled);
        task.setUpdateTime(LocalDateTime.now());
        return flowTaskRepository.save(task);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public FlowTaskDO publish(String id) {
        return publish(id, "DEV");
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public FlowTaskDO publish(String id, String envCode) {
        demoModeGuard.checkModifyOrDelete(id, "定时任务");
        publishGateService.assertCanPublish("TASK", id, envCode);
        FlowTaskDO task = requireTask(id);
        if (StrUtil.isBlank(task.getDslContent())) {
            throw new RuntimeException("任务 DSL 为空，无法发布");
        }
        validateCronExpression(task.getCron());
        String snapshot = buildSnapshot(task);
        LocalDateTime now = LocalDateTime.now();
        task.setPublishedSnapshot(snapshot);
        task.setPublishStatus(1);
        task.setPublishTime(now);
        task.setUpdateTime(now);
        FlowTaskDO saved = flowTaskRepository.save(task);
        flowAssetVersionService.append(AssetBizType.TASK, id, snapshot, AssetBizType.SOURCE_PUBLISH, null, JwtTokenUtil.currentUsername());
        if (isSchedulable(saved)) {
            flowTaskScheduler.reschedule(saved);
        }
        notifyRefIndex();
        String env = org.yu.flow.module.release.support.RegressionSecurity.normalizeEnvCode(envCode);
        auditLogService.record("TASK_PUBLISH", "TASK", id,
                "{\"name\":\"" + StrUtil.nullToEmpty(saved.getName())
                        + "\",\"cron\":\"" + StrUtil.nullToEmpty(saved.getCron())
                        + "\",\"env\":\"" + env + "\"}");
        return saved;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public FlowTaskDO unpublish(String id) {
        demoModeGuard.checkModifyOrDelete(id, "定时任务");
        FlowTaskDO task = requireTask(id);
        task.setPublishStatus(0);
        task.setPublishedSnapshot(null);
        task.setUpdateTime(LocalDateTime.now());
        FlowTaskDO saved = flowTaskRepository.save(task);
        flowTaskScheduler.cancel(id);
        notifyRefIndex();
        return saved;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public FlowTaskDO rollbackToPublished(String id) {
        demoModeGuard.checkModifyOrDelete(id, "定时任务");
        FlowTaskDO task = requireTask(id);
        if (StrUtil.isBlank(task.getPublishedSnapshot())) {
            throw new RuntimeException("该任务没有发布快照，无法回滚");
        }
        applySnapshotToDraft(task, task.getPublishedSnapshot(), true);
        if (task.getPublishTime() != null) {
            task.setUpdateTime(task.getPublishTime());
        } else {
            task.setUpdateTime(LocalDateTime.now());
        }
        FlowTaskDO saved = flowTaskRepository.save(task);
        if (isSchedulable(saved)) {
            flowTaskScheduler.reschedule(saved);
        } else {
            flowTaskScheduler.cancel(id);
        }
        notifyRefIndex();
        return saved;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public FlowTaskDO republish(String id) {
        return publish(id);
    }

    @Override
    @Transactional(readOnly = true)
    public List<FlowAssetVersionDTO> listVersions(String id) {
        FlowTaskDO task = requireTask(id);
        return flowAssetVersionService.list(AssetBizType.TASK, id, task.getPublishedSnapshot());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public FlowTaskDO restoreVersion(String id, String versionId) {
        demoModeGuard.checkModifyOrDelete(id, "定时任务");
        FlowTaskDO task = requireTask(id);
        FlowAssetVersionDO version = flowAssetVersionService.requireOwned(versionId, AssetBizType.TASK, id);
        // 恢复内容字段；不覆盖当前启用/日志开关，避免历史版本意外重新调度
        Boolean keepEnabled = task.getEnabled();
        Boolean keepLogEnabled = task.getLogEnabled();
        LocalDateTime now = LocalDateTime.now();
        applySnapshotToDraft(task, version.getSnapshot(), false);
        task.setEnabled(keepEnabled);
        task.setLogEnabled(keepLogEnabled);
        task.setPublishedSnapshot(version.getSnapshot());
        task.setPublishStatus(1);
        task.setPublishTime(now);
        task.setUpdateTime(now);
        FlowTaskDO saved = flowTaskRepository.save(task);
        flowAssetVersionService.append(
                AssetBizType.TASK, id, version.getSnapshot(), AssetBizType.SOURCE_ROLLBACK,
                "回退至 v" + version.getVersionNo(), JwtTokenUtil.currentUsername());
        if (isSchedulable(saved)) {
            flowTaskScheduler.reschedule(saved);
        } else {
            flowTaskScheduler.cancel(id);
        }
        notifyRefIndex();
        return saved;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 工具方法
    // ─────────────────────────────────────────────────────────────────────────

    private static boolean isSchedulable(FlowTaskDO task) {
        return task != null
                && Boolean.TRUE.equals(task.getEnabled())
                && task.getPublishStatus() != null
                && task.getPublishStatus() == 1
                && StrUtil.isNotBlank(task.getPublishedSnapshot());
    }

    /** 完整快照：DSL + Cron/启停/日志/基础信息 */
    private String buildSnapshot(FlowTaskDO task) {
        try {
            ObjectNode snap = SNAPSHOT_MAPPER.createObjectNode();
            snap.put("name", task.getName());
            snap.put("cron", task.getCron());
            snap.put("dslContent", task.getDslContent());
            snap.put("info", task.getInfo());
            snap.put("tags", task.getTags());
            if (task.getEnabled() != null) {
                snap.put("enabled", task.getEnabled());
            }
            if (task.getLogEnabled() != null) {
                snap.put("logEnabled", task.getLogEnabled());
            }
            return SNAPSHOT_MAPPER.writeValueAsString(snap);
        } catch (Exception e) {
            throw new RuntimeException("生成发布快照失败", e);
        }
    }

    /** 兼容旧快照：缺字段时不覆盖现有值。{@code applyOpsFields} 为 true 时同时恢复 enabled/logEnabled。 */
    private void applySnapshotToDraft(FlowTaskDO task, String snapshotJson, boolean applyOpsFields) {
        try {
            JsonNode snap = SNAPSHOT_MAPPER.readTree(snapshotJson);
            applyText(snap, "name", task::setName);
            applyText(snap, "cron", task::setCron);
            applyText(snap, "dslContent", task::setDslContent);
            applyText(snap, "info", task::setInfo);
            applyText(snap, "tags", task::setTags);
            if (applyOpsFields) {
                if (snap.has("enabled") && !snap.get("enabled").isNull()) {
                    task.setEnabled(snap.get("enabled").asBoolean());
                }
                if (snap.has("logEnabled") && !snap.get("logEnabled").isNull()) {
                    task.setLogEnabled(snap.get("logEnabled").asBoolean());
                }
            }
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("解析历史版本快照失败", e);
        }
    }

    /** Spring 6 字段 Cron（秒 分 时 日 月 周）语法校验 */
    private static void validateCronExpression(String cron) {
        if (StrUtil.isBlank(cron)) {
            throw new RuntimeException("Cron 表达式不能为空");
        }
        if (!CronExpression.isValidExpression(cron.trim())) {
            throw new RuntimeException("Cron 表达式不合法，请使用 Spring 6 字段格式（秒 分 时 日 月 周），例如：0 0/5 * * * ?");
        }
    }

    private static void applyText(JsonNode snap, String field, java.util.function.Consumer<String> setter) {
        if (!snap.has(field)) {
            return;
        }
        JsonNode node = snap.get(field);
        setter.accept(node == null || node.isNull() ? null : node.asText());
    }

    private static String getSnapText(JsonNode snap, String field) {
        JsonNode node = snap.get(field);
        return node != null && !node.isNull() ? node.asText() : null;
    }

    private FlowTaskDO requireTask(String id) {
        return flowTaskRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("任务不存在: " + id));
    }

    private void enrichDirectoryNames(List<FlowTaskDTO> dtoList) {
        Set<String> directoryIds = dtoList.stream()
                .map(FlowTaskDTO::getDirectoryId)
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
