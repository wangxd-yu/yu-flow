package org.yu.flow.module.model.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;
import org.yu.flow.module.model.domain.FlowModelInfoDO;

import java.time.LocalDateTime;

/**
 * 数据模型信息 DTO
 */
@Data
public class FlowModelInfoDTO {

    private String id;

    private String directoryId;

    /** 所属目录名称 */
    private String directoryName;

    private String name;

    private String tableName;

    /** 关联的动态数据源 code */
    private String datasource;

    /** 关联的动态数据源名称 */
    private String datasourceName;

    /** 模型核心字段元数据配置（JSON） */
    private String fieldsSchema;

    /** 状态：0=停用，1=启用 */
    private Integer status;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime createTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime updateTime;

    /**
     * DO → DTO
     */
    public static FlowModelInfoDTO fromDO(FlowModelInfoDO entity) {
        if (entity == null) return null;

        FlowModelInfoDTO dto = new FlowModelInfoDTO();
        dto.setId(entity.getId());
        dto.setDirectoryId(entity.getDirectoryId());
        dto.setName(entity.getName());
        dto.setTableName(entity.getTableName());
        dto.setDatasource(entity.getDatasource());
        dto.setFieldsSchema(entity.getFieldsSchema());
        dto.setStatus(entity.getStatus());
        dto.setCreateTime(entity.getCreateTime());
        dto.setUpdateTime(entity.getUpdateTime());
        return dto;
    }
}
