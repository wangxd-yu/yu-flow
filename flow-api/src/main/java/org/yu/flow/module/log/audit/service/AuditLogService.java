package org.yu.flow.module.log.audit.service;

import org.yu.flow.dto.PageBean;
import org.yu.flow.module.log.audit.dto.AuditLogDTO;
import org.yu.flow.module.log.audit.query.AuditLogQueryDTO;

public interface AuditLogService {

    void record(String action, String targetType, String targetId, String detail);

    PageBean<AuditLogDTO> findPage(AuditLogQueryDTO query);
}
