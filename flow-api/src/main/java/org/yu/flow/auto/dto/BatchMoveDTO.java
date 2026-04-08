package org.yu.flow.auto.dto;

import lombok.Data;
import java.util.List;

/**
 * 批量移动 DTO
 */
@Data
public class BatchMoveDTO {
    /**
     * 要移动的 ID 列表
     */
    private List<String> ids;

    /**
     * 目标目录 ID
     */
    private String targetDirectoryId;
}
