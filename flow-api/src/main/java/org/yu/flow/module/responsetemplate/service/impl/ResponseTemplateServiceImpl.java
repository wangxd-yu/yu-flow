package org.yu.flow.module.responsetemplate.service.impl;

import cn.hutool.core.util.StrUtil;
import org.yu.flow.auto.dto.PageBean;
import org.yu.flow.module.responsetemplate.cache.ResponseTemplateCacheManager;
import org.yu.flow.module.responsetemplate.domain.ResponseTemplateDO;
import org.yu.flow.module.responsetemplate.dto.ResponseTemplateDTO;
import org.yu.flow.module.responsetemplate.dto.SaveResponseTemplateDTO;
import org.yu.flow.module.responsetemplate.query.ResponseTemplateQueryDTO;
import org.yu.flow.module.responsetemplate.repository.ResponseTemplateRepository;
import org.yu.flow.module.responsetemplate.service.ResponseTemplateService;
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
 * API 响应模板 Service 实现
 *
 * <p>所有写操作（create / update / delete / setDefault）完成后均会触发缓存全量重载，
 * 保证网关运行时读取到的模板数据始终与数据库一致。</p>
 */
@Service
public class ResponseTemplateServiceImpl implements ResponseTemplateService {

    @Resource
    private ResponseTemplateRepository responseTemplateRepository;

    @Resource
    private ResponseTemplateCacheManager cacheManager;

    @Override
    public PageBean<ResponseTemplateDTO> findPage(ResponseTemplateQueryDTO queryDTO) {
        int page = Math.max(queryDTO.getPage() - 1, 0);
        Pageable pageable = PageRequest.of(page, queryDTO.getSize(),
                Sort.by(Sort.Direction.DESC, "createTime"));

        Specification<ResponseTemplateDO> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (StrUtil.isNotBlank(queryDTO.getTemplateName())) {
                predicates.add(cb.like(root.get("templateName"),
                        "%" + queryDTO.getTemplateName() + "%"));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };

        Page<ResponseTemplateDO> result = responseTemplateRepository.findAll(spec, pageable);

        List<ResponseTemplateDTO> content = result.getContent().stream()
                .map(ResponseTemplateDTO::fromDO)
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
    public ResponseTemplateDTO findById(String id) {
        ResponseTemplateDO entity = responseTemplateRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("响应模板不存在，id: " + id));
        return ResponseTemplateDTO.fromDO(entity);
    }

    @Override
    public List<ResponseTemplateDTO> findAll() {
        return responseTemplateRepository.findAll(Sort.by(Sort.Direction.DESC, "isDefault", "createTime"))
                .stream()
                .map(ResponseTemplateDTO::fromDO)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ResponseTemplateDO create(SaveResponseTemplateDTO dto) {
        if (responseTemplateRepository.existsByTemplateName(dto.getTemplateName())) {
            throw new RuntimeException("模板名称已存在: " + dto.getTemplateName());
        }

        // 如果设为默认，先清除其他默认
        if (dto.getIsDefault() != null && dto.getIsDefault() == 1) {
            responseTemplateRepository.clearAllDefault();
        }

        ResponseTemplateDO entity = ResponseTemplateDO.builder()
                .templateName(dto.getTemplateName())
                .successWrapper(dto.getSuccessWrapper())
                .pageWrapper(dto.getPageWrapper())
                .failWrapper(dto.getFailWrapper())
                .isDefault(dto.getIsDefault() == null ? 0 : dto.getIsDefault())
                .remark(dto.getRemark())
                .createTime(LocalDateTime.now())
                .updateTime(LocalDateTime.now())
                .build();

        ResponseTemplateDO saved = responseTemplateRepository.save(entity);
        cacheManager.reloadAll();
        return saved;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ResponseTemplateDO update(String id, SaveResponseTemplateDTO dto) {
        ResponseTemplateDO existing = responseTemplateRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("响应模板不存在，id: " + id));

        // 名称唯一性校验（排除自身）
        if (StrUtil.isNotBlank(dto.getTemplateName())
                && !dto.getTemplateName().equals(existing.getTemplateName())
                && responseTemplateRepository.existsByTemplateNameAndIdNot(dto.getTemplateName(), id)) {
            throw new RuntimeException("模板名称已被占用: " + dto.getTemplateName());
        }

        // 如果本次要设为默认，先清除其他默认
        if (dto.getIsDefault() != null && dto.getIsDefault() == 1 &&
                (existing.getIsDefault() == null || existing.getIsDefault() != 1)) {
            responseTemplateRepository.clearAllDefault();
        }

        if (StrUtil.isNotBlank(dto.getTemplateName())) {
            existing.setTemplateName(dto.getTemplateName());
        }
        existing.setSuccessWrapper(dto.getSuccessWrapper());
        existing.setPageWrapper(dto.getPageWrapper());
        existing.setFailWrapper(dto.getFailWrapper());
        if (dto.getIsDefault() != null) {
            existing.setIsDefault(dto.getIsDefault());
        }
        existing.setRemark(dto.getRemark());
        existing.setUpdateTime(LocalDateTime.now());

        ResponseTemplateDO saved = responseTemplateRepository.save(existing);
        cacheManager.reloadAll();
        return saved;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(String id) {
        ResponseTemplateDO existing = responseTemplateRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("响应模板不存在，id: " + id));

        if (existing.getIsDefault() != null && existing.getIsDefault() == 1) {
            throw new RuntimeException("全局默认模板不允许直接删除，请先将其他模板设为默认");
        }

        responseTemplateRepository.deleteById(id);
        cacheManager.reloadAll();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void setDefault(String id) {
        ResponseTemplateDO existing = responseTemplateRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("响应模板不存在，id: " + id));

        // 先清除所有默认
        responseTemplateRepository.clearAllDefault();

        // 再设置当前模板为默认
        existing.setIsDefault(1);
        existing.setUpdateTime(LocalDateTime.now());
        responseTemplateRepository.save(existing);

        cacheManager.reloadAll();
    }
}
