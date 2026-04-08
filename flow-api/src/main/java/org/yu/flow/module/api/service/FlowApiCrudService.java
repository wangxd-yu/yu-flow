package org.yu.flow.module.api.service;

import org.yu.flow.auto.dto.PageBean;
import org.yu.flow.module.api.domain.FlowApiDO;
import org.yu.flow.module.api.domain.FlowServiceDO;
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

    /** 根据 ID 查询 FlowServiceDO */
    FlowServiceDO findFlowServiceDOById(String id);

    /** 保存 FlowServiceDO */
    void saveFlowServiceDO(FlowServiceDO flowServiceDO);

    /** 更新 FlowServiceDO */
    void updateFlowServiceDO(FlowServiceDO flowServiceDO);

    /**
     * 校验是否已存在指定的 URL 和 Method 的 API 记录
     *
     * @param url    API 路径
     * @param method 请求方法
     * @return true-已占用/存在冲突, false-可用
     */
    boolean existsByUrlAndMethod(String url, String method);
}
