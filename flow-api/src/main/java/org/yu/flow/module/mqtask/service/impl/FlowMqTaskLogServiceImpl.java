package org.yu.flow.module.mqtask.service.impl;

import cn.hutool.core.util.StrUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.yu.flow.log.support.AbstractLogQueryService;
import org.yu.flow.module.mqtask.domain.FlowMqTaskLogDO;
import org.yu.flow.module.mqtask.dto.FlowMqTaskLogDTO;
import org.yu.flow.module.mqtask.dto.FlowMqTaskLogListDTO;
import org.yu.flow.module.mqtask.query.FlowMqTaskLogQueryDTO;
import org.yu.flow.module.mqtask.repository.FlowMqTaskLogRepository;
import org.yu.flow.module.mqtask.service.FlowMqTaskLogService;

import jakarta.annotation.Resource;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Selection;
import java.util.ArrayList;
import java.util.List;

/**
 * MQ 任务日志服务实现
 *
 * @author yu-flow
 */
@Slf4j
@Service
public class FlowMqTaskLogServiceImpl
        extends AbstractLogQueryService<FlowMqTaskLogDO, FlowMqTaskLogQueryDTO, FlowMqTaskLogListDTO>
        implements FlowMqTaskLogService {

    @Resource
    private FlowMqTaskLogRepository flowMqTaskLogRepository;

    @Override
    public FlowMqTaskLogDO save(FlowMqTaskLogDO logDO) {
        try {
            return flowMqTaskLogRepository.save(logDO);
        } catch (Exception e) {
            log.error("[MqTaskLog] 日志入库失败, taskId={}, status={}, error={}",
                    logDO.getTaskId(), logDO.getStatus(), e.getMessage(), e);
            return logDO;
        }
    }

    @Async("flowAsyncExecutor")
    @Override
    public void saveAsync(FlowMqTaskLogDO logDO) {
        try {
            flowMqTaskLogRepository.save(logDO);
        } catch (Exception e) {
            log.error("[MqTaskLog] 异步入库失败, taskId={}, status={}, error={}",
                    logDO.getTaskId(), logDO.getStatus(), e.getMessage(), e);
        }
    }

    /**
     * 列表分页：只投影摘要字段。
     * <p>用 {@code CASE WHEN … IS NOT NULL} 判断 hasTrace / hasMessageBody，不 SELECT 大字段内容。
     */
    @Override
    public Page<FlowMqTaskLogListDTO> pageList(FlowMqTaskLogQueryDTO query) {
        return pageQuery(query, query.getPage(), query.getSize());
    }

    /** 日志行含 errorMsg 等长文本，限制单页体量避免一次拉爆内存 */
    @Override
    protected int maxPageSize() {
        return 100;
    }

    @Override
    protected Class<FlowMqTaskLogDO> entityClass() {
        return FlowMqTaskLogDO.class;
    }

    @Override
    protected Class<FlowMqTaskLogListDTO> listDtoClass() {
        return FlowMqTaskLogListDTO.class;
    }

    @Override
    protected List<Selection<?>> selections(CriteriaBuilder cb, Root<FlowMqTaskLogDO> root) {
        return List.of(
                root.get("id"),
                root.get("taskId"),
                root.get("taskName"),
                root.get("topic"),
                root.get("messageId"),
                root.get("triggerType"),
                root.get("status"),
                root.get("costTimeMs"),
                root.get("errorMsg"),
                hasTraceExpression(cb, root),
                notNullFlagExpression(cb, root, "messageBody"),
                root.get("createTime"));
    }

    @Override
    protected List<Predicate> buildPredicates(FlowMqTaskLogQueryDTO query,
                                              Root<FlowMqTaskLogDO> root,
                                              CriteriaBuilder cb) {
        List<Predicate> predicates = new ArrayList<>();

        if (StrUtil.isNotBlank(query.getTaskId())) {
            predicates.add(cb.equal(root.get("taskId"), query.getTaskId()));
        }
        if (StrUtil.isNotBlank(query.getTaskName())) {
            predicates.add(cb.like(root.get("taskName"), "%" + query.getTaskName() + "%"));
        }
        if (StrUtil.isNotBlank(query.getTopic())) {
            predicates.add(cb.equal(root.get("topic"), query.getTopic()));
        }
        if (StrUtil.isNotBlank(query.getMessageId())) {
            predicates.add(cb.equal(root.get("messageId"), query.getMessageId()));
        }
        if (StrUtil.isNotBlank(query.getStatus())) {
            predicates.add(cb.equal(root.get("status"), query.getStatus()));
        }
        if (StrUtil.isNotBlank(query.getTriggerType())) {
            predicates.add(cb.equal(root.get("triggerType"), query.getTriggerType()));
        }

        addCreateTimeRange(predicates, root, cb, query.getStartTime(), query.getEndTime(), "[MqTaskLog]");

        return predicates;
    }

    @Override
    public FlowMqTaskLogDTO getById(String id) {
        return flowMqTaskLogRepository.findById(id)
                .map(FlowMqTaskLogDTO::fromDO)
                .orElse(null);
    }

    @Override
    @Transactional
    public void clearByTaskId(String taskId) {
        flowMqTaskLogRepository.deleteByTaskId(taskId);
    }
}
