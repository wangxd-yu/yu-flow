package org.yu.flow.module.api.repository;

import org.yu.flow.module.api.domain.FlowApiDO;
import org.yu.flow.module.api.dto.FlowApiListProjection;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
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
     * @deprecated 路由冲突请用服务层基于发布快照的查重
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

    /** 粗筛：DSL / 发布快照中可能引用某 serviceId 的 API */
    @Query("SELECT a FROM FlowApiDO a WHERE "
            + "(a.dslContent IS NOT NULL AND a.dslContent LIKE CONCAT('%', :needle, '%')) "
            + "OR (a.publishedSnapshot IS NOT NULL AND a.publishedSnapshot LIKE CONCAT('%', :needle, '%'))")
    List<FlowApiDO> findPossibleServiceFlowRefs(@Param("needle") String needle);

    /** 查询所有配置了 API 级日志保留天数的 API（id + logRetentionDays），供日志清理任务使用 */
    @Query("SELECT f.id, f.logRetentionDays FROM FlowApiDO f WHERE f.logRetentionDays IS NOT NULL")
    List<Object[]> findLogRetentionOverrides();

    /**
     * 列表页投影查询：仅返回列表展示所需的轻量字段，避免 select 大字段造成接口卡顿。
     */
    @Query("SELECT f.id AS id, f.name AS name, f.info AS info, f.url AS url, f.datasource AS datasource, "
            + "f.directoryId AS directoryId, f.responseType AS responseType, f.version AS version, "
            + "f.method AS method, f.serviceType AS serviceType, f.interceptMode AS interceptMode, "
            + "f.publishStatus AS publishStatus, f.logEnabled AS logEnabled, f.level AS level, "
            + "f.tags AS tags, f.templateId AS templateId, f.publishTime AS publishTime, "
            + "f.deleted AS deleted, f.createTime AS createTime, f.updateTime AS updateTime, "
            + "f.cacheConfig AS cacheConfig, f.securityConfig AS securityConfig "
            + "FROM FlowApiDO f")
    Page<FlowApiListProjection> findPageWithoutLargeFields(Pageable pageable);

    /**
     * 列表页条件投影查询：带过滤条件的轻量列表查询，不加载大字段。
     */
    @Query(value = "SELECT f.id AS id, f.name AS name, f.info AS info, f.url AS url, f.datasource AS datasource, "
            + "f.directoryId AS directoryId, f.responseType AS responseType, f.version AS version, "
            + "f.method AS method, f.serviceType AS serviceType, f.interceptMode AS interceptMode, "
            + "f.publishStatus AS publishStatus, f.logEnabled AS logEnabled, f.level AS level, "
            + "f.tags AS tags, f.templateId AS templateId, f.publishTime AS publishTime, "
            + "f.deleted AS deleted, f.createTime AS createTime, f.updateTime AS updateTime, "
            + "f.cacheConfig AS cacheConfig, f.securityConfig AS securityConfig "
            + "FROM FlowApiDO f "
            + "WHERE (:directoryIdsEmpty = true OR f.directoryId IN :directoryIds) "
            + "AND (CAST(:name AS string) IS NULL OR f.name LIKE CONCAT('%', CAST(:name AS string), '%')) "
            + "AND (CAST(:method AS string) IS NULL OR f.method = CAST(:method AS string)) "
            + "AND (CAST(:url AS string) IS NULL OR f.url LIKE CONCAT('%', CAST(:url AS string), '%')) "
            + "AND (:publishStatusEmpty = true OR f.publishStatus = :publishStatus) "
            + "AND (CAST(:serviceType AS string) IS NULL OR f.serviceType = CAST(:serviceType AS string))",
            countQuery = "SELECT COUNT(f) FROM FlowApiDO f "
                    + "WHERE (:directoryIdsEmpty = true OR f.directoryId IN :directoryIds) "
                    + "AND (CAST(:name AS string) IS NULL OR f.name LIKE CONCAT('%', CAST(:name AS string), '%')) "
                    + "AND (CAST(:method AS string) IS NULL OR f.method = CAST(:method AS string)) "
                    + "AND (CAST(:url AS string) IS NULL OR f.url LIKE CONCAT('%', CAST(:url AS string), '%')) "
                    + "AND (:publishStatusEmpty = true OR f.publishStatus = :publishStatus) "
                    + "AND (CAST(:serviceType AS string) IS NULL OR f.serviceType = CAST(:serviceType AS string))")
    Page<FlowApiListProjection> findPageWithoutLargeFields(
            @Param("directoryIds") List<String> directoryIds,
            @Param("directoryIdsEmpty") boolean directoryIdsEmpty,
            @Param("name") String name,
            @Param("method") String method,
            @Param("url") String url,
            @Param("publishStatusEmpty") boolean publishStatusEmpty,
            @Param("publishStatus") int publishStatus,
            @Param("serviceType") String serviceType,
            Pageable pageable);
}
