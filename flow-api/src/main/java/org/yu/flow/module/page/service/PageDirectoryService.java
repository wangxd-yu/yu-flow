package org.yu.flow.module.page.service;

import org.yu.flow.module.page.domain.PageDirectoryDO;
import org.yu.flow.module.page.dto.PageDirectoryDTO;

import java.util.List;

/**
 * 页面目录 Service 接口
 *
 * @author yu-flow
 */
public interface PageDirectoryService {

    /**
     * 获取目录树结构
     *
     * @return 树形 DTO 列表（多根节点场景下返回 List）
     */
    List<PageDirectoryDTO> getTree();

    /**
     * 新增目录
     */
    PageDirectoryDO create(PageDirectoryDO directory);

    /**
     * 更新目录
     */
    PageDirectoryDO update(String id, PageDirectoryDO directory);

    /**
     * 删除目录（需校验子目录 / 关联页面）
     */
    void delete(String id);
}
