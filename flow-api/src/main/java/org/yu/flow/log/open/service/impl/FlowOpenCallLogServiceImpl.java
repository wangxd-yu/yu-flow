package org.yu.flow.log.open.service.impl;

import cn.hutool.core.util.StrUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.yu.flow.log.open.domain.FlowOpenCallLogDO;
import org.yu.flow.log.open.dto.FlowOpenCallLogListDTO;
import org.yu.flow.log.open.query.FlowOpenCallLogQueryDTO;
import org.yu.flow.log.open.repository.FlowOpenCallLogRepository;
import org.yu.flow.log.open.service.FlowOpenCallLogService;
import org.yu.flow.log.support.AbstractLogQueryService;

import jakarta.annotation.Resource;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Selection;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
public class FlowOpenCallLogServiceImpl
        extends AbstractLogQueryService<FlowOpenCallLogDO, FlowOpenCallLogQueryDTO, FlowOpenCallLogListDTO>
        implements FlowOpenCallLogService {

    @Resource
    private FlowOpenCallLogRepository flowOpenCallLogRepository;

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
        return pageQuery(query, query.getPage(), query.getSize());
    }

    @Override
    protected int defaultPageSize() {
        return 20;
    }

    @Override
    protected int maxPageSize() {
        return 100;
    }

    @Override
    protected Class<FlowOpenCallLogDO> entityClass() {
        return FlowOpenCallLogDO.class;
    }

    @Override
    protected Class<FlowOpenCallLogListDTO> listDtoClass() {
        return FlowOpenCallLogListDTO.class;
    }

    @Override
    protected List<Selection<?>> selections(CriteriaBuilder cb, Root<FlowOpenCallLogDO> root) {
        return List.of(
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
                root.get("createTime"));
    }

    @Override
    protected List<Predicate> buildPredicates(FlowOpenCallLogQueryDTO query,
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
        LocalDateTime start = parseTimeQuietly(query.getStartTime());
        LocalDateTime end = parseTimeQuietly(query.getEndTime());
        if (start != null) {
            ps.add(cb.greaterThanOrEqualTo(root.get("createTime"), start));
        }
        if (end != null) {
            ps.add(cb.lessThanOrEqualTo(root.get("createTime"), end));
        }
        return ps;
    }
}
