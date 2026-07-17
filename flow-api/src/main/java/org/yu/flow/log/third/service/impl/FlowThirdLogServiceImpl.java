package org.yu.flow.log.third.service.impl;

import cn.hutool.core.util.StrUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.yu.flow.log.third.domain.FlowThirdLogDO;
import org.yu.flow.log.third.dto.FlowThirdLogDTO;
import org.yu.flow.log.third.dto.FlowThirdLogListDTO;
import org.yu.flow.log.third.query.FlowThirdLogQueryDTO;
import org.yu.flow.log.third.repository.FlowThirdLogRepository;
import org.yu.flow.log.third.service.FlowThirdLogService;

import jakarta.annotation.Resource;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.TypedQuery;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

@Slf4j
@Service
public class FlowThirdLogServiceImpl implements FlowThirdLogService {

    @Resource
    private FlowThirdLogRepository flowThirdLogRepository;

    @PersistenceContext
    private EntityManager entityManager;

    @Async("flowAsyncExecutor")
    @Override
    public void saveLogAsync(FlowThirdLogDO logDO) {
        try {
            flowThirdLogRepository.save(logDO);
        } catch (Exception e) {
            log.error("[ThirdLog] 异步入库失败, url={}, source={}, error={}",
                    logDO.getRequestUrl(), logDO.getSource(), e.getMessage(), e);
        }
    }

    /**
     * 列表分页：只投影摘要字段，不加载 requestParams/requestHeaders/responseBody/curl。
     */
    @Override
    public Page<FlowThirdLogListDTO> pageList(FlowThirdLogQueryDTO query) {
        int page = query.getPage() == null ? 0 : Math.max(query.getPage(), 0);
        int size = query.getSize() == null ? 10 : Math.max(query.getSize(), 1);
        Pageable pageable = PageRequest.of(page, size);

        CriteriaBuilder cb = entityManager.getCriteriaBuilder();

        CriteriaQuery<FlowThirdLogListDTO> dataQuery = cb.createQuery(FlowThirdLogListDTO.class);
        Root<FlowThirdLogDO> root = dataQuery.from(FlowThirdLogDO.class);
        List<Predicate> predicates = buildPredicates(query, root, cb);

        dataQuery.select(cb.construct(
                FlowThirdLogListDTO.class,
                root.get("id"),
                root.get("apiType"),
                root.get("source"),
                root.get("sourceRef"),
                root.get("sourceName"),
                root.get("requestUrl"),
                root.get("requestMethod"),
                root.get("responseStatus"),
                root.get("elapsedTime"),
                root.get("isSuccess"),
                root.get("createTime")
        ));
        dataQuery.where(predicates.toArray(new Predicate[0]));
        dataQuery.orderBy(cb.desc(root.get("createTime")));

        TypedQuery<FlowThirdLogListDTO> typedQuery = entityManager.createQuery(dataQuery);
        typedQuery.setFirstResult((int) pageable.getOffset());
        typedQuery.setMaxResults(pageable.getPageSize());
        List<FlowThirdLogListDTO> content = typedQuery.getResultList();

        CriteriaQuery<Long> countQuery = cb.createQuery(Long.class);
        Root<FlowThirdLogDO> countRoot = countQuery.from(FlowThirdLogDO.class);
        countQuery.select(cb.count(countRoot));
        countQuery.where(buildPredicates(query, countRoot, cb).toArray(new Predicate[0]));
        Long total = entityManager.createQuery(countQuery).getSingleResult();

        return new PageImpl<>(content, pageable, total == null ? 0 : total);
    }

    private List<Predicate> buildPredicates(FlowThirdLogQueryDTO query,
                                            Root<FlowThirdLogDO> root,
                                            CriteriaBuilder cb) {
        List<Predicate> predicates = new ArrayList<>();

        if (StrUtil.isNotBlank(query.getSource())) {
            predicates.add(cb.equal(root.get("source"), query.getSource()));
        }
        if (StrUtil.isNotBlank(query.getSourceName())) {
            predicates.add(cb.like(root.get("sourceName"), "%" + query.getSourceName() + "%"));
        }
        if (StrUtil.isNotBlank(query.getSourceRef())) {
            predicates.add(cb.equal(root.get("sourceRef"), query.getSourceRef()));
        }
        if (StrUtil.isNotBlank(query.getApiType())) {
            predicates.add(cb.like(root.get("apiType"), "%" + query.getApiType() + "%"));
        }
        if (query.getIsSuccess() != null) {
            predicates.add(cb.equal(root.get("isSuccess"), query.getIsSuccess()));
        }
        if (StrUtil.isNotBlank(query.getRequestMethod())) {
            predicates.add(cb.equal(root.get("requestMethod"), query.getRequestMethod()));
        }
        if (StrUtil.isNotBlank(query.getRequestUrl())) {
            predicates.add(cb.like(root.get("requestUrl"), "%" + query.getRequestUrl() + "%"));
        }

        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        if (StrUtil.isNotBlank(query.getStartTime())) {
            try {
                Date start = sdf.parse(query.getStartTime());
                predicates.add(cb.greaterThanOrEqualTo(root.get("createTime"), start));
            } catch (ParseException e) {
                log.warn("[ThirdLog] startTime 格式不合法, value={}", query.getStartTime());
            }
        }
        if (StrUtil.isNotBlank(query.getEndTime())) {
            try {
                Date end = sdf.parse(query.getEndTime());
                predicates.add(cb.lessThanOrEqualTo(root.get("createTime"), end));
            } catch (ParseException e) {
                log.warn("[ThirdLog] endTime 格式不合法, value={}", query.getEndTime());
            }
        }

        return predicates;
    }

    @Override
    public FlowThirdLogDTO getById(String id) {
        return flowThirdLogRepository.findById(id)
                .map(FlowThirdLogDTO::fromDO)
                .orElse(null);
    }
}
