package org.yu.flow.module.directory.service;

import org.yu.flow.module.directory.domain.FlowDirectoryDO;
import org.yu.flow.module.directory.dto.FlowDirectoryDTO;

import java.util.List;

/**
 * 全局目录 Service 接口
 *
 * @author yu-flow
 */
public interface FlowDirectoryService {

    /**
     * 获取目录树结构
     *
     * @return 树形 DTO 列表（多根节点场景下返回 List）
     */
    List<FlowDirectoryDTO> getTree();

    /**
     * 按业务域过滤目录树。
     * <p>bizType 为空时返回全部；否则返回「该域目录 + 共用目录（bizType 为空）」及其祖先。
     */
    List<FlowDirectoryDTO> getTree(String bizType);

    /**
     * 新增目录
     */
    FlowDirectoryDO create(FlowDirectoryDO directory);

    /**
     * 更新目录
     */
    FlowDirectoryDO update(String id, FlowDirectoryDO directory);

    /**
     * 删除目录（需校验子目录 / 关联资产）
     */
    void delete(String id);

    /**
     * 获取指定目录下所有的子目录ID（包含自身的ID）
     *
     * @param directoryId 目录ID
     * @return 目录ID列表
     */
    List<String> getAllChildIds(String directoryId);

    /**
     * 校验资产挂载目录归属：directoryId 为空放行；目录 bizType 为空（共享）放行；
     * 否则必须与 expectedBizType 一致（api / task / service / model / page）。
     */
    void assertDirectoryBizType(String directoryId, String expectedBizType);
}
