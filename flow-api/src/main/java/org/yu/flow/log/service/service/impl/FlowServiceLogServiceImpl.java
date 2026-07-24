package org.yu.flow.log.service.service.impl;

import cn.hutool.core.util.StrUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.yu.flow.log.service.domain.FlowServiceLogDO;
import org.yu.flow.log.service.dto.FlowServiceLogDTO;
import org.yu.flow.log.service.dto.FlowServiceLogListDTO;
import org.yu.flow.log.service.query.FlowServiceLogQueryDTO;
import org.yu.flow.log.service.repository.FlowServiceLogRepository;
import org.yu.flow.log.service.service.FlowServiceLogService;

import jakarta.annotation.Resource;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.TypedQuery;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
public class FlowServiceLogServiceImpl implements FlowServiceLogService {

    private static final DateTimeFormatter DATE_TIME_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @Resource
    private FlowServiceLogRepository flowServiceLogRepository;

    @PersistenceContext
    private EntityManager entityManager;

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
        int page = query.getPage() == null ? 0 : Math.max(query.getPage(), 0);
        int size = query.getSize() == null ? 10 : Math.max(query.getSize(), 1);
        Pageable pageable = PageRequest.of(page, size);

        CriteriaBuilder cb = entityManager.getCriteriaBuilder();

        CriteriaQuery<FlowServiceLogListDTO> dataQuery = cb.createQuery(FlowServiceLogListDTO.class);
        Root<FlowServiceLogDO> root = dataQuery.from(FlowServiceLogDO.class);
        List<Predicate> predicates = buildPredicates(query, root, cb);

        Expression<Boolean> hasTrace = cb.<Boolean>selectCase()
                .when(cb.isNotNull(root.get("traceData")), cb.literal(true))
                .otherwise(cb.literal(false));

        dataQuery.select(cb.construct(
                FlowServiceLogListDTO.class,
                root.get("id"),
                root.get("serviceId"),
                root.get("serviceName"),
                root.get("triggerType"),
                root.get("status"),
                root.get("costTimeMs"),
                hasTrace,
                root.get("createTime")
        ));
        dataQuery.where(predicates.toArray(new Predicate[0]));
        dataQuery.orderBy(cb.desc(root.get("createTime")));

        TypedQuery<FlowServiceLogListDTO> typedQuery = entityManager.createQuery(dataQuery);
        typedQuery.setFirstResult((int) pageable.getOffset());
        typedQuery.setMaxResults(pageable.getPageSize());
        List<FlowServiceLogListDTO> content = typedQuery.getResultList();

        CriteriaQuery<Long> countQuery = cb.createQuery(Long.class);
        Root<FlowServiceLogDO> countRoot = countQuery.from(FlowServiceLogDO.class);
        countQuery.select(cb.count(countRoot));
        countQuery.where(buildPredicates(query, countRoot, cb).toArray(new Predicate[0]));
        Long total = entityManager.createQuery(countQuery).getSingleResult();

        return new PageImpl<>(content, pageable, total == null ? 0 : total);
    }

    private List<Predicate> buildPredicates(FlowServiceLogQueryDTO query,
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

        if (StrUtil.isNotBlank(query.getStartTime())) {
            try {
                LocalDateTime start = LocalDateTime.parse(query.getStartTime().trim(), DATE_TIME_FMT);
                predicates.add(cb.greaterThanOrEqualTo(root.get("createTime"), start));
            } catch (DateTimeParseException e) {
                log.warn("[ServiceLog] startTime 格式不合法, value={}", query.getStartTime());
            }
        }
        if (StrUtil.isNotBlank(query.getEndTime())) {
            try {
                LocalDateTime end = LocalDateTime.parse(query.getEndTime().trim(), DATE_TIME_FMT);
                predicates.add(cb.lessThanOrEqualTo(root.get("createTime"), end));
            } catch (DateTimeParseException e) {
                log.warn("[ServiceLog] endTime 格式不合法, value={}", query.getEndTime());
            }
        }

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
