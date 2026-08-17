package org.yu.flow.module.host.dto;

import lombok.Data;
import lombok.experimental.Accessors;
import org.yu.flow.module.host.HostCatalogDimBinding;

import java.util.LinkedHashMap;
import java.util.Map;

@Data
@Accessors(chain = true)
public class HostCatalogOverviewDTO {

    /** 存在 Java SPI 时策略表单走 SPI，保留接口仅供预览/备用 */
    private boolean spiOverride;
    private Map<String, HostCatalogDimBinding> settings = new LinkedHashMap<>();
    private Map<String, HostCatalogApiMetaDTO> apis = new LinkedHashMap<>();
}
