package org.yu.flow.log.third.service.impl;

import cn.hutool.core.util.StrUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.yu.flow.log.support.AbstractLogQueryService;
import org.yu.flow.log.third.domain.FlowThirdLogDO;
import org.yu.flow.log.third.dto.FlowThirdLogDTO;
import org.yu.flow.log.third.dto.FlowThirdLogListDTO;
import org.yu.flow.log.third.query.FlowThirdLogQueryDTO;
import org.yu.flow.log.third.repository.FlowThirdLogRepository;
import org.yu.flow.log.third.service.FlowThirdLogService;

import jakarta.annotation.Resource;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Selection;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
public class FlowThirdLogServiceImpl
        extends AbstractLogQueryService<FlowThirdLogDO, FlowThirdLogQueryDTO, FlowThirdLogListDTO>
        implements FlowThirdLogService {

    @Resource
    private FlowThirdLogRepository flowThirdLogRepository;

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
        return pageQuery(query, query.getPage(), query.getSize());
    }

    @Override
    protected Class<FlowThirdLogDO> entityClass() {
        return FlowThirdLogDO.class;
    }

    @Override
    protected Class<FlowThirdLogListDTO> listDtoClass() {
        return FlowThirdLogListDTO.class;
    }

    @Override
    protected List<Selection<?>> selections(CriteriaBuilder cb, Root<FlowThirdLogDO> root) {
        return List.of(
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
                root.get("createTime"));
    }

    @Override
    protected List<Predicate> buildPredicates(FlowThirdLogQueryDTO query,
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

        addCreateTimeRange(predicates, root, cb, query.getStartTime(), query.getEndTime(), "[ThirdLog]");

        return predicates;
    }

    @Override
    public FlowThirdLogDTO getById(String id) {
        return flowThirdLogRepository.findById(id)
                .map(FlowThirdLogDTO::fromDO)
                .orElse(null);
    }
}
