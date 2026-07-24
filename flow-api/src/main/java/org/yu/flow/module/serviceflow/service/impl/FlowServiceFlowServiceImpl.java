package org.yu.flow.module.serviceflow.service.impl;

import cn.hutool.core.util.StrUtil;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.yu.flow.auto.dto.PageBean;
import org.yu.flow.auto.util.JwtTokenUtil;
import org.yu.flow.config.DemoModeGuard;
import org.yu.flow.module.assetversion.AssetBizType;
import org.yu.flow.module.assetversion.domain.FlowAssetVersionDO;
import org.yu.flow.module.assetversion.dto.FlowAssetVersionDTO;
import org.yu.flow.module.assetversion.service.FlowAssetVersionService;
import org.yu.flow.module.directory.domain.FlowDirectoryDO;
import org.yu.flow.module.directory.repository.FlowDirectoryRepository;
import org.yu.flow.module.directory.service.FlowDirectoryService;
import org.yu.flow.module.serviceflow.domain.FlowServiceFlowDO;
import org.yu.flow.module.serviceflow.dto.FlowServiceFlowDTO;
import org.yu.flow.module.serviceflow.query.FlowServiceFlowQueryDTO;
import org.yu.flow.module.serviceflow.repository.FlowServiceFlowRepository;
import org.yu.flow.module.serviceflow.service.FlowServiceFlowService;
import org.yu.flow.module.serviceflow.service.ServiceFlowReferenceChecker;
import org.yu.flow.module.assetref.FlowReferenceIndex;
import org.yu.flow.log.audit.service.AuditLogService;

import jakarta.annotation.Resource;
import jakarta.persistence.criteria.Predicate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
public class FlowServiceFlowServiceImpl implements FlowServiceFlowService {

    private static final ObjectMapper SNAPSHOT_MAPPER = org.yu.flow.util.FlowObjectMapperUtil.flowObjectMapper();

    @Resource
    private FlowServiceFlowRepository flowServiceFlowRepository;

    @Resource
    private FlowDirectoryService flowDirectoryService;

    @Resource
    private FlowDirectoryRepository flowDirectoryRepository;

    @Resource
    private DemoModeGuard demoModeGuard;

    @Resource
    private ServiceFlowReferenceChecker serviceFlowReferenceChecker;

    @Resource
    private FlowAssetVersionService flowAssetVersionService;

    @Resource
    private FlowReferenceIndex flowReferenceIndex;

    @Resource
    private AuditLogService auditLogService;

    @Override
    @Transactional
    public FlowServiceFlowDO save(FlowServiceFlowDO entity) {
        flowDirectoryService.assertDirectoryBizType(entity.getDirectoryId(), "service");
        if (entity.getEnabled() == null) entity.setEnabled(true);
        if (entity.getLogEnabled() == null) entity.setLogEnabled(false);
        if (entity.getPublishStatus() == null) entity.setPublishStatus(0);
        if (entity.getDeleted() == null) entity.setDeleted(0);
        LocalDateTime now = LocalDateTime.now();
        entity.setCreateTime(now);
        entity.setUpdateTime(now);
        FlowServiceFlowDO saved = flowServiceFlowRepository.save(entity);
        flowReferenceIndex.scheduleRebuildBroadcastAfterCommit();
        return saved;
    }

    @Override
    @Transactional
    public FlowServiceFlowDO update(FlowServiceFlowDO entity) {
        demoModeGuard.checkModifyOrDelete(entity.getId(), "内部服务");
        FlowServiceFlowDO existing = flowServiceFlowRepository.findById(entity.getId())
                .orElseThrow(() -> new RuntimeException("服务不存在: " + entity.getId()));

        if (entity.getName() != null) existing.setName(entity.getName());
        if (entity.getEnabled() != null) existing.setEnabled(entity.getEnabled());
        if (entity.getLogEnabled() != null) existing.setLogEnabled(entity.getLogEnabled());
        if (entity.getDslContent() != null) existing.setDslContent(entity.getDslContent());
        if (entity.getContract() != null) existing.setContract(entity.getContract());
        if (entity.getInfo() != null) existing.setInfo(entity.getInfo());
        if (entity.getTags() != null) existing.setTags(entity.getTags());
        // 允许空串表示移到根目录（Jackson 会反序列化 ""，blankToNull → null）
        if (entity.getDirectoryId() != null) {
            String dirId = StrUtil.isBlank(entity.getDirectoryId()) ? null : entity.getDirectoryId();
            flowDirectoryService.assertDirectoryBizType(dirId, "service");
            existing.setDirectoryId(dirId);
        }
        existing.setUpdateTime(LocalDateTime.now());
        FlowServiceFlowDO saved = flowServiceFlowRepository.save(existing);
        flowReferenceIndex.scheduleRebuildBroadcastAfterCommit();
        return saved;
    }

    @Override
    @Transactional
    public void delete(String id) {
        demoModeGuard.checkModifyOrDelete(id, "内部服务");
        serviceFlowReferenceChecker.assertDeletable(id);
        flowServiceFlowRepository.deleteById(id);
        flowReferenceIndex.scheduleRebuildBroadcastAfterCommit();
    }

    @Override
    @Transactional
    public void batchDelete(List<String> ids) {
        if (ids == null || ids.isEmpty()) {
            return;
        }
        ids.forEach(id -> {
            demoModeGuard.checkModifyOrDelete(id, "内部服务");
            serviceFlowReferenceChecker.assertDeletable(id);
        });
        flowServiceFlowRepository.logicDeleteByIds(ids);
        flowReferenceIndex.scheduleRebuildBroadcastAfterCommit();
    }

    @Override
    public FlowServiceFlowDO findById(String id) {
        return flowServiceFlowRepository.findById(id).orElse(null);
    }

    @Override
    public PageBean<FlowServiceFlowDTO> findPage(FlowServiceFlowQueryDTO queryDTO) {
        Pageable pageable = PageRequest.of(
                queryDTO.getPage(), queryDTO.getSize(),
                Sort.by(Sort.Direction.DESC, "createTime")
        );

        Specification<FlowServiceFlowDO> spec = (root, cq, cb) -> {
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
            if (queryDTO.getEnabled() != null) {
                predicates.add(cb.equal(root.get("enabled"), queryDTO.getEnabled()));
            }
            if (queryDTO.getPublishStatus() != null) {
                predicates.add(cb.equal(root.get("publishStatus"), queryDTO.getPublishStatus()));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };

        Page<FlowServiceFlowDO> page = flowServiceFlowRepository.findAll(spec, pageable);
        List<FlowServiceFlowDTO> items = page.getContent().stream()
                .map(FlowServiceFlowDTO::fromDO)
                .collect(Collectors.toList());
        enrichDirectoryNames(items);
        return new PageBean<>(items, page.getNumber(), page.getSize(),
                page.getTotalPages(), page.getTotalElements());
    }

    @Override
    @Transactional
    public FlowServiceFlowDO enable(String id) {
        demoModeGuard.checkModifyOrDelete(id, "内部服务");
        FlowServiceFlowDO entity = require(id);
        entity.setEnabled(true);
        entity.setUpdateTime(LocalDateTime.now());
        return flowServiceFlowRepository.save(entity);
    }

    @Override
    @Transactional
    public FlowServiceFlowDO disable(String id) {
        demoModeGuard.checkModifyOrDelete(id, "内部服务");
        FlowServiceFlowDO entity = require(id);
        entity.setEnabled(false);
        entity.setUpdateTime(LocalDateTime.now());
        return flowServiceFlowRepository.save(entity);
    }

    @Override
    @Transactional
    public FlowServiceFlowDO updateLogEnabled(String id, boolean logEnabled) {
        FlowServiceFlowDO entity = require(id);
        entity.setLogEnabled(logEnabled);
        entity.setUpdateTime(LocalDateTime.now());
        return flowServiceFlowRepository.save(entity);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public FlowServiceFlowDO publish(String id) {
        demoModeGuard.checkModifyOrDelete(id, "内部服务");
        FlowServiceFlowDO entity = require(id);
        if (StrUtil.isBlank(entity.getDslContent())) {
            throw new RuntimeException("服务 DSL 为空，无法发布");
        }
        assertPublishableServiceDsl(entity.getDslContent());
        String snapshot = buildSnapshot(entity);
        entity.setPublishedSnapshot(snapshot);
        entity.setPublishStatus(1);
        LocalDateTime now = LocalDateTime.now();
        entity.setPublishTime(now);
        entity.setUpdateTime(now);
        FlowServiceFlowDO saved = flowServiceFlowRepository.save(entity);
        flowAssetVersionService.append(AssetBizType.SERVICE, id, snapshot, AssetBizType.SOURCE_PUBLISH, null, JwtTokenUtil.currentUsername());
        flowReferenceIndex.scheduleRebuildBroadcastAfterCommit();
        auditLogService.record("SERVICE_PUBLISH", "SERVICE", id,
                "{\"name\":\"" + StrUtil.nullToEmpty(saved.getName()) + "\"}");
        return saved;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public FlowServiceFlowDO unpublish(String id) {
        demoModeGuard.checkModifyOrDelete(id, "内部服务");
        FlowServiceFlowDO entity = require(id);
        entity.setPublishStatus(0);
        entity.setPublishedSnapshot(null);
        entity.setUpdateTime(LocalDateTime.now());
        FlowServiceFlowDO saved = flowServiceFlowRepository.save(entity);
        flowReferenceIndex.scheduleRebuildBroadcastAfterCommit();
        return saved;
    }

    @Override
    @Transactional(readOnly = true)
    public List<String> listReferenceLabels(String id) {
        require(id);
        return serviceFlowReferenceChecker.findReferenceLabels(id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public FlowServiceFlowDO rollbackToPublished(String id) {
        demoModeGuard.checkModifyOrDelete(id, "内部服务");
        FlowServiceFlowDO entity = require(id);
        if (StrUtil.isBlank(entity.getPublishedSnapshot())) {
            throw new RuntimeException("该服务没有发布快照，无法回滚");
        }
        applySnapshotToDraft(entity, entity.getPublishedSnapshot());
        if (entity.getPublishTime() != null) {
            entity.setUpdateTime(entity.getPublishTime());
        } else {
            entity.setUpdateTime(LocalDateTime.now());
        }
        FlowServiceFlowDO saved = flowServiceFlowRepository.save(entity);
        flowReferenceIndex.scheduleRebuildBroadcastAfterCommit();
        return saved;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public FlowServiceFlowDO republish(String id) {
        return publish(id);
    }

    @Override
    @Transactional(readOnly = true)
    public List<FlowAssetVersionDTO> listVersions(String id) {
        FlowServiceFlowDO entity = require(id);
        return flowAssetVersionService.list(AssetBizType.SERVICE, id, entity.getPublishedSnapshot());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public FlowServiceFlowDO restoreVersion(String id, String versionId) {
        demoModeGuard.checkModifyOrDelete(id, "内部服务");
        FlowServiceFlowDO entity = require(id);
        FlowAssetVersionDO version = flowAssetVersionService.requireOwned(versionId, AssetBizType.SERVICE, id);
        LocalDateTime now = LocalDateTime.now();
        applySnapshotToDraft(entity, version.getSnapshot());
        entity.setPublishedSnapshot(version.getSnapshot());
        entity.setPublishStatus(1);
        entity.setPublishTime(now);
        entity.setUpdateTime(now);
        FlowServiceFlowDO saved = flowServiceFlowRepository.save(entity);
        flowAssetVersionService.append(
                AssetBizType.SERVICE, id, version.getSnapshot(), AssetBizType.SOURCE_ROLLBACK,
                "回退至 v" + version.getVersionNo(), JwtTokenUtil.currentUsername());
        flowReferenceIndex.scheduleRebuildBroadcastAfterCommit();
        return saved;
    }

    /** 完整快照：DSL + 契约 + 启停/日志/基础信息 */
    private String buildSnapshot(FlowServiceFlowDO entity) {
        try {
            ObjectNode snap = SNAPSHOT_MAPPER.createObjectNode();
            snap.put("name", entity.getName());
            snap.put("dslContent", entity.getDslContent());
            snap.put("contract", entity.getContract());
            snap.put("info", entity.getInfo());
            snap.put("tags", entity.getTags());
            if (entity.getEnabled() != null) {
                snap.put("enabled", entity.getEnabled());
            }
            if (entity.getLogEnabled() != null) {
                snap.put("logEnabled", entity.getLogEnabled());
            }
            return SNAPSHOT_MAPPER.writeValueAsString(snap);
        } catch (Exception e) {
            throw new RuntimeException("生成发布快照失败", e);
        }
    }

    /** 兼容旧快照：缺字段时不覆盖现有值 */
    private void applySnapshotToDraft(FlowServiceFlowDO entity, String snapshotJson) {
        try {
            JsonNode snap = SNAPSHOT_MAPPER.readTree(snapshotJson);
            applyText(snap, "name", entity::setName);
            applyText(snap, "dslContent", entity::setDslContent);
            applyText(snap, "contract", entity::setContract);
            applyText(snap, "info", entity::setInfo);
            applyText(snap, "tags", entity::setTags);
            if (snap.has("enabled") && !snap.get("enabled").isNull()) {
                entity.setEnabled(snap.get("enabled").asBoolean());
            }
            if (snap.has("logEnabled") && !snap.get("logEnabled").isNull()) {
                entity.setLogEnabled(snap.get("logEnabled").asBoolean());
            }
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("解析历史版本快照失败", e);
        }
    }

    private static void applyText(JsonNode snap, String field, java.util.function.Consumer<String> setter) {
        if (!snap.has(field)) {
            return;
        }
        JsonNode node = snap.get(field);
        setter.accept(node == null || node.isNull() ? null : node.asText());
    }

    private static String getSnapText(JsonNode snap, String field) {
        JsonNode node = snap.get(field);
        return node != null && !node.isNull() ? node.asText() : null;
    }

    private FlowServiceFlowDO require(String id) {
        return flowServiceFlowRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("服务不存在: " + id));
    }

    /**
     * 发布前校验：必须有且仅有一个 service 入口，且不得混用 request/schedule。
     */
    private void assertPublishableServiceDsl(String dslContent) {
        try {
            JsonNode root = SNAPSHOT_MAPPER.readTree(dslContent);
            JsonNode nodes = root.get("nodes");
            if (nodes == null || !nodes.isArray()) {
                nodes = root.get("steps");
            }
            if (nodes == null || !nodes.isArray() || nodes.isEmpty()) {
                throw new RuntimeException("服务流程为空，无法发布");
            }
            int serviceCount = 0;
            int requestCount = 0;
            int scheduleCount = 0;
            for (JsonNode node : nodes) {
                if (node == null || !node.isObject()) {
                    continue;
                }
                String type = nodeText(node, "type");
                if (type == null && node.has("data") && node.get("data").isObject()) {
                    type = nodeText(node.get("data"), "type");
                }
                if ("service".equals(type)) {
                    serviceCount++;
                } else if ("request".equals(type)) {
                    requestCount++;
                } else if ("schedule".equals(type)) {
                    scheduleCount++;
                }
            }
            if (serviceCount == 0) {
                throw new RuntimeException("缺少 Service 入口节点，无法发布");
            }
            if (serviceCount > 1) {
                throw new RuntimeException("只能有一个 Service 入口节点，无法发布");
            }
            if (requestCount > 0 || scheduleCount > 0) {
                throw new RuntimeException("服务流程不能包含 request / schedule 入口节点，无法发布");
            }
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("服务 DSL 格式不正确，无法发布: " + e.getMessage());
        }
    }

    private static String nodeText(JsonNode node, String field) {
        JsonNode v = node.get(field);
        return v != null && !v.isNull() ? v.asText() : null;
    }

    private void enrichDirectoryNames(List<FlowServiceFlowDTO> dtoList) {
        Set<String> directoryIds = dtoList.stream()
                .map(FlowServiceFlowDTO::getDirectoryId)
                .filter(StrUtil::isNotBlank)
                .collect(Collectors.toSet());
        if (directoryIds.isEmpty()) {
            return;
        }
        Map<String, String> dirMap = flowDirectoryRepository.findAllById(directoryIds).stream()
                .collect(Collectors.toMap(FlowDirectoryDO::getId, FlowDirectoryDO::getName));
        dtoList.forEach(dto -> dto.setDirectoryName(dirMap.get(dto.getDirectoryId())));
    }
}
