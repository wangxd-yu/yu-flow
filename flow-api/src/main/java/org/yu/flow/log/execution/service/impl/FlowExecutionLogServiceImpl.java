package org.yu.flow.log.execution.service.impl;

import cn.hutool.core.util.StrUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.yu.flow.log.execution.domain.FlowExecutionLogDO;
import org.yu.flow.log.execution.dto.FlowExecutionLogDTO;
import org.yu.flow.log.execution.dto.FlowExecutionLogListDTO;
import org.yu.flow.log.execution.query.FlowExecutionLogQueryDTO;
import org.yu.flow.log.execution.repository.FlowExecutionLogRepository;
import org.yu.flow.log.execution.service.FlowExecutionLogService;
import org.yu.flow.log.support.AbstractLogQueryService;

import jakarta.annotation.Resource;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Selection;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
public class FlowExecutionLogServiceImpl
        extends AbstractLogQueryService<FlowExecutionLogDO, FlowExecutionLogQueryDTO, FlowExecutionLogListDTO>
        implements FlowExecutionLogService {

    @Resource
    private FlowExecutionLogRepository flowExecutionLogRepository;

    @Async("flowAsyncExecutor")
    @Override
    public void saveLogAsync(FlowExecutionLogDO logDO) {
        try {
            flowExecutionLogRepository.save(logDO);
        } catch (Exception e) {
            log.error("[ExecutionLog] 异步入库失败, apiUrl={}, status={}, error={}",
                    logDO.getUrl(), logDO.getStatus(), e.getMessage(), e);
        }
    }

    /**
     * 列表分页：只投影摘要字段。
     * <p>用 {@code CASE WHEN traceData IS NOT NULL} 判断 hasTrace，不加载 LOB 内容。
     */
    @Override
    public Page<FlowExecutionLogListDTO> pageList(FlowExecutionLogQueryDTO query) {
        return pageQuery(query, query.getPage(), query.getSize());
    }

    @Override
    protected Class<FlowExecutionLogDO> entityClass() {
        return FlowExecutionLogDO.class;
    }

    @Override
    protected Class<FlowExecutionLogListDTO> listDtoClass() {
        return FlowExecutionLogListDTO.class;
    }

    @Override
    protected List<Selection<?>> selections(CriteriaBuilder cb, Root<FlowExecutionLogDO> root) {
        return List.of(
                root.get("id"),
                root.get("apiId"),
                root.get("apiName"),
                root.get("url"),
                root.get("serviceType"),
                root.get("method"),
                root.get("status"),
                root.get("costTimeMs"),
                hasTraceExpression(cb, root),
                root.get("createTime"));
    }

    @Override
    protected List<Predicate> buildPredicates(FlowExecutionLogQueryDTO query,
                                              Root<FlowExecutionLogDO> root,
                                              CriteriaBuilder cb) {
        List<Predicate> predicates = new ArrayList<>();

        if (StrUtil.isNotBlank(query.getApiId())) {
            predicates.add(cb.equal(root.get("apiId"), query.getApiId()));
        }
        if (StrUtil.isNotBlank(query.getApiName())) {
            predicates.add(cb.like(root.get("apiName"), "%" + query.getApiName() + "%"));
        }
        if (StrUtil.isNotBlank(query.getStatus())) {
            predicates.add(cb.equal(root.get("status"), query.getStatus()));
        }
        if (StrUtil.isNotBlank(query.getMethod())) {
            predicates.add(cb.equal(root.get("method"), query.getMethod()));
        }
        if (StrUtil.isNotBlank(query.getUrl())) {
            predicates.add(cb.like(root.get("url"), "%" + query.getUrl() + "%"));
        }

        addCreateTimeRange(predicates, root, cb, query.getStartTime(), query.getEndTime(), "[ExecutionLog]");

        return predicates;
    }

    @Override
    public FlowExecutionLogDTO getById(String id) {
        return flowExecutionLogRepository.findById(id)
                .map(FlowExecutionLogDTO::fromDO)
                .orElse(null);
    }
}
