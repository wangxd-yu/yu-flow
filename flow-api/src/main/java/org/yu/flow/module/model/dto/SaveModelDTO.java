package org.yu.flow.module.model.dto;

import lombok.Data;

import javax.validation.constraints.NotBlank;

/**
 * 新建和更新数据模型的 DTO
 */
@Data
public class SaveModelDTO {

    /** 主键，更新时必传 */
    private String id;

    /** 所属目录 ID */
    private String directoryId;

    /** 模型中文名，如：用户信息 */
    private String name;

    /** 底层物理表名，如：t_user */
    private String tableName;

    /** 关联的动态数据源 code */
    @NotBlank(message = "关联的数据源Code不能为空")
    private String datasource;

    /** 状态：0停用, 1启用 */
    private Integer status;

    /**
     * 模型核心字段元数据配置。
     * 建议在 DTO 中使用 String 类型接收来自前端的大段 JSON 格式字符，
     * Controller 接收后再由服务层或其他工具类解析业务校验。
     */
    private String fieldsSchema;

}
