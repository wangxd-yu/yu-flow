package org.yu.flow.module.api.dto;

import java.time.LocalDateTime;

/**
 * API 列表页投影：仅包含列表展示所需的轻量字段，避免 select 大字段（dslContent、
 * sqlContent、jsonContent、textContent、contract、publishedSnapshot 等）造成接口卡顿。
 */
public interface FlowApiListProjection {

    String getId();

    String getName();

    String getInfo();

    String getUrl();

    String getDatasource();

    String getDirectoryId();

    String getResponseType();

    String getVersion();

    String getMethod();

    String getServiceType();

    String getInterceptMode();

    Integer getPublishStatus();

    Boolean getLogEnabled();

    Integer getLevel();

    String getTags();

    String getTemplateId();

    LocalDateTime getPublishTime();

    Integer getDeleted();

    LocalDateTime getCreateTime();

    LocalDateTime getUpdateTime();

    String getCacheConfig();

    String getSecurityConfig();
}
