package org.yu.flow.module.host;

import cn.hutool.core.util.StrUtil;
import lombok.Data;

/**
 * 单个身份目录维度的绑定（启用、字段映射、是否远程搜）。
 */
@Data
public class HostCatalogDimBinding {

    private boolean enabled;
    private String valueField = "value";
    private String labelField = "label";
    /** 部门树父节点字段，默认 parentId */
    private String parentField = "parentId";
    private boolean searchable;

    public String resolvedValueField() {
        return StrUtil.blankToDefault(valueField, "value").trim();
    }

    public String resolvedLabelField() {
        return StrUtil.blankToDefault(labelField, "label").trim();
    }

    public String resolvedParentField() {
        return StrUtil.blankToDefault(parentField, "parentId").trim();
    }

    public static HostCatalogDimBinding disabledDefault() {
        HostCatalogDimBinding b = new HostCatalogDimBinding();
        b.setEnabled(false);
        b.setValueField("value");
        b.setLabelField("label");
        b.setParentField("parentId");
        b.setSearchable(false);
        return b;
    }
}
