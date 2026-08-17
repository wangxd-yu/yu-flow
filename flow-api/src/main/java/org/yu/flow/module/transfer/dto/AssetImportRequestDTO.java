package org.yu.flow.module.transfer.dto;

import lombok.Data;

/**
 * 导入请求：预检与正式导入共用同一个包体。
 */
@Data
public class AssetImportRequestDTO {

    private AssetBundle bundle;

    /**
     * 目标环境已存在同 ID 资产时是否覆盖其草稿。
     * <p>false 时这些资产按 SKIP 处理，只导入新增部分。</p>
     */
    private Boolean overwriteExisting;
}
