package org.yu.flow.module.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 字段元数据结构 (前端统一消费)
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FieldMetaSchema {

    /** 字段标识，对应列名（建议驼峰或原名） */
    private String fieldId;

    /** 字段名称/说明，通常使用表的列注释(Comment / Remarks) */
    private String fieldName;

    /** 数据库原生类型，如 VARCHAR, INT, DATETIME 等 */
    private String dbType;

    /** 前端组件类型，如 input-text, input-number, input-date 等 */
    private String uiType;

    /** 是否必填 */
    private Boolean isRequired;

    /** 长度 (可选) */
    private Integer length;

    /** 选项字典 */
    private java.util.List<java.util.Map<String, String>> options;
}
