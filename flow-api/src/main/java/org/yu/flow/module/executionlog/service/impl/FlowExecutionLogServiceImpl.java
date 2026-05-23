package org.yu.flow.module.executionlog.service.impl;

import cn.hutool.core.util.StrUtil;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.yu.flow.module.executionlog.domain.FlowExecutionLogDO;
import org.yu.flow.module.executionlog.dto.FlowExecutionLogDTO;
import org.yu.flow.module.executionlog.query.FlowExecutionLogQueryDTO;
import org.yu.flow.module.executionlog.repository.FlowExecutionLogRepository;
import org.yu.flow.module.executionlog.service.FlowExecutionLogService;

import javax.annotation.Resource;
import javax.persistence.criteria.Predicate;
import java.util.ArrayList;
import java.util.List;

@Service
public class FlowExecutionLogServiceImpl implements FlowExecutionLogService {

    @Resource
    private FlowExecutionLogRepository flowExecutionLogRepository;

    @Async
    @Override
    public void saveLogAsync(FlowExecutionLogDO logDO) {
        flowExecutionLogRepository.save(logDO);
    }

    @Override
    public Page<FlowExecutionLogDTO> pageQuery(FlowExecutionLogQueryDTO query) {
        Pageable pageable = PageRequest.of(query.getPage(), query.getSize(), Sort.by(Sort.Direction.DESC, "createTime"));

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
            return cb.and(predicates.toArray(new Predicate[0]));
        };

        Page<FlowExecutionLogDO> pageResult = flowExecutionLogRepository.findAll(spec, pageable);
        return pageResult.map(FlowExecutionLogDTO::fromDO);
    }

    @Override
    public FlowExecutionLogDTO getById(String id) {
        return flowExecutionLogRepository.findById(id).map(FlowExecutionLogDTO::fromDO).orElse(null);
    }
}
