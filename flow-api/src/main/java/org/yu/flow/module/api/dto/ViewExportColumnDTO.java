package org.yu.flow.module.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ViewExportColumnDTO {
    private String field;
    private String header;
    private Integer width;
    /** 默认 true */
    private Boolean exportable;
    /** 默认 true；预览表格是否显示 */
    private Boolean visible;
    /** 模板占位符 key（对应 {.key}），默认等于 field */
    private String templateKey;
}
