package org.yu.flow.log.execution.service.impl;

import cn.hutool.core.util.StrUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.yu.flow.log.execution.domain.FlowExecutionLogDO;
import org.yu.flow.log.execution.dto.FlowExecutionLogDTO;
import org.yu.flow.log.execution.dto.FlowExecutionLogListDTO;
import org.yu.flow.log.execution.query.FlowExecutionLogQueryDTO;
import org.yu.flow.log.execution.repository.FlowExecutionLogRepository;
import org.yu.flow.log.execution.service.FlowExecutionLogService;

import jakarta.annotation.Resource;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.TypedQuery;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

@Slf4j
@Service
public class FlowExecutionLogServiceImpl implements FlowExecutionLogService {

    @Resource
    private FlowExecutionLogRepository flowExecutionLogRepository;

    @PersistenceContext
    private EntityManager entityManager;

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
        int page = query.getPage() == null ? 0 : Math.max(query.getPage(), 0);
        int size = query.getSize() == null ? 10 : Math.max(query.getSize(), 1);
        Pageable pageable = PageRequest.of(page, size);

        CriteriaBuilder cb = entityManager.getCriteriaBuilder();

        CriteriaQuery<FlowExecutionLogListDTO> dataQuery = cb.createQuery(FlowExecutionLogListDTO.class);
        Root<FlowExecutionLogDO> root = dataQuery.from(FlowExecutionLogDO.class);
        List<Predicate> predicates = buildPredicates(query, root, cb);

        Expression<Boolean> hasTrace = cb.<Boolean>selectCase()
                .when(cb.isNotNull(root.get("traceData")), cb.literal(true))
                .otherwise(cb.literal(false));

        dataQuery.select(cb.construct(
                FlowExecutionLogListDTO.class,
                root.get("id"),
                root.get("apiId"),
                root.get("apiName"),
                root.get("url"),
                root.get("serviceType"),
                root.get("method"),
                root.get("status"),
                root.get("costTimeMs"),
                hasTrace,
                root.get("createTime")
        ));
        dataQuery.where(predicates.toArray(new Predicate[0]));
        dataQuery.orderBy(cb.desc(root.get("createTime")));

        TypedQuery<FlowExecutionLogListDTO> typedQuery = entityManager.createQuery(dataQuery);
        typedQuery.setFirstResult((int) pageable.getOffset());
        typedQuery.setMaxResults(pageable.getPageSize());
        List<FlowExecutionLogListDTO> content = typedQuery.getResultList();

        CriteriaQuery<Long> countQuery = cb.createQuery(Long.class);
        Root<FlowExecutionLogDO> countRoot = countQuery.from(FlowExecutionLogDO.class);
        countQuery.select(cb.count(countRoot));
        countQuery.where(buildPredicates(query, countRoot, cb).toArray(new Predicate[0]));
        Long total = entityManager.createQuery(countQuery).getSingleResult();

        return new PageImpl<>(content, pageable, total == null ? 0 : total);
    }

    private List<Predicate> buildPredicates(FlowExecutionLogQueryDTO query,
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

        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        if (StrUtil.isNotBlank(query.getStartTime())) {
            try {
                Date start = sdf.parse(query.getStartTime());
                predicates.add(cb.greaterThanOrEqualTo(root.get("createTime"), start));
            } catch (ParseException e) {
                log.warn("[ExecutionLog] startTime 格式不合法, value={}", query.getStartTime());
            }
        }
        if (StrUtil.isNotBlank(query.getEndTime())) {
            try {
                Date end = sdf.parse(query.getEndTime());
                predicates.add(cb.lessThanOrEqualTo(root.get("createTime"), end));
            } catch (ParseException e) {
                log.warn("[ExecutionLog] endTime 格式不合法, value={}", query.getEndTime());
            }
        }

        return predicates;
    }

    @Override
    public FlowExecutionLogDTO getById(String id) {
        return flowExecutionLogRepository.findById(id)
                .map(FlowExecutionLogDTO::fromDO)
                .orElse(null);
    }
}
