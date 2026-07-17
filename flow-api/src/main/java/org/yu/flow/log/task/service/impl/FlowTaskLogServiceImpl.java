package org.yu.flow.log.task.service.impl;

import cn.hutool.core.util.StrUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.yu.flow.log.task.domain.FlowTaskLogDO;
import org.yu.flow.log.task.dto.FlowTaskLogDTO;
import org.yu.flow.log.task.dto.FlowTaskLogListDTO;
import org.yu.flow.log.task.query.FlowTaskLogQueryDTO;
import org.yu.flow.log.task.repository.FlowTaskLogRepository;
import org.yu.flow.log.task.service.FlowTaskLogService;

import jakarta.annotation.Resource;
import jakarta.persistence.criteria.Predicate;
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

    @Override
    public Page<FlowTaskLogListDTO> pageList(FlowTaskLogQueryDTO query) {
        Pageable pageable = PageRequest.of(
                query.getPage(), query.getSize(),
                Sort.by(Sort.Direction.DESC, "createTime")
        );

        Specification<FlowTaskLogDO> spec = (root, cq, cb) -> {
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

            return cb.and(predicates.toArray(new Predicate[0]));
        };

        return flowTaskLogRepository.findAll(spec, pageable)
                .map(FlowTaskLogListDTO::fromDO);
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
