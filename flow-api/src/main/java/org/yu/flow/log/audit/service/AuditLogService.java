package org.yu.flow.log.audit.service;

import org.yu.flow.auto.dto.PageBean;
import org.yu.flow.log.audit.dto.AuditLogDTO;
import org.yu.flow.log.audit.query.AuditLogQueryDTO;

public interface AuditLogService {

    void record(String action, String targetType, String targetId, String detail);

    PageBean<AuditLogDTO> findPage(AuditLogQueryDTO query);
}
