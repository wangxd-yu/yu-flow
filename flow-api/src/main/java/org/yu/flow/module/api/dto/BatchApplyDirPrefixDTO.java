package org.yu.flow.module.api.dto;

import lombok.Data;

import java.util.List;

/**
 * 批量按目录有效前缀重写接口草稿 path。
 */
@Data
public class BatchApplyDirPrefixDTO {
    /** 接口 ID 列表 */
    private List<String> ids;

    /**
     * 从现有 path 剥离的旧前缀（按 / 边界）。
     * 空则服务端用勾选 URL 的最长公共前缀。
     */
    private String oldPrefix;
}
