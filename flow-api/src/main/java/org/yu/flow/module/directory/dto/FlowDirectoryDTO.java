package org.yu.flow.module.directory.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;
import org.yu.flow.module.directory.domain.FlowDirectoryDO;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 全局目录 DTO（树形结构）
 *
 * @author yu-flow
 */
@Data
public class FlowDirectoryDTO {

    private String id;

    private String parentId;

    private String name;

    private Integer sort;

    /** 子目录列表（用于构造树形结构） */
    private List<FlowDirectoryDTO> children = new ArrayList<>();

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime createTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime updateTime;

    /**
     * DO → DTO（不含 children，children 由 Service 层递归构建）
     */
    public static FlowDirectoryDTO fromDO(FlowDirectoryDO entity) {
        if (entity == null) return null;

        FlowDirectoryDTO dto = new FlowDirectoryDTO();
        dto.setId(entity.getId());
        dto.setParentId(entity.getParentId());
        dto.setName(entity.getName());
        dto.setSort(entity.getSort());
        dto.setCreateTime(entity.getCreateTime());
        dto.setUpdateTime(entity.getUpdateTime());
        return dto;
    }
}
