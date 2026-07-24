package org.yu.flow.module.api.service;

import org.yu.flow.auto.dto.PageBean;
import org.yu.flow.module.api.domain.FlowApiDO;
import org.yu.flow.module.api.dto.FlowApiDTO;
import org.yu.flow.module.api.query.FlowApiQueryDTO;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;

/**
 * FlowApi 业务服务接口 —— 仅负责 CRUD 管理操作
 *
 * @author yu-flow
 * @date 2025-03-05 23:54
 */
public interface FlowApiCrudService {

    /** 新增 API 配置 */
    FlowApiDO save(FlowApiDO flowApiDO);

    /** 批量新增 API 配置（优化事务和缓存刷新流） */
    List<FlowApiDO> batchSave(List<FlowApiDO> flowApiDOList);

    /** 更新 API 配置 */
    FlowApiDO update(FlowApiDO flowApiDO);

    /** 根据 ID 删除 */
    void delete(String id);

    /** 批量逻辑删除 */
    void batchDelete(List<String> ids);

    /** 批量移动到指定目录 */
    void batchMove(List<String> ids, String targetDirectoryId);

    /** 更新执行日志开关 */
    FlowApiDO updateLogEnabled(String id, boolean enabled);

    /**
     * 更新查询响应缓存配置（即时生效，已发布时刷新 L1 路由缓存中的配置）。
     *
     * @param id          API ID
     * @param cacheConfig cache_config JSON，可为 null 表示关闭/清空
     */
    FlowApiDO updateCacheConfig(String id, String cacheConfig);

    /** 根据 ID 查询 */
    FlowApiDO findById(String id);

    /** 根据 URL 查询已发布的接口 */
    FlowApiDO findByUrl(String url);

    /** 查询所有已发布接口的 URL 列表 */
    List<String> findAllUrls();

    /** 查询全部（DTO 列表） */
    List<FlowApiDTO> findAll();

    /** 分页查询全部 */
    Page<FlowApiDTO> findAll(Pageable pageable);

    /** 分页查询（支持动态条件过滤） */
    PageBean<FlowApiDTO> findPage(FlowApiQueryDTO queryDTO);

    /** 根据发布状态查询 */
    List<FlowApiDTO> findByPublishStatus(Integer publishStatus);

    /** 查询所有已发布的 API DO */
    List<FlowApiDO> findPublishApi();

    /** 根据名称查询 */
    FlowApiDTO findByName(String name);

    /**
     * 校验路径是否与<strong>已发布</strong>接口路由冲突。
     *
     * @param url       待检查路径
     * @param method    请求方法
     * @param excludeId 排除自身（编辑草稿时）
     * @return true-已占用/存在冲突, false-可用
     */
    boolean existsByUrlAndMethod(String url, String method, String excludeId);

    /** @deprecated 请使用 {@link #existsByUrlAndMethod(String, String, String)} */
    default boolean existsByUrlAndMethod(String url, String method) {
        return existsByUrlAndMethod(url, method, null);
    }

    /** 发布 API：将草稿内容冻结为发布快照（envCode 默认 DEV） */
    FlowApiDO publish(String id);

    /** 发布 API（指定逻辑环境门禁） */
    FlowApiDO publish(String id, String envCode);

    /** 下线 API：清除快照，线上停止服务 */
    FlowApiDO unpublish(String id);

    /** 回滚草稿：将发布快照中的内容复制回草稿字段 */
    FlowApiDO rollbackToPublished(String id);

    /** 重新发布：将最新草稿重新冻结为快照 */
    FlowApiDO republish(String id);

    /** 历史版本列表 */
    java.util.List<org.yu.flow.module.assetversion.dto.FlowAssetVersionDTO> listVersions(String id);

    /** 回退线上到指定历史版本（不覆盖草稿） */
    FlowApiDO restoreVersion(String id, String versionId);
}
