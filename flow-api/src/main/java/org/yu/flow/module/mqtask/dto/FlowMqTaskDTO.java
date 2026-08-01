package org.yu.flow.module.mqtask.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;
import org.yu.flow.module.assetversion.UnpublishedChangeDetector;
import org.yu.flow.module.mqtask.domain.FlowMqTaskDO;

import java.time.LocalDateTime;

/**
 * MQ 任务对外传输对象
 *
 * @author yu-flow
 */
@Data
public class FlowMqTaskDTO {

    private String id;
    private String name;
    private String directoryId;
    /** 目录名称（列表展示用，非持久化字段） */
    private String directoryName;
    private String connectionCode;
    private String topic;
    private String consumerGroup;
    private Integer concurrency;
    private Boolean enabled;
    private Boolean logEnabled;
    /** 日志保留天数：null=跟随系统配置，0=永久保留，>0=自定义天数 */
    private Integer logRetentionDays;
    private String dslContent;
    private Integer publishStatus;
    private String publishedSnapshot;
    private Boolean hasUnpublishedChanges;
    private String info;
    private String tags;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime publishTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime createTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime updateTime;

    public static FlowMqTaskDTO fromDO(FlowMqTaskDO entity) {
        if (entity == null) return null;
        FlowMqTaskDTO dto = new FlowMqTaskDTO();
        dto.setId(entity.getId());
        dto.setName(entity.getName());
        dto.setDirectoryId(entity.getDirectoryId());
        dto.setConnectionCode(entity.getConnectionCode());
        dto.setTopic(entity.getTopic());
        dto.setConsumerGroup(entity.getConsumerGroup());
        dto.setConcurrency(entity.getConcurrency() != null ? entity.getConcurrency() : 1);
        dto.setEnabled(entity.getEnabled() != null ? entity.getEnabled() : true);
        dto.setLogEnabled(entity.getLogEnabled() != null ? entity.getLogEnabled() : false);
        dto.setLogRetentionDays(entity.getLogRetentionDays());
        dto.setDslContent(entity.getDslContent());
        dto.setPublishStatus(entity.getPublishStatus() != null ? entity.getPublishStatus() : 0);
        dto.setPublishedSnapshot(entity.getPublishedSnapshot());
        dto.setPublishTime(entity.getPublishTime());
        dto.setInfo(entity.getInfo());
        dto.setTags(entity.getTags());
        dto.setCreateTime(entity.getCreateTime());
        dto.setUpdateTime(entity.getUpdateTime());
        dto.setHasUnpublishedChanges(UnpublishedChangeDetector.mqTaskHasUnpublishedChanges(entity));
        return dto;
    }
}
