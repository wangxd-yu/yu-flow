package org.yu.flow.module.open.dto;

import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
public class OpenGrantUpdateDTO {
    /** 全量替换授权的 apiId 列表 */
    private List<String> apiIds;
    /**
     * 可选：apiId → allow_methods（空/缺省=跟随接口发布 method）。
     * 值如 {@code GET} 或 {@code GET,POST}。
     */
    private Map<String, String> allowMethodsByApiId;
}
