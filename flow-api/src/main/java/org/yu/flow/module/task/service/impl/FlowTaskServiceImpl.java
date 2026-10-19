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
import org.yu.flow.dto.PageBean;
import org.yu.flow.config.DemoModeGuard;
import org.yu.flow.module.assetversion.AssetBizType;
import org.yu.flow.module.assetversion.domain.FlowAssetVersionDO;
import org.yu.flow.module.assetversion.dto.FlowAssetVersionDTO;
import org.yu.flow.module.assetversion.service.PublishableAssetService;
import org.yu.flow.module.directory.domain.FlowDirectoryDO;
import org.yu.flow.module.directory.repository.FlowDirectoryRepository;
import org.yu.flow.module.directory.service.FlowDirectoryService;
import org.yu.flow.util.JwtTokenUtil;
import org.yu.flow.module.task.domain.FlowTaskDO;
import org.yu.flow.module.task.dto.FlowTaskDTO;
import org.yu.flow.module.task.query.FlowTaskQueryDTO;
import org.yu.flow.module.task.repository.FlowTaskRepository;
import org.yu.flow.module.task.scheduler.FlowTaskScheduler;
import org.yu.flow.module.task.service.FlowTaskService;
import org.yu.flow.module.assetref.FlowReferenceIndex;
import org.yu.flow.module.log.audit.service.AuditLogService;

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
public class FlowTaskServiceImpl extends PublishableAssetService<FlowTaskDO> implements FlowTaskService {

    private static final ObjectMapper SNAPSHOT_MAPPER = org.yu.flow.util.FlowObjectMapperUtil.flowObjectMapper();

    @Resource
    private FlowTaskRepository flowTaskRepository;

    @Resource
    private FlowTaskScheduler flowTaskScheduler;

    @Resource
    private FlowDirectoryService flowDirectoryService;

    @Resource
    private FlowDirectoryRepository flowDirectoryRepository;


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
        if (StrUtil.isBlank(taskDO.getLogMode())) taskDO.setLogMode("SYSTEM_DEFAULT");
        // 保留天数 <0 视为未配置（跟随系统）
        if (taskDO.getLogRetentionDays() != null && taskDO.getLogRetentionDays() < 0) taskDO.setLogRetentionDays(null);
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
        if (taskDO.getLogMode() != null) existing.setLogMode(taskDO.getLogMode());
        if (StrUtil.isBlank(existing.getLogMode())) existing.setLogMode("SYSTEM_DEFAULT");
        // 保留天数：-1=清除任务级配置（回退系统），0=永久保留，>0=自定义天数
        if (taskDO.getLogRetentionDays() != null) {
            existing.setLogRetentionDays(taskDO.getLogRetentionDays() < 0 ? null : taskDO.getLogRetentionDays());
        }
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
    public FlowTaskDO republish(String id) {
        return publish(id);
    }

    // ─── 发布生命周期（见 PublishableAssetService）─────────────────────────

    @Override
    protected String assetType() {
        return "TASK";
    }

    @Override
    protected String versionBizType() {
        return AssetBizType.TASK;
    }

    @Override
    protected String demoTarget() {
        return "定时任务";
    }

    @Override
    protected String noSnapshotMessage() {
        return "该任务没有发布快照，无法回滚";
    }

    @Override
    protected FlowTaskDO require(String id) {
        return requireTask(id);
    }

    @Override
    protected FlowTaskDO persist(FlowTaskDO task) {
        return flowTaskRepository.save(task);
    }

    @Override
    protected void checkPublishable(FlowTaskDO task) {
        if (StrUtil.isBlank(task.getDslContent())) {
            throw new RuntimeException("任务 DSL 为空，无法发布");
        }
        validateCronExpression(task.getCron());
    }

    /** 回滚草稿连同启停 / 日志开关一起恢复 */
    @Override
    protected void applySnapshotToDraft(FlowTaskDO task, String snapshot) {
        applySnapshotToDraft(task, snapshot, true);
    }

    /** 回退历史版本只恢复内容，启停 / 日志开关保持当前值，避免历史版本意外重新调度 */
    @Override
    protected void applyVersionToDraft(FlowTaskDO task, String snapshot) {
        applySnapshotToDraft(task, snapshot, false);
    }

    @Override
    protected String publishedSnapshot(FlowTaskDO task) {
        return task.getPublishedSnapshot();
    }

    @Override
    protected LocalDateTime publishTime(FlowTaskDO task) {
        return task.getPublishTime();
    }

    @Override
    protected void setPublishState(FlowTaskDO task, Integer status, String snapshot) {
        task.setPublishStatus(status);
        task.setPublishedSnapshot(snapshot);
    }

    @Override
    protected void setPublishTime(FlowTaskDO task, LocalDateTime time) {
        task.setPublishTime(time);
    }

    @Override
    protected void setUpdateTime(FlowTaskDO task, LocalDateTime time) {
        task.setUpdateTime(time);
    }

    @Override
    protected String publishAuditDetail(FlowTaskDO saved, String env) {
        return "{\"name\":\"" + StrUtil.nullToEmpty(saved.getName())
                + "\",\"cron\":\"" + StrUtil.nullToEmpty(saved.getCron())
                + "\",\"env\":\"" + env + "\"}";
    }

    @Override
    protected void afterPublished(FlowTaskDO saved) {
        if (isSchedulable(saved)) {
            flowTaskScheduler.reschedule(saved);
        }
    }

    @Override
    protected void afterUnpublished(FlowTaskDO saved) {
        flowTaskScheduler.cancel(saved.getId());
    }

    @Override
    protected void afterRolledBack(FlowTaskDO saved) {
        refreshSchedule(saved);
    }

    @Override
    protected void afterRestored(FlowTaskDO saved) {
        refreshSchedule(saved);
    }

    private void refreshSchedule(FlowTaskDO task) {
        if (isSchedulable(task)) {
            flowTaskScheduler.reschedule(task);
        } else {
            flowTaskScheduler.cancel(task.getId());
        }
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
    @Override
    protected String buildSnapshot(FlowTaskDO task) {
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
