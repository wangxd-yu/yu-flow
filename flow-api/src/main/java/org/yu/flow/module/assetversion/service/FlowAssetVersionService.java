package org.yu.flow.module.assetversion.service;

import org.yu.flow.module.assetversion.domain.FlowAssetVersionDO;
import org.yu.flow.module.assetversion.dto.FlowAssetVersionDTO;

import java.util.List;

/**
 * 资产发布历史版本服务。
 */
public interface FlowAssetVersionService {

    /**
     * 追加一条历史版本，并按系统配置裁剪旧版本。
     *
     * @return 新写入的历史记录
     */
    FlowAssetVersionDO append(String bizType, String assetId, String snapshot, String source, String remark, String publisher);

    /**
     * 列出历史版本（不含 snapshot 正文，按 version_no 降序）。
     *
     * @param currentSnapshot 当前线上快照，用于标记 current
     */
    List<FlowAssetVersionDTO> list(String bizType, String assetId, String currentSnapshot);

    /**
     * 获取含完整 snapshot 的版本详情。
     */
    FlowAssetVersionDO require(String versionId);

    /**
     * 校验版本归属后返回完整实体。
     */
    FlowAssetVersionDO requireOwned(String versionId, String bizType, String assetId);
}
