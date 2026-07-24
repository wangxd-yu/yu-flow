package org.yu.flow.log.audit.dto;

import lombok.Data;
import org.yu.flow.log.audit.domain.AuditLogDO;

import java.time.format.DateTimeFormatter;

@Data
public class AuditLogDTO {
    private String id;
    private String action;
    private String operator;
    private String targetType;
    private String targetId;
    private String detail;
    private String createTime;

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    public static AuditLogDTO fromDO(AuditLogDO d) {
        AuditLogDTO dto = new AuditLogDTO();
        dto.setId(d.getId());
        dto.setAction(d.getAction());
        dto.setOperator(d.getOperator());
        dto.setTargetType(d.getTargetType());
        dto.setTargetId(d.getTargetId());
        dto.setDetail(d.getDetail());
        dto.setCreateTime(d.getCreateTime() == null ? null : FMT.format(d.getCreateTime()));
        return dto;
    }
}
