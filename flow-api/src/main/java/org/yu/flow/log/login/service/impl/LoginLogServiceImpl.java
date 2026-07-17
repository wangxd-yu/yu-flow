package org.yu.flow.log.login.service.impl;

import cn.hutool.core.util.StrUtil;
import lombok.extern.slf4j.Slf4j;
import org.yu.flow.auto.dto.PageBean;
import org.yu.flow.log.login.domain.LoginLogDO;
import org.yu.flow.log.login.dto.LoginLogDTO;
import org.yu.flow.log.login.query.LoginLogQueryDTO;
import org.yu.flow.log.login.repository.LoginLogRepository;
import org.yu.flow.log.login.service.LoginLogService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import jakarta.annotation.Resource;
import jakarta.persistence.criteria.Predicate;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 登录日志 Service 实现
 */
@Slf4j
@Service
public class LoginLogServiceImpl implements LoginLogService {

    @Resource
    private LoginLogRepository loginLogRepository;

    @Override
    public void saveLog(LoginLogDO logDO) {
        try {
            loginLogRepository.save(logDO);
        } catch (Exception e) {
            log.error("保存登录日志失败", e);
        }
    }

    @Override
    public PageBean<LoginLogDTO> findPage(LoginLogQueryDTO queryDTO) {
        int page = Math.max(queryDTO.getPage() - 1, 0);
        Pageable pageable = PageRequest.of(page, queryDTO.getSize(),
                Sort.by(Sort.Direction.DESC, "createTime"));

        Specification<LoginLogDO> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (StrUtil.isNotBlank(queryDTO.getAccount())) {
                predicates.add(cb.like(root.get("account"), "%" + queryDTO.getAccount() + "%"));
            }
            if (queryDTO.getStatus() != null) {
                predicates.add(cb.equal(root.get("status"), queryDTO.getStatus()));
            }
            if (queryDTO.getStartTime() != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("createTime"), queryDTO.getStartTime()));
            }
            if (queryDTO.getEndTime() != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("createTime"), queryDTO.getEndTime()));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };

        Page<LoginLogDO> result = loginLogRepository.findAll(spec, pageable);

        List<LoginLogDTO> content = result.getContent().stream()
                .map(LoginLogDTO::fromDO)
                .collect(Collectors.toList());

        return new PageBean<>(
                content,
                result.getNumber() + 1,
                result.getSize(),
                result.getTotalPages(),
                result.getTotalElements()
        );
    }
}
