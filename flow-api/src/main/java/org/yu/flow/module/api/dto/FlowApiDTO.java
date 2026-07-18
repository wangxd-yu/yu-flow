package org.yu.flow.module.api.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;
import org.yu.flow.module.api.domain.FlowApiDO;

import java.time.LocalDateTime;

@Data
public class FlowApiDTO {
    private String id;
    private String name;
    private String info;
    private String url;
    private String datasource;
    private String directoryId;
    /** 所属目录名称 */
    private String directoryName;
    private String responseType;
    private String version;
    /** @deprecated 使用 dslContent/sqlContent/jsonContent/textContent 替代 */
    @Deprecated
    private String config;
    private String dslContent;
    private String sqlContent;
    private String jsonContent;
    private String textContent;
    private String method;
    private String serviceType;
    private Integer publishStatus;
    private Boolean logEnabled;
    /** 查询响应 Redis 缓存配置 JSON */
    private String cacheConfig;
    private Integer level;
    private String contract;
    private String tags;
    private String templateId;
    private String customSuccessWrapper;
    private String customPageWrapper;
    private String customFailWrapper;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime publishTime;

    /** 是否存在未发布的草稿变更（前端展示用） */
    private Boolean hasUnpublishedChanges;

    private Integer deleted = 0;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime createTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime updateTime;

    // 转换方法
    public static FlowApiDTO fromDO(FlowApiDO configDO) {
        if (configDO == null) return null;
        FlowApiDTO dto = new FlowApiDTO();
        dto.setId(configDO.getId());
        dto.setName(configDO.getName());
        dto.setInfo(configDO.getInfo());
        dto.setUrl(configDO.getUrl());
        dto.setDatasource(configDO.getDatasource());
        dto.setDirectoryId(configDO.getDirectoryId());
        dto.setResponseType(configDO.getResponseType());
        dto.setVersion(configDO.getVersion());
        dto.setDslContent(configDO.getDslContent());
        dto.setSqlContent(configDO.getSqlContent());
        dto.setJsonContent(configDO.getJsonContent());
        dto.setTextContent(configDO.getTextContent());
        dto.setMethod(configDO.getMethod());
        dto.setServiceType(configDO.getServiceType());
        dto.setPublishStatus(configDO.getPublishStatus());
        dto.setLogEnabled(configDO.getLogEnabled() == null || configDO.getLogEnabled());
        dto.setCacheConfig(configDO.getCacheConfig());
        dto.setLevel(configDO.getLevel());
        dto.setContract(configDO.getContract());
        dto.setTags(configDO.getTags());
        dto.setTemplateId(configDO.getTemplateId());
        dto.setCustomSuccessWrapper(configDO.getCustomSuccessWrapper());
        dto.setCustomPageWrapper(configDO.getCustomPageWrapper());
        dto.setCustomFailWrapper(configDO.getCustomFailWrapper());
        dto.setPublishTime(configDO.getPublishTime());
        dto.setDeleted(configDO.getDeleted());
        dto.setCreateTime(configDO.getCreateTime());
        dto.setUpdateTime(configDO.getUpdateTime());
        // 判断草稿是否与线上快照有差异
        dto.setHasUnpublishedChanges(
                configDO.getPublishStatus() != null
                        && configDO.getPublishStatus() == 1
                        && configDO.getPublishedSnapshot() != null
                        && configDO.getUpdateTime() != null
                        && configDO.getPublishTime() != null
                        && configDO.getUpdateTime().isAfter(configDO.getPublishTime())
        );
        return dto;
    }

    public FlowApiDO toDO() {
        FlowApiDO configDO = new FlowApiDO();
        configDO.setId(this.getId());
        configDO.setName(this.getName());
        configDO.setInfo(this.getInfo());
        configDO.setUrl(this.getUrl());
        configDO.setDatasource(this.getDatasource());
        configDO.setDirectoryId(this.getDirectoryId());
        configDO.setResponseType(this.getResponseType());
        configDO.setVersion(this.getVersion());
        configDO.setDslContent(this.getDslContent());
        configDO.setSqlContent(this.getSqlContent());
        configDO.setJsonContent(this.getJsonContent());
        configDO.setTextContent(this.getTextContent());
        configDO.setMethod(this.getMethod());
        configDO.setServiceType(this.getServiceType());
        configDO.setPublishStatus(this.getPublishStatus());
        configDO.setLogEnabled(this.getLogEnabled());
        configDO.setCacheConfig(this.getCacheConfig());
        configDO.setLevel(this.getLevel());
        configDO.setContract(this.getContract());
        configDO.setTags(this.getTags());
        configDO.setTemplateId(this.getTemplateId());
        configDO.setCustomSuccessWrapper(this.getCustomSuccessWrapper());
        configDO.setCustomPageWrapper(this.getCustomPageWrapper());
        configDO.setCustomFailWrapper(this.getCustomFailWrapper());
        configDO.setDeleted(this.getDeleted());
        configDO.setCreateTime(this.getCreateTime());
        configDO.setUpdateTime(this.getUpdateTime());
        return configDO;
    }
}
