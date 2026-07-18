package org.yu.flow.module.api.service;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.yu.flow.config.DemoModeGuard;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.CopyOptions;
import cn.hutool.core.util.StrUtil;
import cn.hutool.db.Db;
import cn.hutool.db.Entity;
import org.yu.flow.auto.dto.PageBean;
import org.yu.flow.config.FlowApiCacheManager;
import org.yu.flow.module.api.cache.ApiResponseCacheService;
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

import jakarta.annotation.Resource;
import jakarta.persistence.criteria.Predicate;
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

    @Resource
    private DemoModeGuard demoModeGuard;

    @Resource
    private ApiResponseCacheService apiResponseCacheService;

    // ============================= FlowApiDO CRUD =============================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public FlowApiDO save(FlowApiDO flowApiDO) {
        // [Demo 模式] 禁止新建写入类 DB API
        demoModeGuard.checkApiResponseType(flowApiDO.getResponseType());

        if (flowApiDO.getLogEnabled() == null) {
            flowApiDO.setLogEnabled(true);
        }
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
            if (api.getLogEnabled() == null) {
                api.setLogEnabled(true);
            }
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
        // [Demo 模式] 系统预置 API 不可修改；禁止改为写入类 API
        demoModeGuard.checkModifyOrDelete(flowApiDO.getId(), "API 接口");
        demoModeGuard.checkApiResponseType(flowApiDO.getResponseType());

        Optional<FlowApiDO> existing = flowApiRepository.findById(flowApiDO.getId());
        if (!existing.isPresent()) {
            throw new RuntimeException("配置不存在，id: " + flowApiDO.getId());
        }
        FlowApiDO dbRecord = existing.get();
        flowApiDO.setCreateTime(dbRecord.getCreateTime());
        flowApiDO.setUpdateTime(LocalDateTime.now());

        // ── 版本快照策略 ──
        // 保留已有的发布快照和发布时间，草稿编辑不影响线上
        flowApiDO.setPublishedSnapshot(dbRecord.getPublishedSnapshot());
        flowApiDO.setPublishTime(dbRecord.getPublishTime());
        if (flowApiDO.getLogEnabled() == null) {
            flowApiDO.setLogEnabled(dbRecord.getLogEnabled());
        }
        if (flowApiDO.getCacheConfig() == null) {
            flowApiDO.setCacheConfig(dbRecord.getCacheConfig());
        }
        boolean cacheConfigChanged = !Objects.equals(
                StrUtil.nullToEmpty(flowApiDO.getCacheConfig()),
                StrUtil.nullToEmpty(dbRecord.getCacheConfig()));
        boolean cacheWasEnabled = isResponseCacheEnabled(dbRecord.getCacheConfig());
        boolean cacheNowEnabled = isResponseCacheEnabled(flowApiDO.getCacheConfig());

        flowApiRepository.save(flowApiDO);

        // 如果当前是未发布状态，仍然刷新缓存（兑容旧逻辑）
        // 已发布状态下编辑草稿不刷新缓存（快照隔离）
        // 例外：cacheConfig 为运行时配置，变更需即时刷新 L1
        if (flowApiDO.getPublishStatus() == null || flowApiDO.getPublishStatus() != 1 || cacheConfigChanged) {
            flowApiCacheManager.publishRefreshEvent();
        }
        // 停用缓存，或缓存配置有变更时，清空该接口已有响应缓存
        if (cacheConfigChanged && (cacheWasEnabled || cacheNowEnabled)) {
            apiResponseCacheService.evictAll(flowApiDO.getId());
        }
        return flowApiDO;
    }

    @Override
    public void delete(String id) {
        // [Demo 模式] 系统预置 API 不可删除
        demoModeGuard.checkModifyOrDelete(id, "API 接口");
        flowApiRepository.deleteById(id);
        apiResponseCacheService.evictAll(id);
        flowApiCacheManager.publishRefreshEvent();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void batchDelete(List<String> ids) {
        if (ids != null && !ids.isEmpty()) {
            // [Demo 模式] 逐一检查，只要有一个受保护的 ID 就整体拒绝
            ids.forEach(id -> demoModeGuard.checkModifyOrDelete(id, "API 接口"));
            flowApiRepository.logicDeleteByIds(ids);
            ids.forEach(apiResponseCacheService::evictAll);
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
    @Transactional(rollbackFor = Exception.class)
    public FlowApiDO updateLogEnabled(String id, boolean enabled) {
        demoModeGuard.checkModifyOrDelete(id, "API 接口");
        FlowApiDO api = flowApiRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("API 不存在，id: " + id));
        api.setLogEnabled(enabled);
        FlowApiDO saved = flowApiRepository.save(api);
        if (saved.getPublishStatus() != null && saved.getPublishStatus() == 1) {
            flowApiCacheManager.publishRefreshEvent();
        }
        return saved;
    }

    /** 判断 cache_config JSON 是否启用响应缓存 */
    private boolean isResponseCacheEnabled(String cacheConfigJson) {
        if (StrUtil.isBlank(cacheConfigJson)) {
            return false;
        }
        try {
            var cfg = apiResponseCacheService.parseConfig(cacheConfigJson);
            return cfg != null && cfg.isEnabled();
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public FlowApiDO updateCacheConfig(String id, String cacheConfig) {
        demoModeGuard.checkModifyOrDelete(id, "API 接口");
        FlowApiDO api = flowApiRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("API 不存在，id: " + id));
        // 校验 JSON 合法性（空串视为清空）
        if (StrUtil.isNotBlank(cacheConfig)) {
            if (apiResponseCacheService.parseConfig(cacheConfig) == null) {
                throw new IllegalArgumentException("cacheConfig JSON 无效");
            }
        } else {
            cacheConfig = null;
        }
        String oldConfig = api.getCacheConfig();
        api.setCacheConfig(cacheConfig);
        api.setUpdateTime(LocalDateTime.now());
        FlowApiDO saved = flowApiRepository.save(api);
        if (saved.getPublishStatus() != null && saved.getPublishStatus() == 1) {
            flowApiCacheManager.publishRefreshEvent();
        }
        // 配置变更后清掉旧响应缓存，避免 key 规则不一致残留
        if (!Objects.equals(StrUtil.nullToEmpty(oldConfig), StrUtil.nullToEmpty(cacheConfig))) {
            apiResponseCacheService.evictAll(id);
        }
        return saved;
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
                result.getNumber(),
                result.getSize(),
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

    // ============================= 发布/下线/回滚 =============================

    private static final ObjectMapper SNAPSHOT_MAPPER = new ObjectMapper();

    /**
     * 发布 API：将草稿内容冻结为发布快照
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public FlowApiDO publish(String id) {
        FlowApiDO api = flowApiRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("API 不存在，id: " + id));

        // 生成快照 JSON
        api.setPublishedSnapshot(buildSnapshot(api));
        api.setPublishTime(LocalDateTime.now());
        api.setPublishStatus(1);
        flowApiRepository.save(api);

        // 刷新缓存，线上生效
        flowApiCacheManager.publishRefreshEvent();
        return api;
    }

    /**
     * 下线 API：清除快照，线上停止服务
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public FlowApiDO unpublish(String id) {
        FlowApiDO api = flowApiRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("API 不存在，id: " + id));

        api.setPublishStatus(0);
        api.setPublishedSnapshot(null);
        flowApiRepository.save(api);

        apiResponseCacheService.evictAll(id);
        flowApiCacheManager.publishRefreshEvent();
        return api;
    }

    /**
     * 回滚草稿：将发布快照中的内容复制回草稿字段
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public FlowApiDO rollbackToPublished(String id) {
        FlowApiDO api = flowApiRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("API 不存在，id: " + id));

        if (api.getPublishedSnapshot() == null) {
            throw new RuntimeException("该 API 没有发布快照，无法回滚");
        }

        try {
            com.fasterxml.jackson.databind.JsonNode snap = SNAPSHOT_MAPPER.readTree(api.getPublishedSnapshot());
            api.setDslContent(getSnapText(snap, "dslContent"));
            api.setSqlContent(getSnapText(snap, "sqlContent"));
            api.setJsonContent(getSnapText(snap, "jsonContent"));
            api.setTextContent(getSnapText(snap, "textContent"));
            api.setContract(getSnapText(snap, "contract"));
            api.setServiceType(getSnapText(snap, "serviceType"));
            api.setDatasource(getSnapText(snap, "datasource"));
            api.setResponseType(getSnapText(snap, "responseType"));
            api.setUpdateTime(api.getPublishTime()); // 将 updateTime 对齐到发布时间，消除变更标记
        } catch (Exception e) {
            throw new RuntimeException("解析发布快照失败", e);
        }

        flowApiRepository.save(api);
        return api;
    }

    /**
     * 重新发布：将最新草稿重新冻结为快照
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public FlowApiDO republish(String id) {
        return publish(id);
    }

    /**
     * 构建快照 JSON
     */
    private String buildSnapshot(FlowApiDO api) {
        try {
            ObjectNode snap = SNAPSHOT_MAPPER.createObjectNode();
            snap.put("serviceType", api.getServiceType());
            snap.put("dslContent", api.getDslContent());
            snap.put("sqlContent", api.getSqlContent());
            snap.put("jsonContent", api.getJsonContent());
            snap.put("textContent", api.getTextContent());
            snap.put("datasource", api.getDatasource());
            snap.put("responseType", api.getResponseType());
            snap.put("contract", api.getContract());
            return SNAPSHOT_MAPPER.writeValueAsString(snap);
        } catch (Exception e) {
            throw new RuntimeException("生成发布快照失败", e);
        }
    }

    private String getSnapText(com.fasterxml.jackson.databind.JsonNode snap, String field) {
        com.fasterxml.jackson.databind.JsonNode node = snap.get(field);
        return node != null && !node.isNull() ? node.asText() : null;
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
