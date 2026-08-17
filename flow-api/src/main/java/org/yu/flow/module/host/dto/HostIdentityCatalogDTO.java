package org.yu.flow.module.host.dto;

import lombok.Data;
import lombok.experimental.Accessors;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 宿主身份目录快照。{@code available} 表示至少启用了一个维度；策略表单只展示 {@code supported=true} 的维。
 */
@Data
@Accessors(chain = true)
public class HostIdentityCatalogDTO {

    private boolean available;
    /** key = {@link org.yu.flow.module.host.FlowHostCatalogDimension} 名 */
    private Map<String, HostIdentityCatalogDimensionDTO> dimensions = new LinkedHashMap<>();
}
