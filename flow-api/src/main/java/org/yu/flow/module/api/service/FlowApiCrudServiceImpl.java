package org.yu.flow.module.api.service;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.CopyOptions;
import cn.hutool.core.util.StrUtil;
import cn.hutool.db.Db;
import cn.hutool.db.Entity;
import org.yu.flow.auto.dto.PageBean;
import org.yu.flow.config.FlowApiCacheManager;
import org.yu.flow.module.api.domain.FlowApiDO;
import org.yu.flow.module.api.domain.FlowServiceDO;
import org.yu.flow.module.api.dto.FlowApiDTO;
import org.yu.flow.module.api.query.FlowApiQueryDTO;
import org.yu.flow.module.api.repository.FlowApiRepository;
import org.yu.flow.module.directory.domain.FlowDirectoryDO;
import org.yu.flow.module.directory.repository.FlowDirectoryRepository;
import org.yu.flow.module.directory.service.FlowDirectoryService;
import org.yu.flow.util.BeanMergeUtil;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import javax.persistence.criteria.Predicate;
import javax.sql.DataSource;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * FlowApi 业务服务实现 —— 仅负责 CRUD 管理操作
 *
 * @author yu-flow
 */
@Service
public class FlowApiCrudServiceImpl implements FlowApiCrudService {

    @Resource
    private DataSource dataSource;

    @Resource
    private FlowApiRepository flowApiRepository;

    @Resource
    private FlowDirectoryRepository flowDirectoryRepository;

    @Resource
    private FlowDirectoryService flowDirectoryService;

    @Resource
    private FlowApiCacheManager flowApiCacheManager;

    // ============================= FlowApiDO CRUD =============================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public FlowApiDO save(FlowApiDO flowApiDO) {
        flowApiDO.setCreateTime(LocalDateTime.now());
        flowApiDO = flowApiRepository.save(flowApiDO);

        if (flowApiDO.getPublishStatus() != null && flowApiDO.getPublishStatus().equals(1)) {
            flowApiCacheManager.publishRefreshEvent();
        }
        return flowApiDO;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public List<FlowApiDO> batchSave(List<FlowApiDO> flowApiDOList) {
        if (flowApiDOList == null || flowApiDOList.isEmpty()) {
            return Collections.emptyList();
        }

        LocalDateTime now = LocalDateTime.now();
        boolean needRefreshCache = false;

        for (FlowApiDO api : flowApiDOList) {
            api.setCreateTime(now);
            if (!needRefreshCache && api.getPublishStatus() != null && api.getPublishStatus().equals(1)) {
                needRefreshCache = true;
            }
        }

        // 统一在单个事务内批量保存，比外层 for 循环单条 save 减少大量事务开销和数据库交互
        List<FlowApiDO> savedList = flowApiRepository.saveAll(flowApiDOList);

        // 如果该批次中有任何已发布的 API，只发出【一次】缓存刷新事件
        // 彻底解决原来的 "100次批量插入 = 100次DB保存 + 100次全网刷新风暴"
        if (needRefreshCache) {
            flowApiCacheManager.publishRefreshEvent();
        }

        return savedList;
    }

    @Override
    @Transactional
    public FlowApiDO update(FlowApiDO flowApiDO) {
        Optional<FlowApiDO> existing = flowApiRepository.findById(flowApiDO.getId());
        if (!existing.isPresent()) {
            throw new RuntimeException("配置不存在，id: " + flowApiDO.getId());
        }
        flowApiDO.setCreateTime(existing.get().getCreateTime());
        flowApiDO.setUpdateTime(LocalDateTime.now());
        flowApiRepository.save(flowApiDO);
        flowApiCacheManager.publishRefreshEvent();
        return flowApiDO;
    }

    @Override
    public void delete(String id) {
        flowApiRepository.deleteById(id);
        flowApiCacheManager.publishRefreshEvent();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void batchDelete(List<String> ids) {
        if (ids != null && !ids.isEmpty()) {
            flowApiRepository.logicDeleteByIds(ids);
            flowApiCacheManager.publishRefreshEvent();
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void batchMove(List<String> ids, String targetDirectoryId) {
        if (ids == null || ids.isEmpty()) {
            return;
        }
        if ("0".equals(targetDirectoryId) || StrUtil.isBlank(targetDirectoryId)) {
            targetDirectoryId = null;
        }
        flowApiRepository.updateDirectoryIdByIds(targetDirectoryId, ids);
        flowApiCacheManager.publishRefreshEvent();
    }

    @Override
    public FlowApiDO findById(String id) {
        return flowApiRepository.findById(id).orElse(null);
    }

    @Override
    public FlowApiDO findByUrl(String url) {
        return flowApiRepository.findByUrlAndPublishStatus(url, 1).orElse(null);
    }

    @Override
    public List<String> findAllUrls() {
        return flowApiRepository.findByPublishStatus(1).stream()
                .map(FlowApiDO::getUrl)
                .collect(Collectors.toList());
    }

    @Override
    public List<FlowApiDTO> findAll() {
        return flowApiRepository.findAll().stream()
                .map(FlowApiDTO::fromDO)
                .collect(Collectors.toList());
    }

    @Override
    public Page<FlowApiDTO> findAll(Pageable pageableIn) {
        Pageable pageable = PageRequest.of(Math.max(pageableIn.getPageNumber() - 1, 0), pageableIn.getPageSize(), Sort.by(Sort.Direction.DESC, "createTime"));
        Page<FlowApiDO> page = flowApiRepository.findAll(pageable);
        List<FlowApiDTO> dtoList = page.getContent().stream()
                .map(FlowApiDTO::fromDO)
                .collect(Collectors.toList());

        // 批量获取 directoryName（内存拼装，避免 N+1）
        enrichDirectoryNames(dtoList);

        return new PageImpl<>(dtoList, pageable, page.getTotalElements());
    }

    @Override
    public PageBean<FlowApiDTO> findPage(FlowApiQueryDTO queryDTO) {
        Pageable pageable = PageRequest.of(queryDTO.getPage(), queryDTO.getSize(), Sort.by(Sort.Direction.DESC, "createTime"));

        Specification<FlowApiDO> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (StrUtil.isNotBlank(queryDTO.getDirectoryId())) {
                List<String> dirIds = flowDirectoryService.getAllChildIds(queryDTO.getDirectoryId());
                if (!dirIds.isEmpty()) {
                    predicates.add(root.get("directoryId").in(dirIds));
                } else {
                    predicates.add(cb.equal(root.get("directoryId"), "-1"));
                }
            }
            if (StrUtil.isNotBlank(queryDTO.getName())) {
                predicates.add(cb.like(root.get("name"), "%" + queryDTO.getName() + "%"));
            }
            if (StrUtil.isNotBlank(queryDTO.getMethod())) {
                predicates.add(cb.equal(root.get("method"), queryDTO.getMethod()));
            }
            if (StrUtil.isNotBlank(queryDTO.getUrl())) {
                predicates.add(cb.like(root.get("url"), "%" + queryDTO.getUrl() + "%"));
            }
            if (queryDTO.getPublishStatus() != null) {
                predicates.add(cb.equal(root.get("publishStatus"), queryDTO.getPublishStatus()));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };

        Page<FlowApiDO> result = flowApiRepository.findAll(spec, pageable);

        List<FlowApiDTO> content = result.getContent().stream()
                .map(FlowApiDTO::fromDO)
                .collect(Collectors.toList());

        // 批量获取 directoryName
        enrichDirectoryNames(content);

        return new PageBean<>(
                content,
                result.getSize(),
                result.getNumber(),
                result.getTotalPages(),
                result.getTotalElements()
        );
    }

    @Override
    public List<FlowApiDTO> findByPublishStatus(Integer publishStatus) {
        return flowApiRepository.findByPublishStatus(publishStatus).stream()
                .map(FlowApiDTO::fromDO)
                .collect(Collectors.toList());
    }

    @Override
    public List<FlowApiDO> findPublishApi() {
        return new ArrayList<>(flowApiRepository.findByPublishStatus(1));
    }

    @Override
    public FlowApiDTO findByName(String name) {
        return FlowApiDTO.fromDO(flowApiRepository.findByName(name));
    }

    @Override
    public boolean existsByUrlAndMethod(String url, String method) {
        return flowApiRepository.existsByUrlAndMethod(url, method);
    }

    // ============================= FlowServiceDO CRUD =============================

    @Override
    public FlowServiceDO findFlowServiceDOById(String id) {
        try {
            Entity entity = Db.use(dataSource).get("flow_service", "id", id);
            if (entity == null) {
                throw new RuntimeException("接口服务 未找到ID为" + id + "的记录");
            }
            CopyOptions copyOptions = CopyOptions.create()
                    .ignoreCase()
                    .setIgnoreNullValue(true)
                    .setIgnoreError(true);

            return BeanUtil.toBean(entity, FlowServiceDO.class, copyOptions);
        } catch (Exception e) {
            throw new RuntimeException("查询FlowService失败", e);
        }
    }

    @Override
    public void saveFlowServiceDO(FlowServiceDO flowServiceDO) {
        try {
            Db.use(dataSource).tx(db -> db.insert(Entity.create("flow_service").parseBean(flowServiceDO, true, true)));
            flowApiCacheManager.publishRefreshEvent();
        } catch (Exception e) {
            throw new RuntimeException("保存FlowService失败", e);
        }
    }

    @Override
    public void updateFlowServiceDO(FlowServiceDO flowServiceDO) {
        try {
            FlowServiceDO dbFlowService = findFlowServiceDOById(flowServiceDO.getId());
            if (dbFlowService == null) {
                throw new RuntimeException("未找到ID为 " + flowServiceDO.getId() + " 的记录");
            }

            BeanMergeUtil.mergeNonNullProperties(flowServiceDO, dbFlowService, true, "createTime");

            Entity entity = Entity.create("flow_service")
                    .parseBean(dbFlowService, true, true)
                    .set("id", dbFlowService.getId());

            Db.use(dataSource).update(
                    entity,
                    Entity.create("flow_service").set("id", dbFlowService.getId())
            );

            flowApiCacheManager.publishRefreshEvent();
        } catch (Exception e) {
            throw new RuntimeException("合并更新FlowService失败", e);
        }
    }

    // ============================= 私有方法 =============================

    /**
     * 批量填充 DTO 列表中的 directoryName（内存拼装，解决 N+1 问题）
     */
    private void enrichDirectoryNames(List<FlowApiDTO> dtoList) {
        Set<String> directoryIds = dtoList.stream()
                .map(FlowApiDTO::getDirectoryId)
                .filter(StrUtil::isNotBlank)
                .collect(Collectors.toSet());

        if (!directoryIds.isEmpty()) {
            Map<String, String> dirMap = flowDirectoryRepository.findAllById(directoryIds).stream()
                    .collect(Collectors.toMap(
                            FlowDirectoryDO::getId,
                            FlowDirectoryDO::getName));
            dtoList.forEach(dto -> dto.setDirectoryName(dirMap.get(dto.getDirectoryId())));
        }
    }
}
