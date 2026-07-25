package org.yu.flow.log.task.service.impl;

import cn.hutool.core.util.StrUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.yu.flow.log.support.AbstractLogQueryService;
import org.yu.flow.log.task.domain.FlowTaskLogDO;
import org.yu.flow.log.task.dto.FlowTaskLogDTO;
import org.yu.flow.log.task.dto.FlowTaskLogListDTO;
import org.yu.flow.log.task.query.FlowTaskLogQueryDTO;
import org.yu.flow.log.task.repository.FlowTaskLogRepository;
import org.yu.flow.log.task.service.FlowTaskLogService;

import jakarta.annotation.Resource;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Selection;
import java.util.ArrayList;
import java.util.List;

/**
 * 任务日志服务实现
 *
 * @author yu-flow
 */
@Slf4j
@Service
public class FlowTaskLogServiceImpl
        extends AbstractLogQueryService<FlowTaskLogDO, FlowTaskLogQueryDTO, FlowTaskLogListDTO>
        implements FlowTaskLogService {

    @Resource
    private FlowTaskLogRepository flowTaskLogRepository;

    @Override
    public FlowTaskLogDO save(FlowTaskLogDO logDO) {
        try {
            return flowTaskLogRepository.save(logDO);
        } catch (Exception e) {
            log.error("[TaskLog] 日志入库失败, taskId={}, status={}, error={}",
                    logDO.getTaskId(), logDO.getStatus(), e.getMessage(), e);
            return logDO;
        }
    }

    @Async("flowAsyncExecutor")
    @Override
    public void saveAsync(FlowTaskLogDO logDO) {
        try {
            flowTaskLogRepository.save(logDO);
        } catch (Exception e) {
            log.error("[TaskLog] 异步入库失败, taskId={}, status={}, error={}",
                    logDO.getTaskId(), logDO.getStatus(), e.getMessage(), e);
        }
    }

    /**
     * 列表分页：只投影摘要字段。
     * <p>用 {@code CASE WHEN traceData IS NOT NULL} 判断 hasTrace，不 SELECT 大字段内容。
     * HighGo/PG 下 IS NOT NULL 不会展开 TOAST/LONGTEXT。
     */
    @Override
    public Page<FlowTaskLogListDTO> pageList(FlowTaskLogQueryDTO query) {
        return pageQuery(query, query.getPage(), query.getSize());
    }

    @Override
    protected Class<FlowTaskLogDO> entityClass() {
        return FlowTaskLogDO.class;
    }

    @Override
    protected Class<FlowTaskLogListDTO> listDtoClass() {
        return FlowTaskLogListDTO.class;
    }

    @Override
    protected List<Selection<?>> selections(CriteriaBuilder cb, Root<FlowTaskLogDO> root) {
        return List.of(
                root.get("id"),
                root.get("taskId"),
                root.get("taskName"),
                root.get("triggerType"),
                root.get("status"),
                root.get("costTimeMs"),
                hasTraceExpression(cb, root),
                root.get("createTime"));
    }

    @Override
    protected List<Predicate> buildPredicates(FlowTaskLogQueryDTO query,
                                              Root<FlowTaskLogDO> root,
                                              CriteriaBuilder cb) {
        List<Predicate> predicates = new ArrayList<>();

        if (StrUtil.isNotBlank(query.getTaskId())) {
            predicates.add(cb.equal(root.get("taskId"), query.getTaskId()));
        }
        if (StrUtil.isNotBlank(query.getTaskName())) {
            predicates.add(cb.like(root.get("taskName"), "%" + query.getTaskName() + "%"));
        }
        if (StrUtil.isNotBlank(query.getStatus())) {
            predicates.add(cb.equal(root.get("status"), query.getStatus()));
        }
        if (StrUtil.isNotBlank(query.getTriggerType())) {
            predicates.add(cb.equal(root.get("triggerType"), query.getTriggerType()));
        }

        addCreateTimeRange(predicates, root, cb, query.getStartTime(), query.getEndTime(), "[TaskLog]");

        return predicates;
    }

    @Override
    public FlowTaskLogDTO getById(String id) {
        return flowTaskLogRepository.findById(id)
                .map(FlowTaskLogDTO::fromDO)
                .orElse(null);
    }

    @Override
    @Transactional
    public void clearByTaskId(String taskId) {
        flowTaskLogRepository.deleteByTaskId(taskId);
    }
}
