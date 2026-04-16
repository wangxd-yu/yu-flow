package org.yu.flow.module.responsetemplate.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;
import org.yu.flow.module.responsetemplate.domain.ResponseTemplateDO;

import java.time.LocalDateTime;

/**
 * API 响应模板 DTO (API 响应)
 */
@Data
public class ResponseTemplateDTO {

    private String id;

    /** 模板名称 */
    private String templateName;

    /** 成功包装体 */
    private String successWrapper;

    /** 分页包装体 */
    private String pageWrapper;

    /** 失败包装体 */
    private String failWrapper;

    /** 是否全局默认 (1: 默认, 0: 非默认) */
    private Integer isDefault;

    /** 备注 */
    private String remark;

    private String createBy;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime createTime;

    private String updateBy;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime updateTime;

    /**
     * DO → DTO
     */
    public static ResponseTemplateDTO fromDO(ResponseTemplateDO entity) {
        if (entity == null) return null;

        ResponseTemplateDTO dto = new ResponseTemplateDTO();
        dto.setId(entity.getId());
        dto.setTemplateName(entity.getTemplateName());
        dto.setSuccessWrapper(entity.getSuccessWrapper());
        dto.setPageWrapper(entity.getPageWrapper());
        dto.setFailWrapper(entity.getFailWrapper());
        dto.setIsDefault(entity.getIsDefault());
        dto.setRemark(entity.getRemark());
        dto.setCreateBy(entity.getCreateBy());
        dto.setCreateTime(entity.getCreateTime());
        dto.setUpdateBy(entity.getUpdateBy());
        dto.setUpdateTime(entity.getUpdateTime());
        return dto;
    }

    /**
     * DTO → DO（用于创建/更新时的转换）
     */
    public ResponseTemplateDO toDO() {
        ResponseTemplateDO entity = new ResponseTemplateDO();
        entity.setTemplateName(this.templateName);
        entity.setSuccessWrapper(this.successWrapper);
        entity.setPageWrapper(this.pageWrapper);
        entity.setFailWrapper(this.failWrapper);
        entity.setIsDefault(this.isDefault);
        entity.setRemark(this.remark);
        return entity;
    }
}
