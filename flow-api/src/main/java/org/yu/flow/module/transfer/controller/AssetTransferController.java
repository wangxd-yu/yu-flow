package org.yu.flow.module.transfer.controller;

import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.yu.flow.annotation.YuFlowApi;
import org.yu.flow.dto.R;
import org.yu.flow.module.rbac.support.RequirePerm;
import org.yu.flow.module.transfer.dto.AssetBundle;
import org.yu.flow.module.transfer.dto.AssetExportRequestDTO;
import org.yu.flow.module.transfer.dto.AssetImportRequestDTO;
import org.yu.flow.module.transfer.dto.TransferReportDTO;
import org.yu.flow.module.transfer.service.AssetTransferService;

import jakarta.annotation.Resource;

/**
 * 接口 / 内部服务 / 定时任务的跨环境批量导出与导入。
 */
@Slf4j
@YuFlowApi
@RestController
@RequestMapping("flow-api/asset-transfer")
@RequirePerm({"flow:api:view", "flow:api:write"})
public class AssetTransferController {

    @Resource
    private AssetTransferService assetTransferService;

    /** 导出为资产包，前端负责另存为 .json 文件 */
    @PostMapping("/export")
    @RequirePerm("flow:api:view")
    public R<AssetBundle> export(@RequestBody AssetExportRequestDTO request) {
        return R.ok(assetTransferService.export(request));
    }

    /** 只算不写，返回逐条处理结论与依赖缺失清单 */
    @PostMapping("/preflight")
    @RequirePerm("flow:api:view")
    public R<TransferReportDTO> preflight(@RequestBody AssetImportRequestDTO request) {
        return R.ok(assetTransferService.preflight(request.getBundle(),
                Boolean.TRUE.equals(request.getOverwriteExisting())));
    }

    /**
     * 实际导入。
     * <p>这里只做「至少持有一类写权限」的粗筛，真正的逐类校验在服务层按包内实际资产类型进行，
     * 避免只有接口权限的账号顺带写入内部服务与定时任务。</p>
     */
    @PostMapping("/import")
    @RequirePerm({"flow:api:write", "flow:service:write", "flow:task:write"})
    public R<TransferReportDTO> importBundle(@RequestBody AssetImportRequestDTO request) {
        return R.ok(assetTransferService.importBundle(request.getBundle(),
                Boolean.TRUE.equals(request.getOverwriteExisting())));
    }
}
