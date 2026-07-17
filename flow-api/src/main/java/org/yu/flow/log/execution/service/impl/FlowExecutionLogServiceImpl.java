package org.yu.flow.log.execution.service.impl;

import cn.hutool.core.util.StrUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.yu.flow.log.execution.domain.FlowExecutionLogDO;
import org.yu.flow.log.execution.dto.FlowExecutionLogDTO;
import org.yu.flow.log.execution.dto.FlowExecutionLogListDTO;
import org.yu.flow.log.execution.query.FlowExecutionLogQueryDTO;
import org.yu.flow.log.execution.repository.FlowExecutionLogRepository;
import org.yu.flow.log.execution.service.FlowExecutionLogService;

import jakarta.annotation.Resource;
import jakarta.persistence.criteria.Predicate;
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

    @Override
    public Page<FlowExecutionLogListDTO> pageList(FlowExecutionLogQueryDTO query) {
        Pageable pageable = PageRequest.of(
                query.getPage(), query.getSize(),
                Sort.by(Sort.Direction.DESC, "createTime")
        );

        Specification<FlowExecutionLogDO> spec = (root, cq, cb) -> {
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
            // 新增：URL 路径模糊搜索
            if (StrUtil.isNotBlank(query.getUrl())) {
                predicates.add(cb.like(root.get("url"), "%" + query.getUrl() + "%"));
            }
            // 新增：时间范围过滤
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

            return cb.and(predicates.toArray(new Predicate[0]));
        };

        return flowExecutionLogRepository.findAll(spec, pageable)
                .map(FlowExecutionLogListDTO::fromDO);
    }

    @Override
    public FlowExecutionLogDTO getById(String id) {
        return flowExecutionLogRepository.findById(id)
                .map(FlowExecutionLogDTO::fromDO)
                .orElse(null);
    }
}
