package org.yu.flow.engine.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 节点元数据
 * 包含节点的描述信息、分类等
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StepMetadata {
    private String displayName;     // 显示名称 (如 "条件判断")
    private String description;     // 节点描述
    private String category;        // 分类 (如 "control", "service", "data")
    private String icon;            // 图标
    private String version;         // 版本
    private List<String> tags;      // 标签
}
