package org.yu.flow.log.service.service.impl;

import cn.hutool.core.util.StrUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.yu.flow.log.service.domain.FlowServiceLogDO;
import org.yu.flow.log.service.dto.FlowServiceLogDTO;
import org.yu.flow.log.service.dto.FlowServiceLogListDTO;
import org.yu.flow.log.service.query.FlowServiceLogQueryDTO;
import org.yu.flow.log.service.repository.FlowServiceLogRepository;
import org.yu.flow.log.service.service.FlowServiceLogService;
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
public class FlowServiceLogServiceImpl
        extends AbstractLogQueryService<FlowServiceLogDO, FlowServiceLogQueryDTO, FlowServiceLogListDTO>
        implements FlowServiceLogService {

    @Resource
    private FlowServiceLogRepository flowServiceLogRepository;

    @Override
    public FlowServiceLogDO save(FlowServiceLogDO logDO) {
        try {
            return flowServiceLogRepository.save(logDO);
        } catch (Exception e) {
            log.error("[ServiceLog] 日志入库失败, serviceId={}, status={}, error={}",
                    logDO.getServiceId(), logDO.getStatus(), e.getMessage(), e);
            return logDO;
        }
    }

    @Async("flowAsyncExecutor")
    @Override
    public void saveAsync(FlowServiceLogDO logDO) {
        try {
            flowServiceLogRepository.save(logDO);
        } catch (Exception e) {
            log.error("[ServiceLog] 异步入库失败, serviceId={}, status={}, error={}",
                    logDO.getServiceId(), logDO.getStatus(), e.getMessage(), e);
        }
    }

    @Override
    public Page<FlowServiceLogListDTO> pageList(FlowServiceLogQueryDTO query) {
        return pageQuery(query, query.getPage(), query.getSize());
    }

    @Override
    protected Class<FlowServiceLogDO> entityClass() {
        return FlowServiceLogDO.class;
    }

    @Override
    protected Class<FlowServiceLogListDTO> listDtoClass() {
        return FlowServiceLogListDTO.class;
    }

    @Override
    protected List<Selection<?>> selections(CriteriaBuilder cb, Root<FlowServiceLogDO> root) {
        return List.of(
                root.get("id"),
                root.get("serviceId"),
                root.get("serviceName"),
                root.get("triggerType"),
                root.get("status"),
                root.get("costTimeMs"),
                hasTraceExpression(cb, root),
                root.get("createTime"));
    }

    @Override
    protected List<Predicate> buildPredicates(FlowServiceLogQueryDTO query,
                                              Root<FlowServiceLogDO> root,
                                              CriteriaBuilder cb) {
        List<Predicate> predicates = new ArrayList<>();

        if (StrUtil.isNotBlank(query.getServiceId())) {
            predicates.add(cb.equal(root.get("serviceId"), query.getServiceId()));
        }
        if (StrUtil.isNotBlank(query.getServiceName())) {
            predicates.add(cb.like(root.get("serviceName"), "%" + query.getServiceName() + "%"));
        }
        if (StrUtil.isNotBlank(query.getStatus())) {
            predicates.add(cb.equal(root.get("status"), query.getStatus()));
        }
        if (StrUtil.isNotBlank(query.getTriggerType())) {
            predicates.add(cb.equal(root.get("triggerType"), query.getTriggerType()));
        }

        addCreateTimeRange(predicates, root, cb, query.getStartTime(), query.getEndTime(), "[ServiceLog]");

        return predicates;
    }

    @Override
    public FlowServiceLogDTO getById(String id) {
        return flowServiceLogRepository.findById(id)
                .map(FlowServiceLogDTO::fromDO)
                .orElse(null);
    }

    @Override
    @Transactional
    public void clearByServiceId(String serviceId) {
        flowServiceLogRepository.deleteByServiceId(serviceId);
    }
}
