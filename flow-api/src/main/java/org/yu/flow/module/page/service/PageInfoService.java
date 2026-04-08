package org.yu.flow.module.page.service;

import org.yu.flow.auto.dto.PageBean;
import org.yu.flow.module.page.query.PageInfoQueryDTO;
import org.yu.flow.module.page.domain.PageInfoDO;
import org.yu.flow.module.page.dto.PageInfoDTO;

/**
 * 页面信息 Service 接口
 *
 * @author yu-flow
 */
public interface PageInfoService {

    /**
     * 校验页面访问路径是否已被占用
     *
     * @param routePath 页面访问路径
     * @return true-已占用, false-可用
     */
    boolean existsByRoutePath(String routePath);

    /**
     * 分页查询页面列表（支持按 directoryId, name, routePath 过滤）
     */
    PageBean<PageInfoDTO> findPage(PageInfoQueryDTO queryDTO);

    /**
     * 获取页面详情（含 schema）
     */
    PageInfoDTO findById(String id);

    /**
     * 新建页面
     */
    PageInfoDO create(PageInfoDO pageInfo);

    /**
     * 更新基础信息（不含 schema）
     */
    PageInfoDO update(String id, PageInfoDO pageInfo);

    /**
     * 保存设计器生成的 JSON Schema
     */
    PageInfoDO updateJson(String id, String json);

    /**
     * 切换发布状态
     */
    PageInfoDO updateStatus(String id, Integer status);

    /**
     * 克隆页面
     */
    PageInfoDO clonePage(String id);

    /**
     * 删除页面
     */
    void delete(String id);

    /**
     * 批量移动到指定目录
     */
    void batchMove(java.util.List<String> ids, String targetDirectoryId);
}
