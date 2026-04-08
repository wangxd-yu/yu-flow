package org.yu.flow.module.directory.service;

import org.yu.flow.module.api.repository.FlowApiRepository;
import org.yu.flow.module.directory.domain.FlowDirectoryDO;
import org.yu.flow.module.directory.dto.FlowDirectoryDTO;
import org.yu.flow.module.directory.repository.FlowDirectoryRepository;
import org.yu.flow.module.model.repository.FlowModelInfoRepository;
import org.yu.flow.module.page.repository.PageInfoRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
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

    // ================================================================
    // 获取目录树
    // ================================================================
    @Override
    public List<FlowDirectoryDTO> getTree() {
        // 1. 查询全部目录
        List<FlowDirectoryDO> allDirs = directoryRepository.findAll();

        // 2. 转换为 DTO
        List<FlowDirectoryDTO> allDtos = allDirs.stream()
                .map(FlowDirectoryDTO::fromDO)
                .collect(Collectors.toList());

        // 3. 按 parentId 分组
        Map<String, List<FlowDirectoryDTO>> parentMap = allDtos.stream()
                .filter(d -> d.getParentId() != null)
                .collect(Collectors.groupingBy(FlowDirectoryDTO::getParentId));

        // 4. 递归构建树
        allDtos.forEach(dto -> {
            List<FlowDirectoryDTO> children = parentMap.get(dto.getId());
            if (children != null) {
                // 按 sort 排序
                children.sort((a, b) -> {
                    int sa = a.getSort() == null ? 0 : a.getSort();
                    int sb = b.getSort() == null ? 0 : b.getSort();
                    return Integer.compare(sa, sb);
                });
                dto.setChildren(children);
            }
        });

        // 5. 返回根节点列表（parentId 为 null 的节点）
        return allDtos.stream()
                .filter(d -> d.getParentId() == null)
                .collect(Collectors.toList());
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
        return directoryRepository.save(directory);
    }

    // ================================================================
    // 更新目录
    // ================================================================
    @Override
    @Transactional(rollbackFor = Exception.class)
    public FlowDirectoryDO update(String id, FlowDirectoryDO directory) {
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
