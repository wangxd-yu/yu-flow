package org.yu.flow.module.responsetemplate.dto;

import lombok.Data;

import javax.validation.constraints.NotBlank;

/**
 * 新建和更新 API 响应模板的 DTO
 */
@Data
public class SaveResponseTemplateDTO {

    /** 主键，更新时必传 */
    private String id;

    /** 模板名称（如：标准App响应、ERP对接格式），不能为空 */
    @NotBlank(message = "模板名称不能为空")
    private String templateName;

    /** 成功包装体 JSON 模板 */
    private String successWrapper;

    /** 分页包装体 JSON 模板 */
    private String pageWrapper;

    /** 失败包装体 JSON 模板 */
    private String failWrapper;

    /** 是否设为全局默认 (1: 默认, 0: 非默认) */
    private Integer isDefault;

    /** 备注 */
    private String remark;
}
