package org.yu.flow.log.audit.controller;

import org.yu.flow.annotation.YuFlowApi;
import org.yu.flow.auto.dto.PageBean;
import org.yu.flow.dto.R;
import org.yu.flow.log.audit.dto.AuditLogDTO;
import org.yu.flow.log.audit.query.AuditLogQueryDTO;
import org.yu.flow.log.audit.service.AuditLogService;
import org.yu.flow.module.rbac.support.RequirePerm;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.annotation.Resource;

@YuFlowApi
@RestController
@RequestMapping("/flow-api/log/audit")
public class AuditLogController {

    @Resource
    private AuditLogService auditLogService;

    @GetMapping("/page")
    @RequirePerm("log:view")
    public R<PageBean<AuditLogDTO>> page(AuditLogQueryDTO query) {
        return R.ok(auditLogService.findPage(query));
    }
}
