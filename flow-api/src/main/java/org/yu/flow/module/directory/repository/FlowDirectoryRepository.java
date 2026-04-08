package org.yu.flow.module.directory.repository;

import org.yu.flow.module.directory.domain.FlowDirectoryDO;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * 全局目录 Repository
 *
 * @author yu-flow
 */
public interface FlowDirectoryRepository extends JpaRepository<FlowDirectoryDO, String> {

    /**
     * 按父节点查询子目录（按 sort 升序）
     */
    List<FlowDirectoryDO> findByParentIdOrderBySortAsc(String parentId);

    /**
     * 判断某个目录下是否还有子目录
     */
    boolean existsByParentId(String parentId);
}
