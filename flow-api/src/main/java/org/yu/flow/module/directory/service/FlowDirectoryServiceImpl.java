package org.yu.flow.module.directory.service;
import org.yu.flow.config.DemoModeGuard;

import org.yu.flow.module.api.repository.FlowApiRepository;
import org.yu.flow.module.directory.domain.FlowDirectoryDO;
import org.yu.flow.module.directory.dto.FlowDirectoryDTO;
import org.yu.flow.module.directory.repository.FlowDirectoryRepository;
import org.yu.flow.module.model.repository.FlowModelInfoRepository;
import org.yu.flow.module.page.repository.PageInfoRepository;
import org.yu.flow.module.serviceflow.repository.FlowServiceFlowRepository;
import org.yu.flow.module.task.repository.FlowTaskRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import cn.hutool.core.util.StrUtil;

import jakarta.annotation.Resource;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 全局目录 Service 实现
 *
 * @author yu-flow
 */
@Service
public class FlowDirectoryServiceImpl implements FlowDirectoryService {

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
    private DemoModeGuard demoModeGuard;

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

        List<FlowDirectoryDTO> allDtos = allDirs.stream()
                .map(FlowDirectoryDTO::fromDO)
                .collect(Collectors.toList());

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
        // 未显式指定域时，继承父目录域
        if (StrUtil.isBlank(directory.getBizType()) && StrUtil.isNotBlank(directory.getParentId())) {
            directoryRepository.findById(directory.getParentId()).ifPresent(parent -> {
                if (StrUtil.isNotBlank(parent.getBizType())) {
                    directory.setBizType(parent.getBizType());
                }
            });
        }
        return directoryRepository.save(directory);
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

        existing.setName(directory.getName());
        if (directory.getSort() != null) {
            existing.setSort(directory.getSort());
        }
        if (directory.getParentId() != null) {
            existing.setParentId(directory.getParentId());
        }
        existing.setUpdateTime(LocalDateTime.now());
        return directoryRepository.save(existing);
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
        directoryRepository.deleteById(id);
    }

    // ================================================================
    // 获取指定目录下所有的子目录ID（包含自身）
    // ================================================================
    @Override
    public List<String> getAllChildIds(String directoryId) {
        List<FlowDirectoryDO> allDirs = directoryRepository.findAll();
        List<String> resultIds = new ArrayList<>();

        if (directoryId != null && !directoryId.isEmpty()) {
            resultIds.add(directoryId);
            findChildIds(directoryId, allDirs, resultIds);
        }
        return resultIds;
    }

    private void findChildIds(String parentId, List<FlowDirectoryDO> allDirs, List<String> resultIds) {
        for (FlowDirectoryDO dir : allDirs) {
            if (dir.getParentId() != null && dir.getParentId().equals(parentId)) {
                resultIds.add(dir.getId());
                findChildIds(dir.getId(), allDirs, resultIds);
            }
        }
    }
}
