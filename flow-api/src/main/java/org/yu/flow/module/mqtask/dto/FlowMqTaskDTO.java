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
    /** 日志策略模式：SYSTEM_DEFAULT / OFF / ERROR_ONLY / ALL */
    private String logMode;
    /** 原始报文落库策略：SYSTEM_DEFAULT / FULL / MASK / OFF */
    private String logPayloadMode;
    /** 日志保留天数：null=跟随系统配置，0=永久保留，>0=自定义天数 */
    private Integer logRetentionDays;
    /** 失败重试次数（0=不重试） */
    private Integer retryMax;
    /** 重试间隔毫秒 */
    private Integer retryBackoffMs;
    /** 最终失败时转发的死信 topic/队列 */
    private String deadLetterTopic;
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
        dto.setLogMode(entity.getLogMode());
        dto.setLogPayloadMode(entity.getLogPayloadMode());
        dto.setLogRetentionDays(entity.getLogRetentionDays());
        dto.setRetryMax(entity.getRetryMax() != null ? entity.getRetryMax() : 0);
        dto.setRetryBackoffMs(entity.getRetryBackoffMs() != null ? entity.getRetryBackoffMs() : 1000);
        dto.setDeadLetterTopic(entity.getDeadLetterTopic());
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

    /**
     * 列表投影：清掉 DSL 与发布快照两个大文本字段。
     *
     * <p>草稿是否有未发布变更已在 {@link #fromDO} 里比对完成，列表页只用元信息；
     * 详情仍走 {@code GET /{id}}。</p>
     */
    public FlowMqTaskDTO stripHeavyFields() {
        this.dslContent = null;
        this.publishedSnapshot = null;
        return this;
    }
}
