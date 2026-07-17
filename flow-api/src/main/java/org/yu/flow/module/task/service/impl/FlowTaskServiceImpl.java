package org.yu.flow.module.task.service.impl;

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
import org.yu.flow.module.directory.domain.FlowDirectoryDO;
import org.yu.flow.module.directory.repository.FlowDirectoryRepository;
import org.yu.flow.module.directory.service.FlowDirectoryService;
import org.yu.flow.module.task.domain.FlowTaskDO;
import org.yu.flow.module.task.dto.FlowTaskDTO;
import org.yu.flow.module.task.query.FlowTaskQueryDTO;
import org.yu.flow.module.task.repository.FlowTaskRepository;
import org.yu.flow.module.task.scheduler.FlowTaskScheduler;
import org.yu.flow.module.task.service.FlowTaskService;

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

    // ─────────────────────────────────────────────────────────────────────────
    // CRUD
    // ─────────────────────────────────────────────────────────────────────────

    @Override
    @Transactional
    public FlowTaskDO save(FlowTaskDO taskDO) {
        if (taskDO.getEnabled() == null) taskDO.setEnabled(true);
        if (taskDO.getLogEnabled() == null) taskDO.setLogEnabled(true);
        if (taskDO.getDeleted() == null) taskDO.setDeleted(0);
        LocalDateTime now = LocalDateTime.now();
        taskDO.setCreateTime(now);
        taskDO.setUpdateTime(now);
        FlowTaskDO saved = flowTaskRepository.save(taskDO);
        // 如果启用状态，自动注册调度
        if (Boolean.TRUE.equals(saved.getEnabled())) {
            flowTaskScheduler.schedule(saved);
        }
        return saved;
    }

    @Override
    @Transactional
    public FlowTaskDO update(FlowTaskDO taskDO) {
        demoModeGuard.checkModifyOrDelete(taskDO.getId(), "定时任务");
        FlowTaskDO existing = flowTaskRepository.findById(taskDO.getId())
                .orElseThrow(() -> new RuntimeException("任务不存在: " + taskDO.getId()));

        if (taskDO.getName() != null) existing.setName(taskDO.getName());
        if (taskDO.getCron() != null) existing.setCron(taskDO.getCron());
        if (taskDO.getEnabled() != null) existing.setEnabled(taskDO.getEnabled());
        if (taskDO.getLogEnabled() != null) existing.setLogEnabled(taskDO.getLogEnabled());
        if (taskDO.getDslContent() != null) existing.setDslContent(taskDO.getDslContent());
        if (taskDO.getInfo() != null) existing.setInfo(taskDO.getInfo());
        if (taskDO.getTags() != null) existing.setTags(taskDO.getTags());
        if (taskDO.getDirectoryId() != null) existing.setDirectoryId(taskDO.getDirectoryId());
        existing.setUpdateTime(LocalDateTime.now());

        FlowTaskDO updated = flowTaskRepository.save(existing);
        // Cron 或启用状态变化时重新调度
        flowTaskScheduler.reschedule(updated);
        return updated;
    }

    @Override
    @Transactional
    public void delete(String id) {
        demoModeGuard.checkModifyOrDelete(id, "定时任务");
        flowTaskScheduler.cancel(id);
        flowTaskRepository.deleteById(id);
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
        flowTaskScheduler.schedule(saved);
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

    // ─────────────────────────────────────────────────────────────────────────
    // 工具方法
    // ─────────────────────────────────────────────────────────────────────────

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
