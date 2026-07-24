package org.yu.flow.module.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.yu.flow.module.api.domain.FlowApiExcelTemplateDO;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ApiExcelTemplateMetaDTO {
    private String id;
    private String apiId;
    private String fileName;
    private String contentType;
    private Integer fileSize;
    private String createTime;
    private String updateTime;
    private boolean present;
    /** 模板内是否检测到列表占位符 {.xxx} */
    private Boolean hasListPlaceholder;
    /** 非阻断提示（如缺少占位符） */
    private String warning;

    public static ApiExcelTemplateMetaDTO fromDO(FlowApiExcelTemplateDO d) {
        return fromDO(d, null, null);
    }

    public static ApiExcelTemplateMetaDTO fromDO(FlowApiExcelTemplateDO d,
                                                 Boolean hasListPlaceholder,
                                                 String warning) {
        if (d == null) {
            return ApiExcelTemplateMetaDTO.builder().present(false).build();
        }
        return ApiExcelTemplateMetaDTO.builder()
                .id(d.getId())
                .apiId(d.getApiId())
                .fileName(d.getFileName())
                .contentType(d.getContentType())
                .fileSize(d.getFileSize())
                .createTime(d.getCreateTime() == null ? null : d.getCreateTime().toString().replace('T', ' '))
                .updateTime(d.getUpdateTime() == null ? null : d.getUpdateTime().toString().replace('T', ' '))
                .present(true)
                .hasListPlaceholder(hasListPlaceholder)
                .warning(warning)
                .build();
    }

    public static ApiExcelTemplateMetaDTO empty(String apiId) {
        return ApiExcelTemplateMetaDTO.builder().apiId(apiId).present(false).build();
    }
}
