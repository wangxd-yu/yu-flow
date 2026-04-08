package org.yu.flow.module.sysmacro.service;

import cn.hutool.core.util.StrUtil;
import lombok.extern.slf4j.Slf4j;
import org.yu.flow.auto.dto.PageBean;
import org.yu.flow.module.sysmacro.cache.SysMacroCacheManager;
import org.yu.flow.module.sysmacro.domain.SysMacroDO;
import org.yu.flow.module.sysmacro.dto.SysMacroDTO;
import org.yu.flow.module.sysmacro.dto.SaveSysMacroDTO;
import org.yu.flow.module.sysmacro.query.SysMacroQueryDTO;
import org.yu.flow.module.sysmacro.repository.SysMacroRepository;
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
 * 系统全局宏定义 Service 实现
 *
 * <p>在 create / update / delete 操作成功后，
 * 通过 {@link SysMacroCacheManager#publishRefreshEvent()} 广播缓存刷新事件，
 * 确保集群所有节点的本地缓存得到同步更新。</p>
 */
@Slf4j
@Service
public class SysMacroServiceImpl implements SysMacroService {

    @Resource
    private SysMacroRepository sysMacroRepository;

    @Resource
    private SysMacroCacheManager sysMacroCacheManager;

    @Override
    public PageBean<SysMacroDTO> findPage(SysMacroQueryDTO queryDTO) {
        int page = Math.max(queryDTO.getPage() - 1, 0);
        Pageable pageable = PageRequest.of(page, queryDTO.getSize(),
                Sort.by(Sort.Direction.DESC, "createTime"));

        Specification<SysMacroDO> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (StrUtil.isNotBlank(queryDTO.getMacroCode())) {
                predicates.add(cb.like(root.get("macroCode"), "%" + queryDTO.getMacroCode() + "%"));
            }
            if (StrUtil.isNotBlank(queryDTO.getMacroName())) {
                predicates.add(cb.like(root.get("macroName"), "%" + queryDTO.getMacroName() + "%"));
            }
            if (StrUtil.isNotBlank(queryDTO.getMacroType())) {
                predicates.add(cb.equal(root.get("macroType"), queryDTO.getMacroType()));
            }
            if (StrUtil.isNotBlank(queryDTO.getScope())) {
                predicates.add(cb.equal(root.get("scope"), queryDTO.getScope()));
            }
            if (queryDTO.getStatus() != null) {
                predicates.add(cb.equal(root.get("status"), queryDTO.getStatus()));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };

        Page<SysMacroDO> result = sysMacroRepository.findAll(spec, pageable);

        List<SysMacroDTO> content = result.getContent().stream()
                .map(SysMacroDTO::fromDO)
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
    public SysMacroDTO findById(String id) {
        SysMacroDO entity = sysMacroRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("宏定义不存在，id: " + id));
        return SysMacroDTO.fromDO(entity);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public SysMacroDO create(SaveSysMacroDTO dto) {
        // 校验 macroCode 唯一性
        if (sysMacroRepository.existsByMacroCode(dto.getMacroCode())) {
            throw new RuntimeException("宏编码已存在: " + dto.getMacroCode());
        }

        SysMacroDO entity = SysMacroDO.builder()
                .macroCode(dto.getMacroCode())
                .macroName(dto.getMacroName())
                .macroType(dto.getMacroType())
                .expression(dto.getExpression())
                .scope(StrUtil.isNotBlank(dto.getScope()) ? dto.getScope() : "ALL")
                .returnType(dto.getReturnType())
                .macroParams(dto.getMacroParams())
                .status(dto.getStatus() == null ? 1 : dto.getStatus())
                .remark(dto.getRemark())
                .createTime(LocalDateTime.now())
                .updateTime(LocalDateTime.now())
                .build();

        SysMacroDO saved = sysMacroRepository.save(entity);

        // 广播缓存刷新事件，通知集群所有节点重载本地缓存
        sysMacroCacheManager.publishRefreshEvent();

        return saved;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public SysMacroDO update(String id, SaveSysMacroDTO dto) {
        SysMacroDO existing = sysMacroRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("宏定义不存在，id: " + id));

        // 校验 macroCode 唯一性（排除自身）
        if (StrUtil.isNotBlank(dto.getMacroCode())
                && !dto.getMacroCode().equals(existing.getMacroCode())
                && sysMacroRepository.existsByMacroCodeAndIdNot(dto.getMacroCode(), id)) {
            throw new RuntimeException("宏编码已被占用: " + dto.getMacroCode());
        }

        if (StrUtil.isNotBlank(dto.getMacroCode())) {
            existing.setMacroCode(dto.getMacroCode());
        }
        if (StrUtil.isNotBlank(dto.getMacroName())) {
            existing.setMacroName(dto.getMacroName());
        }
        if (StrUtil.isNotBlank(dto.getMacroType())) {
            existing.setMacroType(dto.getMacroType());
        }
        if (StrUtil.isNotBlank(dto.getExpression())) {
            existing.setExpression(dto.getExpression());
        }
        if (StrUtil.isNotBlank(dto.getScope())) {
            existing.setScope(dto.getScope());
        }
        if (dto.getReturnType() != null) {
            existing.setReturnType(dto.getReturnType());
        }
        if (dto.getStatus() != null) {
            existing.setStatus(dto.getStatus());
        }
        if (dto.getRemark() != null) {
            existing.setRemark(dto.getRemark());
        }
        // macroParams 允许设为 null（当类型从 FUNCTION 改为 VARIABLE 时需要清空）
        existing.setMacroParams(dto.getMacroParams());

        existing.setUpdateTime(LocalDateTime.now());
        SysMacroDO saved = sysMacroRepository.save(existing);

        // 广播缓存刷新事件，通知集群所有节点重载本地缓存
        sysMacroCacheManager.publishRefreshEvent();

        return saved;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(String id) {
        if (!sysMacroRepository.existsById(id)) {
            throw new RuntimeException("宏定义不存在，id: " + id);
        }
        sysMacroRepository.deleteById(id);

        // 广播缓存刷新事件，通知集群所有节点重载本地缓存
        sysMacroCacheManager.publishRefreshEvent();
    }

    @Override
    public List<SysMacroDO> getAllActiveMacros() {
        return sysMacroRepository.findAllByStatus(1);
    }
}
