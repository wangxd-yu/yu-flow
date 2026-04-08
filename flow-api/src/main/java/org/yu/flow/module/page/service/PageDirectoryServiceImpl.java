package org.yu.flow.module.page.service;

import org.yu.flow.module.page.domain.PageDirectoryDO;
import org.yu.flow.module.page.dto.PageDirectoryDTO;
import org.yu.flow.module.page.repository.PageDirectoryRepository;
import org.yu.flow.module.page.repository.PageInfoRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 页面目录 Service 实现
 *
 * @author yu-flow
 */
@Service
public class PageDirectoryServiceImpl implements PageDirectoryService {

    @Resource
    private PageDirectoryRepository directoryRepository;

    @Resource
    private PageInfoRepository pageInfoRepository;

    // ================================================================
    // 获取目录树
    // ================================================================
    @Override
    public List<PageDirectoryDTO> getTree() {
        // 1. 查询全部目录
        List<PageDirectoryDO> allDirs = directoryRepository.findAll();

        // 2. 转换为 DTO
        List<PageDirectoryDTO> allDtos = allDirs.stream()
                .map(PageDirectoryDTO::fromDO)
                .collect(Collectors.toList());

        // 3. 按 parentId 分组
        Map<String, List<PageDirectoryDTO>> parentMap = allDtos.stream()
                .filter(d -> d.getParentId() != null)
                .collect(Collectors.groupingBy(PageDirectoryDTO::getParentId));

        // 4. 递归构建树
        allDtos.forEach(dto -> {
            List<PageDirectoryDTO> children = parentMap.get(dto.getId());
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
    public PageDirectoryDO create(PageDirectoryDO directory) {
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
    public PageDirectoryDO update(String id, PageDirectoryDO directory) {
        PageDirectoryDO existing = directoryRepository.findById(id)
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
    // 删除目录（需校验）
    // ================================================================
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(String id) {
        // 校验1：是否有子目录
        if (directoryRepository.existsByParentId(id)) {
            throw new RuntimeException("该目录下还有子目录，请先删除子目录");
        }
        // 校验2：是否有关联页面
        if (pageInfoRepository.existsByDirectoryId(id)) {
            throw new RuntimeException("该目录下还有页面，请先移除或删除相关页面");
        }
        directoryRepository.deleteById(id);
    }
}
