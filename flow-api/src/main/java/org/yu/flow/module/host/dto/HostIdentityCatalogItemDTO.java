package org.yu.flow.module.host.dto;

import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 管理端下拉一条选项。
 */
@Data
@Accessors(chain = true)
public class HostIdentityCatalogItemDTO {

    private String value;
    private String label;
    private String hint;
    /** 部门树父节点码；空则视为根 */
    private String parentId;
    private Boolean disabled;
    /** HOST = 宿主目录；FLOW = 引擎补齐（目前仅 OPEN_APP） */
    private String source;
}
