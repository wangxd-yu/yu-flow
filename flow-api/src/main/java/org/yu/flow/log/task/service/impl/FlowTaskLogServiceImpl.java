package org.yu.flow.log.task.service.impl;

import cn.hutool.core.util.StrUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.yu.flow.log.task.domain.FlowTaskLogDO;
import org.yu.flow.log.task.dto.FlowTaskLogDTO;
import org.yu.flow.log.task.dto.FlowTaskLogListDTO;
import org.yu.flow.log.task.query.FlowTaskLogQueryDTO;
import org.yu.flow.log.task.repository.FlowTaskLogRepository;
import org.yu.flow.log.task.service.FlowTaskLogService;

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

/**
 * 任务日志服务实现
 *
 * @author yu-flow
 */
@Slf4j
@Service
public class FlowTaskLogServiceImpl implements FlowTaskLogService {

    @Resource
    private FlowTaskLogRepository flowTaskLogRepository;

    @PersistenceContext
    private EntityManager entityManager;

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

    /**
     * 列表分页：只投影摘要字段。
     * <p>用 {@code CASE WHEN traceData IS NOT NULL} 判断 hasTrace，不 SELECT 大字段内容。
     * HighGo/PG 下 IS NOT NULL 不会展开 TOAST/LONGTEXT。
     */
    @Override
    public Page<FlowTaskLogListDTO> pageList(FlowTaskLogQueryDTO query) {
        int page = query.getPage() == null ? 0 : Math.max(query.getPage(), 0);
        int size = query.getSize() == null ? 10 : Math.max(query.getSize(), 1);
        Pageable pageable = PageRequest.of(page, size);

        CriteriaBuilder cb = entityManager.getCriteriaBuilder();

        CriteriaQuery<FlowTaskLogListDTO> dataQuery = cb.createQuery(FlowTaskLogListDTO.class);
        Root<FlowTaskLogDO> root = dataQuery.from(FlowTaskLogDO.class);
        List<Predicate> predicates = buildPredicates(query, root, cb);

        Expression<Boolean> hasTrace = cb.<Boolean>selectCase()
                .when(cb.isNotNull(root.get("traceData")), cb.literal(true))
                .otherwise(cb.literal(false));

        dataQuery.select(cb.construct(
                FlowTaskLogListDTO.class,
                root.get("id"),
                root.get("taskId"),
                root.get("taskName"),
                root.get("triggerType"),
                root.get("status"),
                root.get("costTimeMs"),
                hasTrace,
                root.get("createTime")
        ));
        dataQuery.where(predicates.toArray(new Predicate[0]));
        dataQuery.orderBy(cb.desc(root.get("createTime")));

        TypedQuery<FlowTaskLogListDTO> typedQuery = entityManager.createQuery(dataQuery);
        typedQuery.setFirstResult((int) pageable.getOffset());
        typedQuery.setMaxResults(pageable.getPageSize());
        List<FlowTaskLogListDTO> content = typedQuery.getResultList();

        CriteriaQuery<Long> countQuery = cb.createQuery(Long.class);
        Root<FlowTaskLogDO> countRoot = countQuery.from(FlowTaskLogDO.class);
        countQuery.select(cb.count(countRoot));
        countQuery.where(buildPredicates(query, countRoot, cb).toArray(new Predicate[0]));
        Long total = entityManager.createQuery(countQuery).getSingleResult();

        return new PageImpl<>(content, pageable, total == null ? 0 : total);
    }

    private List<Predicate> buildPredicates(FlowTaskLogQueryDTO query,
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

        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        if (StrUtil.isNotBlank(query.getStartTime())) {
            try {
                Date start = sdf.parse(query.getStartTime());
                predicates.add(cb.greaterThanOrEqualTo(root.get("createTime"), start));
            } catch (ParseException e) {
                log.warn("[TaskLog] startTime 格式不合法, value={}", query.getStartTime());
            }
        }
        if (StrUtil.isNotBlank(query.getEndTime())) {
            try {
                Date end = sdf.parse(query.getEndTime());
                predicates.add(cb.lessThanOrEqualTo(root.get("createTime"), end));
            } catch (ParseException e) {
                log.warn("[TaskLog] endTime 格式不合法, value={}", query.getEndTime());
            }
        }

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
