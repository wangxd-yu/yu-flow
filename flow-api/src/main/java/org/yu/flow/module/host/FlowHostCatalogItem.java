package org.yu.flow.module.host;

import cn.hutool.core.util.StrUtil;
import lombok.Builder;
import lombok.Value;

/**
 * 宿主身份目录中的一条可选项（码 + 展示名）。
 */
@Value
@Builder
public class FlowHostCatalogItem {

    /** 写入策略 JSON 的码，如 {@code END_USER}、角色码、部门 ID */
    String value;
    /** 管理端下拉展示名；空则回退为 value */
    String label;
    /** 可选说明 */
    String hint;
    /** 部门树父节点码；空则视为根。其它维度可忽略 */
    String parentId;
    Boolean disabled;

    public String displayLabel() {
        return StrUtil.isNotBlank(label) ? label.trim() : (value == null ? "" : value.trim());
    }
}
