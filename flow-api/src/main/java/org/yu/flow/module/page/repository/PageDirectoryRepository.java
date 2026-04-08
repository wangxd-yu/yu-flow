package org.yu.flow.module.page.repository;

import org.yu.flow.module.page.domain.PageDirectoryDO;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * 页面目录 Repository
 *
 * @author yu-flow
 */
public interface PageDirectoryRepository extends JpaRepository<PageDirectoryDO, String> {

    /**
     * 按父节点查询子目录（按 sort 升序）
     */
    List<PageDirectoryDO> findByParentIdOrderBySortAsc(String parentId);

    /**
     * 判断某个目录下是否还有子目录
     */
    boolean existsByParentId(String parentId);
}
