package org.yu.flow.module.api.repository;

import org.yu.flow.module.api.domain.FlowApiDO;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

/**
 * @author yu-flow
 * @date 2025-03-06 00:02
 */
public interface FlowApiRepository extends JpaRepository<FlowApiDO, String>, JpaSpecificationExecutor<FlowApiDO> {

    Optional<FlowApiDO> findByUrlAndPublishStatus(String url, Integer publishStatus);

    // 可根据需要添加自定义查询方法
    FlowApiDO findByName(String name);
    List<FlowApiDO> findByPublishStatus(Integer publishStatus);

    /**
     * 判断是否已存在指定的 URL 和 Method 的 API 记录
     */
    boolean existsByUrlAndMethod(String url, String method);

    /**
     * 判断某个目录下是否有 API（删除目录时校验）
     */
    boolean existsByDirectoryId(String directoryId);

    @Modifying
    @Query("UPDATE FlowApiDO f SET f.directoryId = :directoryId WHERE f.id IN :ids")
    int updateDirectoryIdByIds(@Param("directoryId") String directoryId, @Param("ids") List<String> ids);

    @Modifying
    @Query("UPDATE FlowApiDO f SET f.deleted = 1 WHERE f.id IN :ids")
    int logicDeleteByIds(@Param("ids") List<String> ids);
}
