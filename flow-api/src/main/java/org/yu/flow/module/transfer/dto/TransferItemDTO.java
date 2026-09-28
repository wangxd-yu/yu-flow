package org.yu.flow.module.transfer.dto;

import lombok.Data;

/**
 * 预检 / 导入报告中的单条资产处理结果。
 */
@Data
public class TransferItemDTO {

    public static final String ACTION_CREATE = "CREATE";
    public static final String ACTION_UPDATE = "UPDATE";
    public static final String ACTION_SKIP = "SKIP";
    /** 无法自动处理，必须人工介入；存在 CONFLICT 时整包禁止导入 */
    public static final String ACTION_CONFLICT = "CONFLICT";
    /** 版本单下线项：目标环境撤销发布 / 停用，不物理删除 */
    public static final String ACTION_OFFLINE = "OFFLINE";

    /** API / SERVICE / TASK / MQ_TASK / PAGE / MODEL / ... / DIRECTORY / REGRESSION_SUITE */
    private String assetType;

    private String id;

    private String name;

    private String action;

    private String message;

    /** 更新项相对目标环境当前内容变化的字段（发布包预检填充） */
    private java.util.List<String> changedFields;

    public static TransferItemDTO of(String assetType, String id, String name, String action, String message) {
        TransferItemDTO item = new TransferItemDTO();
        item.setAssetType(assetType);
        item.setId(id);
        item.setName(name);
        item.setAction(action);
        item.setMessage(message);
        return item;
    }
}
