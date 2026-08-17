package org.yu.flow.module.oss.controller;

import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.yu.flow.annotation.YuFlowApi;
import org.yu.flow.auto.dto.PageBean;
import org.yu.flow.dto.R;
import org.yu.flow.module.oss.dto.OssDownloadLogDTO;
import org.yu.flow.module.oss.query.OssDownloadLogQueryDTO;
import org.yu.flow.module.oss.service.OssDownloadLogService;
import org.yu.flow.module.rbac.support.RequirePerm;
import org.yu.flow.module.oss.config.ConditionalOnOssEnabled;

@YuFlowApi
@ConditionalOnOssEnabled
@RestController
@RequestMapping("/flow-api/oss/download-logs")
@RequirePerm({"flow:oss:audit", "flow:oss:admin"})
public class OssDownloadLogController {

    @Resource
    private OssDownloadLogService ossDownloadLogService;

    @GetMapping("/page")
    public R<PageBean<OssDownloadLogDTO>> getPage(OssDownloadLogQueryDTO queryDTO) {
        return R.ok(ossDownloadLogService.findPage(queryDTO));
    }
}
