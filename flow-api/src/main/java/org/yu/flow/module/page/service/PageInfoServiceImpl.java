package org.yu.flow.module.page.service;

import cn.hutool.core.util.StrUtil;
import org.yu.flow.auto.dto.PageBean;
import org.yu.flow.module.page.query.PageInfoQueryDTO;
import org.yu.flow.module.page.domain.PageInfoDO;
import org.yu.flow.module.page.dto.PageInfoDTO;
import org.yu.flow.module.page.repository.PageInfoRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import org.yu.flow.module.directory.domain.FlowDirectoryDO;
import org.yu.flow.module.directory.repository.FlowDirectoryRepository;
import org.yu.flow.module.directory.service.FlowDirectoryService;

import javax.annotation.Resource;
import javax.persistence.criteria.Predicate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 页面信息 Service 实现
 *
 * @author yu-flow
 */
@Service
public class PageInfoServiceImpl implements PageInfoService {

    @Resource
    private PageInfoRepository pageInfoRepository;

    @Resource
    private FlowDirectoryRepository flowDirectoryRepository;

    @Resource
    private FlowDirectoryService flowDirectoryService;

    // ================================================================
    // 校验页面访问路径是否已被占用
    // ================================================================
    @Override
    public boolean existsByRoutePath(String routePath) {
        return pageInfoRepository.existsByRoutePath(routePath);
    }

    // ================================================================
    // 分页查询（支持动态条件过滤）
    // ================================================================
    @Override
    public PageBean<PageInfoDTO> findPage(PageInfoQueryDTO queryDTO) {
        Pageable pageable = PageRequest.of(queryDTO.getPage(), queryDTO.getSize(), Sort.by(Sort.Direction.DESC, "createTime"));

        Specification<PageInfoDO> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (StrUtil.isNotBlank(queryDTO.getDirectoryId())) {
                List<String> dirIds = flowDirectoryService.getAllChildIds(queryDTO.getDirectoryId());
                if (!dirIds.isEmpty()) {
                    predicates.add(root.get("directoryId").in(dirIds));
                } else {
                    predicates.add(cb.equal(root.get("directoryId"), "-1")); // 用一个不存在的值保证查不到数据
                }
            }
            if (StrUtil.isNotBlank(queryDTO.getName())) {
                predicates.add(cb.like(root.get("name"), "%" + queryDTO.getName() + "%"));
            }
            if (StrUtil.isNotBlank(queryDTO.getRoutePath())) {
                predicates.add(cb.like(root.get("routePath"), "%" + queryDTO.getRoutePath() + "%"));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };

        Page<PageInfoDO> result = pageInfoRepository.findAll(spec, pageable);

        List<PageInfoDTO> content = result.getContent().stream()
                .map(entity -> {
                    PageInfoDTO dto = PageInfoDTO.fromDO(entity);
                    // 列表查询不返回大体积的 schema 字段，减少网络传输
                    dto.setJson(null);
                    return dto;
                })
                .collect(Collectors.toList());

        // 使用纯 JPA 方案 B（内存拼装）解决 N+1 问题：批量获取 directoryName
        Set<String> directoryIds = content.stream()
                .map(PageInfoDTO::getDirectoryId)
                .filter(StrUtil::isNotBlank)
                .collect(Collectors.toSet());
        if (!directoryIds.isEmpty()) {
            Map<String, String> dirMap = flowDirectoryRepository.findAllById(directoryIds).stream()
                    .collect(Collectors.toMap(
                            FlowDirectoryDO::getId,
                            FlowDirectoryDO::getName));
            content.forEach(dto -> dto.setDirectoryName(dirMap.get(dto.getDirectoryId())));
        }

        return new PageBean<>(
                content,
                result.getSize(),
                result.getNumber(),
                result.getTotalPages(),
                result.getTotalElements()
        );
    }

    // ================================================================
    // 获取详情（含 schema，用于设计器回显）
    // ================================================================
    @Override
    public PageInfoDTO findById(String id) {
        PageInfoDO entity = pageInfoRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("页面不存在，id: " + id));
        return PageInfoDTO.fromDO(entity);
    }

    // ================================================================
    // 新建页面
    // ================================================================
    @Override
    @Transactional(rollbackFor = Exception.class)
    public PageInfoDO create(PageInfoDO pageInfo) {
        // 校验 routePath 唯一性
        if (pageInfoRepository.existsByRoutePath(pageInfo.getRoutePath())) {
            throw new RuntimeException("访问路径已存在: " + pageInfo.getRoutePath());
        }

        pageInfo.setStatus(pageInfo.getStatus() == null ? 0 : pageInfo.getStatus());
        pageInfo.setCreateTime(LocalDateTime.now());
        pageInfo.setUpdateTime(LocalDateTime.now());
        return pageInfoRepository.save(pageInfo);
    }

    // ================================================================
    // 更新基础信息（不含 schema）
    // ================================================================
    @Override
    @Transactional(rollbackFor = Exception.class)
    public PageInfoDO update(String id, PageInfoDO pageInfo) {
        PageInfoDO existing = pageInfoRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("页面不存在，id: " + id));

        // 校验 routePath 唯一性（排除自身）
        if (StrUtil.isNotBlank(pageInfo.getRoutePath())
                && !pageInfo.getRoutePath().equals(existing.getRoutePath())
                && pageInfoRepository.existsByRoutePathAndIdNot(pageInfo.getRoutePath(), id)) {
            throw new RuntimeException("访问路径已被占用: " + pageInfo.getRoutePath());
        }

        if (StrUtil.isNotBlank(pageInfo.getName())) {
            existing.setName(pageInfo.getName());
        }
        if (StrUtil.isNotBlank(pageInfo.getRoutePath())) {
            existing.setRoutePath(pageInfo.getRoutePath());
        }
        if (pageInfo.getDirectoryId() != null) {
            existing.setDirectoryId(pageInfo.getDirectoryId());
        }
        existing.setUpdateTime(LocalDateTime.now());
        return pageInfoRepository.save(existing);
    }

    // ================================================================
    // 保存 JSON Schema（核心：设计器调用）
    // ================================================================
    @Override
    @Transactional(rollbackFor = Exception.class)
    public PageInfoDO updateJson(String id, String json) {
        PageInfoDO existing = pageInfoRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("页面不存在，id: " + id));

        existing.setJson(json);
        existing.setUpdateTime(LocalDateTime.now());
        return pageInfoRepository.save(existing);
    }

    // ================================================================
    // 切换发布状态
    // ================================================================
    @Override
    @Transactional(rollbackFor = Exception.class)
    public PageInfoDO updateStatus(String id, Integer status) {
        PageInfoDO existing = pageInfoRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("页面不存在，id: " + id));

        existing.setStatus(status);
        existing.setUpdateTime(LocalDateTime.now());
        return pageInfoRepository.save(existing);
    }

    // ================================================================
    // 克隆页面
    // ================================================================
    @Override
    @Transactional(rollbackFor = Exception.class)
    public PageInfoDO clonePage(String id) {
        PageInfoDO source = pageInfoRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("页面不存在，id: " + id));

        // 构造克隆对象
        PageInfoDO cloned = PageInfoDO.builder()
                .directoryId(source.getDirectoryId())
                .name(source.getName() + "_Copy")
                .routePath(source.getRoutePath() + "_copy_" + System.currentTimeMillis())
                .json(source.getJson())
                .status(0) // 克隆后默认为草稿
                .createTime(LocalDateTime.now())
                .updateTime(LocalDateTime.now())
                .build();

        return pageInfoRepository.save(cloned);
    }

    // ================================================================
    // 删除页面
    // ================================================================
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(String id) {
        if (!pageInfoRepository.existsById(id)) {
            throw new RuntimeException("页面不存在，id: " + id);
        }
        pageInfoRepository.deleteById(id);
    }

    // ================================================================
    // 批量移动页面到指定目录
    // ================================================================
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void batchMove(List<String> ids, String targetDirectoryId) {
        if (ids == null || ids.isEmpty()) {
            return;
        }
        if ("0".equals(targetDirectoryId) || StrUtil.isBlank(targetDirectoryId)) {
            targetDirectoryId = null;
        }
        pageInfoRepository.updateDirectoryIdByIds(targetDirectoryId, ids);
    }
}
