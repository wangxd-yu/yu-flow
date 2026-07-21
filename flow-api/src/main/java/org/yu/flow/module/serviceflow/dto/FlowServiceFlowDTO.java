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
        dto.setLogEnabled(entity.getLogEnabled() != null ? entity.getLogEnabled() : true);
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
}
