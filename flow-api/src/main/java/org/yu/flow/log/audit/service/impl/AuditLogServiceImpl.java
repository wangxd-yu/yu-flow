package org.yu.flow.log.audit.service.impl;

import cn.hutool.core.util.StrUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.yu.flow.auto.dto.PageBean;
import org.yu.flow.auto.util.JwtTokenUtil;
import org.yu.flow.log.audit.domain.AuditLogDO;
import org.yu.flow.log.audit.dto.AuditLogDTO;
import org.yu.flow.log.audit.query.AuditLogQueryDTO;
import org.yu.flow.log.audit.repository.AuditLogRepository;
import org.yu.flow.log.audit.service.AuditLogService;

import jakarta.annotation.Resource;
import jakarta.persistence.criteria.Predicate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
public class AuditLogServiceImpl implements AuditLogService {

    @Resource
    private AuditLogRepository auditLogRepository;

    @Override
    public void record(String action, String targetType, String targetId, String detail) {
        try {
            String operator = JwtTokenUtil.currentUsername();
            AuditLogDO row = AuditLogDO.builder()
                    .action(action)
                    .operator(StrUtil.blankToDefault(operator, "system"))
                    .targetType(targetType)
                    .targetId(targetId)
                    .detail(detail == null ? null : (detail.length() > 1000 ? detail.substring(0, 1000) : detail))
                    .createTime(LocalDateTime.now())
                    .build();
            auditLogRepository.save(row);
        } catch (Exception e) {
            log.warn("[AuditLog] 记录失败（不影响业务）: {}", e.getMessage());
        }
    }

    @Override
    public PageBean<AuditLogDTO> findPage(AuditLogQueryDTO queryDTO) {
        int page = Math.max(queryDTO.getPage() - 1, 0);
        Specification<AuditLogDO> spec = (root, q, cb) -> {
            List<Predicate> ps = new ArrayList<>();
            if (StrUtil.isNotBlank(queryDTO.getAction())) {
                ps.add(cb.equal(root.get("action"), queryDTO.getAction().trim()));
            }
            if (StrUtil.isNotBlank(queryDTO.getOperator())) {
                ps.add(cb.like(root.get("operator"), "%" + queryDTO.getOperator().trim() + "%"));
            }
            if (StrUtil.isNotBlank(queryDTO.getTargetId())) {
                ps.add(cb.equal(root.get("targetId"), queryDTO.getTargetId().trim()));
            }
            if (queryDTO.getStartTime() != null) {
                ps.add(cb.greaterThanOrEqualTo(root.get("createTime"), queryDTO.getStartTime()));
            }
            if (queryDTO.getEndTime() != null) {
                ps.add(cb.lessThanOrEqualTo(root.get("createTime"), queryDTO.getEndTime()));
            }
            return cb.and(ps.toArray(new Predicate[0]));
        };
        Page<AuditLogDO> result = auditLogRepository.findAll(spec,
                PageRequest.of(page, queryDTO.getSize(), Sort.by(Sort.Direction.DESC, "createTime")));
        List<AuditLogDTO> content = result.getContent().stream()
                .map(AuditLogDTO::fromDO)
                .collect(Collectors.toList());
        return new PageBean<>(content, result.getNumber() + 1, result.getSize(),
                result.getTotalPages(), result.getTotalElements());
    }
}
