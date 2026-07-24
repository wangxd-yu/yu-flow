package org.yu.flow.log.open.service.impl;

import cn.hutool.core.util.StrUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.yu.flow.log.open.domain.FlowOpenCallLogDO;
import org.yu.flow.log.open.dto.FlowOpenCallLogListDTO;
import org.yu.flow.log.open.query.FlowOpenCallLogQueryDTO;
import org.yu.flow.log.open.repository.FlowOpenCallLogRepository;
import org.yu.flow.log.open.service.FlowOpenCallLogService;

import jakarta.annotation.Resource;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.TypedQuery;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
public class FlowOpenCallLogServiceImpl implements FlowOpenCallLogService {

    private static final DateTimeFormatter DATE_TIME_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @Resource
    private FlowOpenCallLogRepository flowOpenCallLogRepository;

    @PersistenceContext
    private EntityManager entityManager;

    @Async("flowAsyncExecutor")
    @Override
    public void saveLogAsync(FlowOpenCallLogDO logDO) {
        try {
            flowOpenCallLogRepository.save(logDO);
        } catch (Exception e) {
            log.error("[OpenCallLog] 异步入库失败 platformId={}, path={}, error={}",
                    logDO.getPlatformId(), logDO.getPath(), e.getMessage());
        }
    }

    @Override
    public Page<FlowOpenCallLogListDTO> pageList(FlowOpenCallLogQueryDTO query) {
        int page = query.getPage() == null ? 0 : Math.max(query.getPage(), 0);
        int size = query.getSize() == null ? 20 : Math.min(Math.max(query.getSize(), 1), 100);
        Pageable pageable = PageRequest.of(page, size);

        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<FlowOpenCallLogListDTO> dataQuery = cb.createQuery(FlowOpenCallLogListDTO.class);
        Root<FlowOpenCallLogDO> root = dataQuery.from(FlowOpenCallLogDO.class);
        List<Predicate> predicates = buildPredicates(query, root, cb);

        dataQuery.select(cb.construct(
                FlowOpenCallLogListDTO.class,
                root.get("id"),
                root.get("platformId"),
                root.get("appKey"),
                root.get("apiId"),
                root.get("method"),
                root.get("path"),
                root.get("status"),
                root.get("costMs"),
                root.get("errorCode"),
                root.get("requestId"),
                root.get("createTime")
        ));
        dataQuery.where(predicates.toArray(new Predicate[0]));
        dataQuery.orderBy(cb.desc(root.get("createTime")));

        TypedQuery<FlowOpenCallLogListDTO> typed = entityManager.createQuery(dataQuery);
        typed.setFirstResult((int) pageable.getOffset());
        typed.setMaxResults(pageable.getPageSize());
        List<FlowOpenCallLogListDTO> content = typed.getResultList();

        CriteriaQuery<Long> countQuery = cb.createQuery(Long.class);
        Root<FlowOpenCallLogDO> countRoot = countQuery.from(FlowOpenCallLogDO.class);
        countQuery.select(cb.count(countRoot));
        countQuery.where(buildPredicates(query, countRoot, cb).toArray(new Predicate[0]));
        Long total = entityManager.createQuery(countQuery).getSingleResult();

        return new PageImpl<>(content, pageable, total == null ? 0 : total);
    }

    private List<Predicate> buildPredicates(FlowOpenCallLogQueryDTO query,
                                            Root<FlowOpenCallLogDO> root,
                                            CriteriaBuilder cb) {
        List<Predicate> ps = new ArrayList<>();
        if (StrUtil.isNotBlank(query.getPlatformId())) {
            ps.add(cb.equal(root.get("platformId"), query.getPlatformId().trim()));
        }
        if (StrUtil.isNotBlank(query.getAppKey())) {
            ps.add(cb.like(root.get("appKey"), "%" + query.getAppKey().trim() + "%"));
        }
        if (StrUtil.isNotBlank(query.getPath())) {
            ps.add(cb.like(root.get("path"), "%" + query.getPath().trim() + "%"));
        }
        if (query.getStatus() != null) {
            ps.add(cb.equal(root.get("status"), query.getStatus()));
        }
        if (StrUtil.isNotBlank(query.getErrorCode())) {
            ps.add(cb.equal(root.get("errorCode"), query.getErrorCode().trim()));
        }
        LocalDateTime start = parseTime(query.getStartTime());
        LocalDateTime end = parseTime(query.getEndTime());
        if (start != null) {
            ps.add(cb.greaterThanOrEqualTo(root.get("createTime"), start));
        }
        if (end != null) {
            ps.add(cb.lessThanOrEqualTo(root.get("createTime"), end));
        }
        return ps;
    }

    private static LocalDateTime parseTime(String s) {
        if (StrUtil.isBlank(s)) {
            return null;
        }
        try {
            return LocalDateTime.parse(s.trim(), DATE_TIME_FMT);
        } catch (DateTimeParseException e) {
            return null;
        }
    }
}
