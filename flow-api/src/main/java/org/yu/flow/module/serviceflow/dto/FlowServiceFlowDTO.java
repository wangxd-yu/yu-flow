package org.yu.flow.module.serviceflow.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;
import org.yu.flow.module.assetversion.UnpublishedChangeDetector;
import org.yu.flow.module.serviceflow.domain.FlowServiceFlowDO;

import java.time.LocalDateTime;

@Data
public class FlowServiceFlowDTO {

    private String id;
    private String name;
    private String directoryId;
    private String directoryName;
    private Boolean enabled;
    private Boolean logEnabled;
    /** 日志策略模式：SYSTEM_DEFAULT / OFF / ERROR_ONLY / ALL */
    private String logMode;
    private String dslContent;
    /** 服务契约 JSON */
    private String contract;
    private Integer publishStatus;
    private String publishedSnapshot;
    /** 草稿相对已发布是否有未发布变更 */
    private Boolean hasUnpublishedChanges;
    private String info;
    private String tags;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime publishTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime createTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime updateTime;

    public static FlowServiceFlowDTO fromDO(FlowServiceFlowDO entity) {
        if (entity == null) return null;
        FlowServiceFlowDTO dto = new FlowServiceFlowDTO();
        dto.setId(entity.getId());
        dto.setName(entity.getName());
        dto.setDirectoryId(entity.getDirectoryId());
        dto.setEnabled(entity.getEnabled() != null ? entity.getEnabled() : true);
        dto.setLogEnabled(entity.getLogEnabled() != null ? entity.getLogEnabled() : false);
        dto.setLogMode(entity.getLogMode() != null ? entity.getLogMode() : "SYSTEM_DEFAULT");
        dto.setDslContent(entity.getDslContent());
        dto.setContract(entity.getContract());
        dto.setPublishStatus(entity.getPublishStatus() != null ? entity.getPublishStatus() : 0);
        dto.setPublishedSnapshot(entity.getPublishedSnapshot());
        dto.setPublishTime(entity.getPublishTime());
        dto.setInfo(entity.getInfo());
        dto.setTags(entity.getTags());
        dto.setCreateTime(entity.getCreateTime());
        dto.setUpdateTime(entity.getUpdateTime());
        dto.setHasUnpublishedChanges(UnpublishedChangeDetector.serviceHasUnpublishedChanges(entity));
        return dto;
    }

    /**
     * 列表投影：清掉 DSL、契约、发布快照三个大文本字段。
     *
     * <p>草稿是否有未发布变更已在 {@link #fromDO} 里比对完成，列表页与流程编辑器的服务选择器
     * 都只用元信息；带上大字段会让一页 50 条的响应膨胀到数 MB。详情仍走 {@code GET /{id}}。</p>
     */
    public FlowServiceFlowDTO stripHeavyFields() {
        this.dslContent = null;
        this.contract = null;
        this.publishedSnapshot = null;
        return this;
    }
}
