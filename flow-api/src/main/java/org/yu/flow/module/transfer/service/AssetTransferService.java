package org.yu.flow.module.transfer.service;

import org.yu.flow.module.transfer.dto.AssetBundle;
import org.yu.flow.module.transfer.dto.AssetExportRequestDTO;
import org.yu.flow.module.transfer.dto.TransferReportDTO;

/**
 * 接口 / 内部服务 / 定时任务的跨环境批量导出与导入。
 */
public interface AssetTransferService {

    /**
     * 按勾选的资产生成迁移包（默认带上依赖闭包、目录链与回归套件）。
     */
    AssetBundle export(AssetExportRequestDTO request);

    /**
     * 只算不写：给出每条资产将被新增 / 更新 / 跳过 / 冲突的结论，并检查外部依赖是否齐备。
     */
    TransferReportDTO preflight(AssetBundle bundle, boolean overwriteExisting);

    /**
     * 实际写入。整包一个事务，任一条失败全部回滚；存在冲突项时直接拒绝。
     */
    TransferReportDTO importBundle(AssetBundle bundle, boolean overwriteExisting);

    /**
     * 发布包导入专用：调用方已按 {@code flow:release:import} 鉴权，不再逐类校验资产写权限
     * （运维实施角色只持有导入权限，不具备编排写权限）。
     */
    TransferReportDTO importBundleAuthorized(AssetBundle bundle, boolean overwriteExisting);
}
