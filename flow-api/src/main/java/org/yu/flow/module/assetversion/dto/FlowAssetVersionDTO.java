package org.yu.flow.module.assetversion.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;
import org.yu.flow.module.assetversion.domain.FlowAssetVersionDO;

import java.time.LocalDateTime;

@Data
public class FlowAssetVersionDTO {

    private String id;
    private String bizType;
    private String assetId;
    private Integer versionNo;
    /** 列表接口默认不返回完整快照，详情/回退时再取 */
    private String snapshot;
    private String source;
    private String remark;
    private String publisher;
    /** 是否为当前线上版本（与主表 published_snapshot 一致） */
    private Boolean current;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime publishTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime createTime;

    public static FlowAssetVersionDTO fromDO(FlowAssetVersionDO entity, boolean includeSnapshot) {
        if (entity == null) {
            return null;
        }
        FlowAssetVersionDTO dto = new FlowAssetVersionDTO();
        dto.setId(entity.getId());
        dto.setBizType(entity.getBizType());
        dto.setAssetId(entity.getAssetId());
        dto.setVersionNo(entity.getVersionNo());
        if (includeSnapshot) {
            dto.setSnapshot(entity.getSnapshot());
        }
        dto.setSource(entity.getSource());
        dto.setRemark(entity.getRemark());
        dto.setPublisher(entity.getPublisher());
        dto.setPublishTime(entity.getPublishTime());
        dto.setCreateTime(entity.getCreateTime());
        return dto;
    }
}
