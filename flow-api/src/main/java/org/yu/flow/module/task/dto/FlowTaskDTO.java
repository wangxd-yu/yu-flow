package org.yu.flow.module.task.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;
import org.yu.flow.module.assetversion.UnpublishedChangeDetector;
import org.yu.flow.module.task.domain.FlowTaskDO;

import java.time.LocalDateTime;

/**
 * 定时任务对外传输对象
 *
 * @author yu-flow
 */
@Data
public class FlowTaskDTO {

    private String id;
    private String name;
    private String directoryId;
    /** 目录名称（列表展示用，非持久化字段） */
    private String directoryName;
    private String cron;
    private Boolean enabled;
    private Boolean logEnabled;
    /** 日志策略模式：SYSTEM_DEFAULT / OFF / ERROR_ONLY / ALL */
    private String logMode;
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

    public static FlowTaskDTO fromDO(FlowTaskDO entity) {
        if (entity == null) return null;
        FlowTaskDTO dto = new FlowTaskDTO();
        dto.setId(entity.getId());
        dto.setName(entity.getName());
        dto.setDirectoryId(entity.getDirectoryId());
        dto.setCron(entity.getCron());
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
        dto.setHasUnpublishedChanges(UnpublishedChangeDetector.taskHasUnpublishedChanges(entity));
        return dto;
    }
}
