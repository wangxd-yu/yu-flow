package org.yu.flow.module.sysconfig.service.impl;

import cn.hutool.core.util.StrUtil;
import org.yu.flow.auto.dto.PageBean;
import org.yu.flow.module.sysconfig.cache.SysConfigCacheManager;
import org.yu.flow.module.sysconfig.domain.SysConfigDO;
import org.yu.flow.module.sysconfig.dto.SaveSysConfigDTO;
import org.yu.flow.module.sysconfig.dto.SysConfigDTO;
import org.yu.flow.module.sysconfig.query.SysConfigQueryDTO;
import org.yu.flow.module.sysconfig.repository.SysConfigRepository;
import org.yu.flow.module.sysconfig.service.SysConfigService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import javax.persistence.criteria.Predicate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 系统配置 Service 实现
 */
@Service
public class SysConfigServiceImpl implements SysConfigService {

    @Resource
    private SysConfigRepository sysConfigRepository;

    @Resource
    private SysConfigCacheManager sysConfigCacheManager;

    @Override
    public PageBean<SysConfigDTO> findPage(SysConfigQueryDTO queryDTO) {
        int page = Math.max(queryDTO.getPage() - 1, 0);
        Pageable pageable = PageRequest.of(page, queryDTO.getSize(),
                Sort.by(Sort.Direction.DESC, "createTime"));

        Specification<SysConfigDO> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (StrUtil.isNotBlank(queryDTO.getConfigKey())) {
                predicates.add(cb.like(root.get("configKey"), "%" + queryDTO.getConfigKey() + "%"));
            }
            if (StrUtil.isNotBlank(queryDTO.getConfigGroup())) {
                predicates.add(cb.equal(root.get("configGroup"), queryDTO.getConfigGroup()));
            }
            if (queryDTO.getStatus() != null) {
                predicates.add(cb.equal(root.get("status"), queryDTO.getStatus()));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };

        Page<SysConfigDO> result = sysConfigRepository.findAll(spec, pageable);

        List<SysConfigDTO> content = result.getContent().stream()
                .map(SysConfigDTO::fromDO)
                .collect(Collectors.toList());

        return new PageBean<>(
                content,
                result.getSize(),
                result.getNumber(),
                result.getTotalPages(),
                result.getTotalElements()
        );
    }

    @Override
    public SysConfigDTO findById(String id) {
        SysConfigDO entity = sysConfigRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("配置不存在，id: " + id));
        return SysConfigDTO.fromDO(entity);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public SysConfigDO create(SaveSysConfigDTO dto) {
        if (sysConfigRepository.existsByConfigKey(dto.getConfigKey())) {
            throw new RuntimeException("配置键已存在: " + dto.getConfigKey());
        }

        SysConfigDO entity = SysConfigDO.builder()
                .configKey(dto.getConfigKey())
                .configValue(dto.getConfigValue())
                .valueType(dto.getValueType())
                .configGroup(StrUtil.isNotBlank(dto.getConfigGroup()) ? dto.getConfigGroup() : "GENERAL")
                .remark(dto.getRemark())
                .isBuiltin(dto.getIsBuiltin() == null ? 0 : dto.getIsBuiltin())
                .status(dto.getStatus() == null ? 1 : dto.getStatus())
                .createTime(LocalDateTime.now())
                .updateTime(LocalDateTime.now())
                .build();

        SysConfigDO saved = sysConfigRepository.save(entity);
        sysConfigCacheManager.publishRefreshEvent();
        return saved;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public SysConfigDO update(String id, SaveSysConfigDTO dto) {
        SysConfigDO existing = sysConfigRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("配置不存在，id: " + id));

        if (StrUtil.isNotBlank(dto.getConfigKey())
                && !dto.getConfigKey().equals(existing.getConfigKey())
                && sysConfigRepository.existsByConfigKeyAndIdNot(dto.getConfigKey(), id)) {
            throw new RuntimeException("配置键已被占用: " + dto.getConfigKey());
        }

        // 内置参数无法改变其 configKey，通常也无法改变分组或类型，但可以修改 value
        if (existing.getIsBuiltin() != null && existing.getIsBuiltin() == 1) {
            // 只允许修改部分字段
            existing.setConfigValue(dto.getConfigValue());
            existing.setRemark(dto.getRemark());
            existing.setUpdateTime(LocalDateTime.now());
            SysConfigDO saved = sysConfigRepository.save(existing);
            sysConfigCacheManager.publishRefreshEvent();
            return saved;
        }

        if (StrUtil.isNotBlank(dto.getConfigKey())) {
            existing.setConfigKey(dto.getConfigKey());
        }
        existing.setConfigValue(dto.getConfigValue());
        if (StrUtil.isNotBlank(dto.getValueType())) {
            existing.setValueType(dto.getValueType());
        }
        if (StrUtil.isNotBlank(dto.getConfigGroup())) {
            existing.setConfigGroup(dto.getConfigGroup());
        }
        if (dto.getStatus() != null) {
            existing.setStatus(dto.getStatus());
        }
        existing.setRemark(dto.getRemark());
        existing.setUpdateTime(LocalDateTime.now());

        SysConfigDO saved = sysConfigRepository.save(existing);
        sysConfigCacheManager.publishRefreshEvent();
        return saved;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(String id) {
        SysConfigDO existing = sysConfigRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("配置不存在，id: " + id));

        if (existing.getIsBuiltin() != null && existing.getIsBuiltin() == 1) {
            throw new RuntimeException("系统内置配置不允许删除: " + existing.getConfigKey());
        }

        sysConfigRepository.deleteById(id);
        sysConfigCacheManager.publishRefreshEvent();
    }
}
