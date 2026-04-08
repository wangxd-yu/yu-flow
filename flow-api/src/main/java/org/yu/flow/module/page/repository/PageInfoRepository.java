package org.yu.flow.module.page.repository;

import org.yu.flow.module.page.domain.PageInfoDO;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

/**
 * 页面信息 Repository
 *
 * @author yu-flow
 */
public interface PageInfoRepository extends JpaRepository<PageInfoDO, String>, JpaSpecificationExecutor<PageInfoDO> {

    /**
     * 判断访问路径是否已存在
     */
    boolean existsByRoutePath(String routePath);

    /**
     * 判断访问路径是否已被其他记录占用（排除自身，用于更新校验）
     */
    boolean existsByRoutePathAndIdNot(String routePath, String id);

    /**
     * 判断某个目录下是否有页面（删除目录时校验）
     */
    boolean existsByDirectoryId(String directoryId);

    @Modifying
    @Query("UPDATE PageInfoDO p SET p.directoryId = :directoryId WHERE p.id IN :ids")
    int updateDirectoryIdByIds(@Param("directoryId") String directoryId, @Param("ids") List<String> ids);
}
