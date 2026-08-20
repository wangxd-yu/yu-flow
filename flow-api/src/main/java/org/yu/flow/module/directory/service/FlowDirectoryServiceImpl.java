package org.yu.flow.module.directory.service;
import org.yu.flow.config.DemoModeGuard;

import org.yu.flow.module.api.repository.FlowApiRepository;
import org.yu.flow.module.api.security.ApiSecurityConfig;
import org.yu.flow.module.api.security.ApiSecurityConfigGuard;
import org.yu.flow.module.directory.domain.FlowDirectoryDO;
import org.yu.flow.module.directory.dto.FlowDirectoryDTO;
import org.yu.flow.module.directory.repository.FlowDirectoryRepository;
import org.yu.flow.module.model.repository.FlowModelInfoRepository;
import org.yu.flow.module.mqtask.repository.FlowMqTaskRepository;
import org.yu.flow.module.page.repository.PageInfoRepository;
import org.yu.flow.module.serviceflow.repository.FlowServiceFlowRepository;
import org.yu.flow.module.task.repository.FlowTaskRepository;
import org.yu.flow.util.FlowObjectMapperUtil;
import org.springframework.stereotype.Service;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.fasterxml.jackson.databind.ObjectMapper;
import cn.hutool.core.util.StrUtil;
import lombok.extern.slf4j.Slf4j;

import jakarta.annotation.Resource;
import java.time.LocalDateTime;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

/**
 * 全局目录 Service 实现
 *
 * @author yu-flow
 */
@Slf4j
@Service
public class FlowDirectoryServiceImpl implements FlowDirectoryService {

    public static final String REFRESH_TOPIC = "flow:directory:cache:refresh:topic";
    private static final ObjectMapper MAPPER = FlowObjectMapperUtil.flowObjectMapper();
    private static final int MAX_DIR_DEPTH = 64;
    /** Redis Pub/Sub 非持久消息，TTL 保证节点漏收广播后也会最终收敛。 */
    private static final long SNAPSHOT_MAX_AGE_NANOS = TimeUnit.MINUTES.toNanos(5);

    @Resource
    private FlowDirectoryRepository directoryRepository;

    @Resource
    private FlowApiRepository flowApiRepository;

    @Resource
    private FlowModelInfoRepository modelInfoRepository;

    @Resource
    private PageInfoRepository pageInfoRepository;

    @Resource
    private FlowTaskRepository flowTaskRepository;

    @Resource
    private FlowServiceFlowRepository flowServiceFlowRepository;

    @Resource
    private FlowMqTaskRepository flowMqTaskRepository;

    @Resource
    private DemoModeGuard demoModeGuard;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    /**
     * 全量目录内存快照：入站热路径沿 parent 向上走内存，避免每层 {@code findById}。
     * 目录增删改后置空，下次解析时 {@code findAll} 一次重建。
     */
    private final AtomicReference<DirectoryChainIndex> chainIndex = new AtomicReference<>();

    // ================================================================
    // 获取目录树
    // ================================================================
    @Override
    public List<FlowDirectoryDTO> getTree() {
        return getTree(null);
    }

    @Override
    public List<FlowDirectoryDTO> getTree(String bizType) {
        List<FlowDirectoryDO> allDirs = directoryRepository.findAll();
        if (StrUtil.isNotBlank(bizType)) {
            allDirs = filterByBizType(allDirs, bizType.trim());
        }

        Map<String, FlowDirectoryDO> byId = allDirs.stream()
                .collect(Collectors.toMap(FlowDirectoryDO::getId, d -> d, (a, b) -> a));

        List<FlowDirectoryDTO> allDtos = allDirs.stream()
                .map(FlowDirectoryDTO::fromDO)
                .collect(Collectors.toList());

        // 有效前缀：根→叶叠加（本批 allDirs 内解析，避免 N+1）
        Map<String, String> effectivePrefixCache = new HashMap<>();
        for (FlowDirectoryDTO dto : allDtos) {
            dto.setEffectivePathPrefix(resolveEffectivePathPrefixInMap(dto.getId(), byId, effectivePrefixCache));
        }

        Map<String, List<FlowDirectoryDTO>> parentMap = allDtos.stream()
                .filter(d -> d.getParentId() != null)
                .collect(Collectors.groupingBy(FlowDirectoryDTO::getParentId));

        allDtos.forEach(dto -> {
            List<FlowDirectoryDTO> children = parentMap.get(dto.getId());
            if (children != null) {
                children.sort((a, b) -> {
                    int sa = a.getSort() == null ? 0 : a.getSort();
                    int sb = b.getSort() == null ? 0 : b.getSort();
                    return Integer.compare(sa, sb);
                });
                dto.setChildren(children);
            }
        });

        return allDtos.stream()
                .filter(d -> d.getParentId() == null)
                .collect(Collectors.toList());
    }

    /**
     * 保留：匹配域 / 共用（bizType 空）节点，以及它们的祖先（保证树完整）。
     */
    private List<FlowDirectoryDO> filterByBizType(List<FlowDirectoryDO> allDirs, String bizType) {
        Map<String, FlowDirectoryDO> byId = allDirs.stream()
                .collect(Collectors.toMap(FlowDirectoryDO::getId, d -> d, (a, b) -> a));

        Set<String> keep = new HashSet<>();
        for (FlowDirectoryDO d : allDirs) {
            if (matchesBizType(d.getBizType(), bizType)) {
                keep.add(d.getId());
            }
        }
        // 补齐祖先
        Set<String> frontier = new HashSet<>(keep);
        while (!frontier.isEmpty()) {
            Set<String> parents = new HashSet<>();
            for (String id : frontier) {
                FlowDirectoryDO d = byId.get(id);
                if (d != null && StrUtil.isNotBlank(d.getParentId()) && keep.add(d.getParentId())) {
                    parents.add(d.getParentId());
                }
            }
            frontier = parents;
        }

        return allDirs.stream().filter(d -> keep.contains(d.getId())).collect(Collectors.toList());
    }

    private static boolean matchesBizType(String dirBizType, String filter) {
        return StrUtil.isBlank(dirBizType) || filter.equalsIgnoreCase(dirBizType);
    }

    // ================================================================
    // 新增目录
    // ================================================================
    @Override
    @Transactional(rollbackFor = Exception.class)
    public FlowDirectoryDO create(FlowDirectoryDO directory) {
        directory.setCreateTime(LocalDateTime.now());
        directory.setUpdateTime(LocalDateTime.now());
        if (directory.getSort() == null) {
            directory.setSort(0);
        }
        directory.setPathPrefix(normalizePathPrefix(directory.getPathPrefix()));
        directory.setSecurityConfig(normalizeSecurityConfigJson(directory.getSecurityConfig()));
        directory.setPrivacyConfig(normalizeSecurityConfigJson(directory.getPrivacyConfig()));
        ApiSecurityConfigGuard.assertCallerPolicyAllowed(directory.getSecurityConfig());
        // 未显式指定域时，继承父目录域
        if (StrUtil.isBlank(directory.getBizType()) && StrUtil.isNotBlank(directory.getParentId())) {
            directoryRepository.findById(directory.getParentId()).ifPresent(parent -> {
                if (StrUtil.isNotBlank(parent.getBizType())) {
                    directory.setBizType(parent.getBizType());
                }
            });
        }
        FlowDirectoryDO saved = directoryRepository.save(directory);
        publishDirectoryChainRefreshAfterCommit();
        return saved;
    }

    // ================================================================
    // 更新目录
    // ================================================================
    @Override
    @Transactional(rollbackFor = Exception.class)
    public FlowDirectoryDO update(String id, FlowDirectoryDO directory) {
        // [Demo 模式] 系统预置目录不可修改
        demoModeGuard.checkModifyOrDelete(id, "目录");
        FlowDirectoryDO existing = directoryRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("目录不存在，id: " + id));

        if (StrUtil.isNotBlank(directory.getName())) {
            existing.setName(directory.getName());
        }
        if (directory.getSort() != null) {
            existing.setSort(directory.getSort());
        }
        if (directory.getParentId() != null) {
            // 空串表示置为根
            existing.setParentId(StrUtil.isBlank(directory.getParentId()) ? null : directory.getParentId());
        }
        // pathPrefix / securityConfig / remark：请求体带字段则更新（允许显式清空）
        if (directory.getPathPrefix() != null) {
            existing.setPathPrefix(normalizePathPrefix(directory.getPathPrefix()));
        }
        if (directory.getSecurityConfig() != null) {
            existing.setSecurityConfig(normalizeSecurityConfigJson(directory.getSecurityConfig()));
            ApiSecurityConfigGuard.assertCallerPolicyAllowed(existing.getSecurityConfig());
        }
        if (directory.getPrivacyConfig() != null) {
            existing.setPrivacyConfig(normalizeSecurityConfigJson(directory.getPrivacyConfig()));
        }
        if (directory.getRemark() != null) {
            existing.setRemark(StrUtil.blankToDefault(directory.getRemark(), null));
        }
        existing.setUpdateTime(LocalDateTime.now());
        FlowDirectoryDO saved = directoryRepository.save(existing);
        publishDirectoryChainRefreshAfterCommit();
        return saved;
    }

    // ================================================================
    // 删除目录（需校验子目录 + 三张核心资产表）
    // ================================================================
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(String id) {
        // [Demo 模式] 系统预置目录不可删除
        demoModeGuard.checkModifyOrDelete(id, "目录");
        // 校验1：是否有子目录
        if (directoryRepository.existsByParentId(id)) {
            throw new RuntimeException("该目录下还有子目录，请先删除子目录");
        }
        // 校验2：是否有关联 API
        if (flowApiRepository.existsByDirectoryId(id)) {
            throw new RuntimeException("该目录下还有 API 接口，请先移除或删除相关接口");
        }
        // 校验3：是否有关联数据模型
        if (modelInfoRepository.existsByDirectoryId(id)) {
            throw new RuntimeException("该目录下还有数据模型，请先移除或删除相关模型");
        }
        // 校验4：是否有关联页面
        if (pageInfoRepository.existsByDirectoryId(id)) {
            throw new RuntimeException("该目录下还有页面，请先移除或删除相关页面");
        }
        // 校验5：是否有关联定时任务
        if (flowTaskRepository.existsByDirectoryId(id)) {
            throw new RuntimeException("该目录下还有定时任务，请先移除或删除相关任务");
        }
        // 校验6：是否有关联内部服务
        if (flowServiceFlowRepository.existsByDirectoryId(id)) {
            throw new RuntimeException("该目录下还有内部服务，请先移除或删除相关服务");
        }
        // 校验7：是否有关联 MQ 任务
        if (flowMqTaskRepository.existsByDirectoryId(id)) {
            throw new RuntimeException("该目录下还有 MQ 任务，请先移除或删除相关任务");
        }
        directoryRepository.deleteById(id);
        publishDirectoryChainRefreshAfterCommit();
    }

    // ================================================================
    // 获取指定目录下所有的子目录ID（包含自身）
    // ================================================================
    @Override
    public List<String> getAllChildIds(String directoryId) {
        List<String> resultIds = new ArrayList<>();
        if (StrUtil.isBlank(directoryId)) {
            return resultIds;
        }
        DirectoryChainIndex snap = snapshot();
        Set<String> seen = new HashSet<>();
        ArrayDeque<String> pending = new ArrayDeque<>();
        seen.add(directoryId);
        pending.add(directoryId);
        resultIds.add(directoryId);
        while (!pending.isEmpty()) {
            String parent = pending.removeFirst();
            for (CachedDir child : snap.childrenByParent.getOrDefault(parent, List.of())) {
                if (seen.add(child.id)) {
                    resultIds.add(child.id);
                    pending.addLast(child.id);
                }
            }
        }
        return resultIds;
    }

    @Override
    public void assertDirectoryBizType(String directoryId, String expectedBizType) {
        if (StrUtil.isBlank(directoryId) || StrUtil.isBlank(expectedBizType)) {
            return;
        }
        FlowDirectoryDO dir = directoryRepository.findById(directoryId)
                .orElseThrow(() -> new RuntimeException("目录不存在: " + directoryId));
        if (StrUtil.isBlank(dir.getBizType())) {
            return; // 共享目录
        }
        if (!expectedBizType.trim().equalsIgnoreCase(dir.getBizType().trim())) {
            throw new RuntimeException("目录「" + dir.getName() + "」属于 "
                    + dir.getBizType() + " 域，不能挂到 " + expectedBizType + " 资产下");
        }
    }

    @Override
    public FlowDirectoryDO getById(String id) {
        if (StrUtil.isBlank(id)) {
            return null;
        }
        return directoryRepository.findById(id).orElse(null);
    }

    @Override
    public String resolveEffectivePathPrefix(String directoryId) {
        if (StrUtil.isBlank(directoryId)) {
            return null;
        }
        DirectoryChainIndex snap = snapshot();
        return snap.mergedPrefix.computeIfAbsent(directoryId, id -> Optional.ofNullable(mergePrefixFromSnapshot(snap, id)))
                .orElse(null);
    }

    @Override
    public ApiSecurityConfig resolveDirectorySecurityOverrides(String directoryId) {
        if (StrUtil.isBlank(directoryId)) {
            ApiSecurityConfig empty = new ApiSecurityConfig();
            empty.setAuthMode(null);
            return empty;
        }
        DirectoryChainIndex snap = snapshot();
        return snap.mergedSecurity.computeIfAbsent(directoryId, id -> mergeSecurityFromSnapshot(snap, id));
    }

    @Override
    public org.yu.flow.module.api.privacy.ApiPrivacyConfig resolveDirectoryPrivacyOverrides(String directoryId) {
        if (StrUtil.isBlank(directoryId)) {
            return new org.yu.flow.module.api.privacy.ApiPrivacyConfig();
        }
        DirectoryChainIndex snap = snapshot();
        return snap.mergedPrivacy.computeIfAbsent(directoryId, id -> mergePrivacyFromSnapshot(snap, id));
    }

    private DirectoryChainIndex snapshot() {
        DirectoryChainIndex current = chainIndex.get();
        if (current != null && current.isFresh()) {
            return current;
        }
        synchronized (this) {
            current = chainIndex.get();
            if (current != null && current.isFresh()) {
                return current;
            }
            List<FlowDirectoryDO> all = directoryRepository.findAll();
            Map<String, CachedDir> byId = new HashMap<>(Math.max(16, all.size() * 2));
            Map<String, List<CachedDir>> childrenByParent = new HashMap<>();
            for (FlowDirectoryDO d : all) {
                if (d == null || StrUtil.isBlank(d.getId())) {
                    continue;
                }
                CachedDir cached = new CachedDir(
                        d.getId(),
                        d.getParentId(),
                        d.getPathPrefix(),
                        parseSecurityConfig(d.getSecurityConfig()),
                        parsePrivacyConfig(d.getPrivacyConfig()));
                byId.put(d.getId(), cached);
                if (StrUtil.isNotBlank(cached.parentId)) {
                    childrenByParent.computeIfAbsent(cached.parentId, ignored -> new ArrayList<>()).add(cached);
                }
            }
            Map<String, List<CachedDir>> immutableChildren = new HashMap<>(childrenByParent.size());
            childrenByParent.forEach((parent, children) ->
                    immutableChildren.put(parent, List.copyOf(children)));
            current = new DirectoryChainIndex(Map.copyOf(byId), Map.copyOf(immutableChildren));
            chainIndex.set(current);
            return current;
        }
    }

    /** Redis 监听器调用：只做本地失效，下次访问按需重建。 */
    public void refreshDirectoryChainCache() {
        chainIndex.set(null);
    }

    /** 本机立即失效；事务提交后广播，让集群所有节点失效。 */
    private void publishDirectoryChainRefreshAfterCommit() {
        refreshDirectoryChainCache();
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    publishDirectoryChainRefresh();
                }
            });
        } else {
            publishDirectoryChainRefresh();
        }
    }

    private void publishDirectoryChainRefresh() {
        // 防止事务期间有请求重新装载了旧快照。
        refreshDirectoryChainCache();
        try {
            stringRedisTemplate.convertAndSend(REFRESH_TOPIC, "REFRESH");
        } catch (Exception e) {
            // Redis 不可用时本节点仍然正确；其他节点通过重启或下次广播收敛。
            log.warn("[FlowDirectory] 目录缓存刷新广播失败，已完成本地失效: {}", e.getMessage());
        }
    }

    private static ApiSecurityConfig mergeSecurityFromSnapshot(DirectoryChainIndex snap, String directoryId) {
        ApiSecurityConfig merged = new ApiSecurityConfig();
        merged.setAuthMode(null);
        String cur = directoryId;
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < MAX_DIR_DEPTH && StrUtil.isNotBlank(cur); i++) {
            if (!seen.add(cur)) {
                break;
            }
            CachedDir d = snap.byId.get(cur);
            if (d == null) {
                break;
            }
            mergeDirectoryLayer(merged, d.security);
            cur = d.parentId;
        }
        return merged;
    }

    private static org.yu.flow.module.api.privacy.ApiPrivacyConfig mergePrivacyFromSnapshot(
            DirectoryChainIndex snap, String directoryId) {
        org.yu.flow.module.api.privacy.ApiPrivacyConfig merged =
                new org.yu.flow.module.api.privacy.ApiPrivacyConfig();
        String cur = directoryId;
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < MAX_DIR_DEPTH && StrUtil.isNotBlank(cur); i++) {
            if (!seen.add(cur)) {
                break;
            }
            CachedDir d = snap.byId.get(cur);
            if (d == null) {
                break;
            }
            boolean stop = org.yu.flow.module.api.privacy.PrivacyConfigMerge.overlay(merged, d.privacy);
            if (stop) {
                break;
            }
            cur = d.parentId;
        }
        return merged;
    }

    private static String mergePrefixFromSnapshot(DirectoryChainIndex snap, String directoryId) {
        List<String> segments = new ArrayList<>();
        String cur = directoryId;
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < MAX_DIR_DEPTH && StrUtil.isNotBlank(cur); i++) {
            if (!seen.add(cur)) {
                break;
            }
            CachedDir d = snap.byId.get(cur);
            if (d == null) {
                break;
            }
            String normalized = normalizePathPrefix(d.pathPrefix);
            if (normalized != null) {
                segments.add(normalized);
            }
            cur = d.parentId;
        }
        if (segments.isEmpty()) {
            return null;
        }
        StringBuilder sb = new StringBuilder();
        for (int i = segments.size() - 1; i >= 0; i--) {
            sb.append(segments.get(i));
        }
        return sb.toString();
    }

    /**
     * 子层（已写入 merged 的字段）优先；仅填充仍为「未设置」的字段。
     */
    private static void mergeDirectoryLayer(ApiSecurityConfig merged, ApiSecurityConfig layer) {
        if (layer == null) {
            return;
        }
        if (StrUtil.isBlank(merged.getAuthMode())
                && StrUtil.isNotBlank(layer.getAuthMode())
                && !"INHERIT".equalsIgnoreCase(layer.getAuthMode().trim())) {
            merged.setAuthMode(layer.getAuthMode().trim());
        }
        if (merged.getAntiReplay() == null && layer.getAntiReplay() != null) {
            merged.setAntiReplay(layer.getAntiReplay());
        }
        if (merged.getRateLimitEnabled() == null && layer.getRateLimitEnabled() != null) {
            merged.setRateLimitEnabled(layer.getRateLimitEnabled());
        }
        if (merged.getRateLimitQps() == null && layer.getRateLimitQps() != null) {
            merged.setRateLimitQps(layer.getRateLimitQps());
        }
        if (merged.getIpAllowlist() == null && layer.getIpAllowlist() != null) {
            merged.setIpAllowlist(layer.getIpAllowlist());
        }
        if (merged.getTimeoutMs() == null && layer.getTimeoutMs() != null) {
            merged.setTimeoutMs(layer.getTimeoutMs());
        }
        if (merged.getCallerPolicy() == null && layer.getCallerPolicy() != null) {
            merged.setCallerPolicy(layer.getCallerPolicy());
        }
    }

    private ApiSecurityConfig parseSecurityConfig(String json) {
        if (StrUtil.isBlank(json)) {
            return null;
        }
        try {
            return MAPPER.readValue(json.trim(), ApiSecurityConfig.class);
        } catch (Exception e) {
            log.warn("[FlowDirectory] 解析 securityConfig 失败: {}", e.getMessage());
            return null;
        }
    }

    private org.yu.flow.module.api.privacy.ApiPrivacyConfig parsePrivacyConfig(String json) {
        if (StrUtil.isBlank(json)) {
            return null;
        }
        try {
            return MAPPER.readValue(json.trim(), org.yu.flow.module.api.privacy.ApiPrivacyConfig.class);
        } catch (Exception e) {
            log.warn("[FlowDirectory] 解析 privacyConfig 失败: {}", e.getMessage());
            return null;
        }
    }

    private static String normalizeSecurityConfigJson(String json) {
        if (json == null) {
            return null;
        }
        String t = json.trim();
        return t.isEmpty() || "{}".equals(t) ? null : t;
    }

    /** 规范化：trim、保证以 / 开头、去掉尾 /；空白 → null */
    public static String normalizePathPrefix(String raw) {
        if (raw == null) {
            return null;
        }
        String v = raw.trim();
        if (v.isEmpty()) {
            return null;
        }
        if (!v.startsWith("/")) {
            v = "/" + v;
        }
        while (v.length() > 1 && v.endsWith("/")) {
            v = v.substring(0, v.length() - 1);
        }
        return v;
    }

    /** 拼接两段已规范化前缀（均以 / 开头、无尾 /） */
    public static String joinPathPrefixes(String parent, String child) {
        if (StrUtil.isBlank(parent)) {
            return StrUtil.isBlank(child) ? null : child;
        }
        if (StrUtil.isBlank(child)) {
            return parent;
        }
        return parent + child;
    }

    private static String resolveEffectivePathPrefixInMap(String id,
                                                          Map<String, FlowDirectoryDO> byId,
                                                          Map<String, String> cache) {
        if (StrUtil.isBlank(id)) {
            return null;
        }
        if (cache.containsKey(id)) {
            return cache.get(id);
        }
        FlowDirectoryDO d = byId.get(id);
        if (d == null) {
            cache.put(id, null);
            return null;
        }
        String parent = resolveEffectivePathPrefixInMap(d.getParentId(), byId, cache);
        String self = normalizePathPrefix(d.getPathPrefix());
        String joined = joinPathPrefixes(parent, self);
        cache.put(id, joined);
        return joined;
    }

    private static final class DirectoryChainIndex {
        final Map<String, CachedDir> byId;
        final Map<String, List<CachedDir>> childrenByParent;
        final long createdAtNanos = System.nanoTime();
        final ConcurrentHashMap<String, ApiSecurityConfig> mergedSecurity = new ConcurrentHashMap<>();
        final ConcurrentHashMap<String, org.yu.flow.module.api.privacy.ApiPrivacyConfig> mergedPrivacy =
                new ConcurrentHashMap<>();
        final ConcurrentHashMap<String, Optional<String>> mergedPrefix = new ConcurrentHashMap<>();

        DirectoryChainIndex(Map<String, CachedDir> byId, Map<String, List<CachedDir>> childrenByParent) {
            this.byId = byId;
            this.childrenByParent = childrenByParent;
        }

        boolean isFresh() {
            return System.nanoTime() - createdAtNanos < SNAPSHOT_MAX_AGE_NANOS;
        }
    }

    private static final class CachedDir {
        final String id;
        final String parentId;
        final String pathPrefix;
        final ApiSecurityConfig security;
        final org.yu.flow.module.api.privacy.ApiPrivacyConfig privacy;

        CachedDir(String id, String parentId, String pathPrefix, ApiSecurityConfig security,
                  org.yu.flow.module.api.privacy.ApiPrivacyConfig privacy) {
            this.id = id;
            this.parentId = parentId;
            this.pathPrefix = pathPrefix;
            this.security = security;
            this.privacy = privacy;
        }
    }
}
