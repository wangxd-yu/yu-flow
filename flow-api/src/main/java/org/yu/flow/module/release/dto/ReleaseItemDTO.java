package org.yu.flow.module.release.dto;

import lombok.Data;

@Data
public class ReleaseItemDTO {

    private String id;
    private String assetType;
    private String assetId;
    private String assetName;
    private String assetKey;
    /** 接口为「METHOD url」，定时任务为 Cron，MQ 任务为「连接 / topic」 */
    private String detail;
    private String action;
    private String origin;
    private String contentHash;

    /** 资产当前是否还存在 */
    private boolean exists;
    private boolean publishable;
    private boolean published;
    private boolean unpublishedChanges;

    /** 冻结后内容已变化，需要重新冻结 */
    private boolean drifted;
}
