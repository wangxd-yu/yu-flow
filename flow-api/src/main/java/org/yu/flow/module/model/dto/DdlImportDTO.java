package org.yu.flow.module.model.dto;

import lombok.Data;

/**
 * DDL 导入请求 DTO
 */
@Data
public class DdlImportDTO {
    /** ddl 语句 */
    private String ddl;

    /** 数据库类型，可选（例如 mysql, postgresql 等），默认为 mysql，不指定亦尝试兼容解析 */
    private String dbType;
}
