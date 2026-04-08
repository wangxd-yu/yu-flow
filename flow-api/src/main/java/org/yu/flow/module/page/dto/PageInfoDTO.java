package org.yu.flow.module.page.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;
import org.yu.flow.module.page.domain.PageInfoDO;

import java.time.LocalDateTime;

/**
 * 页面信息 DTO
 *
 * @author yu-flow
 */
@Data
public class PageInfoDTO {

    private String id;

    private String directoryId;

    /** 所属目录名称 */
    private String directoryName;

    private String name;

    private String routePath;

    /** 页面 JSON Schema（详情接口返回，列表接口可不返回以减少传输量） */
    private String json;

    /** 状态：0=草稿，1=已发布 */
    private Integer status;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime createTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime updateTime;

    /**
     * DO → DTO
     */
    public static PageInfoDTO fromDO(PageInfoDO entity) {
        if (entity == null) return null;

        PageInfoDTO dto = new PageInfoDTO();
        dto.setId(entity.getId());
        dto.setDirectoryId(entity.getDirectoryId());
        dto.setName(entity.getName());
        dto.setRoutePath(entity.getRoutePath());
        dto.setJson(entity.getJson());
        dto.setStatus(entity.getStatus());
        dto.setCreateTime(entity.getCreateTime());
        dto.setUpdateTime(entity.getUpdateTime());
        return dto;
    }

    /**
     * DTO → DO（用于新建）
     */
    public PageInfoDO toDO() {
        PageInfoDO entity = new PageInfoDO();
        entity.setId(this.id);
        entity.setDirectoryId(this.directoryId);
        entity.setName(this.name);
        entity.setRoutePath(this.routePath);
        entity.setJson(this.json);
        entity.setStatus(this.status);
        entity.setCreateTime(this.createTime);
        entity.setUpdateTime(this.updateTime);
        return entity;
    }
}
