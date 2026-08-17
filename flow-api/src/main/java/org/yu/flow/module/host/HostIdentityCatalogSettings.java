package org.yu.flow.module.host;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 宿主身份目录配置（存 {@code flow_sys_config.HOST_IDENTITY_CATALOG}）。
 */
public class HostIdentityCatalogSettings {

    private final Map<FlowHostCatalogDimension, HostCatalogDimBinding> dimensions = new LinkedHashMap<>();

    public HostIdentityCatalogSettings() {
        for (FlowHostCatalogDimension d : FlowHostCatalogDimension.values()) {
            dimensions.put(d, HostCatalogDimBinding.disabledDefault());
        }
    }

    public Map<FlowHostCatalogDimension, HostCatalogDimBinding> dimensions() {
        return dimensions;
    }

    public HostCatalogDimBinding get(FlowHostCatalogDimension dimension) {
        return dimensions.computeIfAbsent(dimension, k -> HostCatalogDimBinding.disabledDefault());
    }

    public void put(FlowHostCatalogDimension dimension, HostCatalogDimBinding binding) {
        dimensions.put(dimension, binding != null ? binding : HostCatalogDimBinding.disabledDefault());
    }

    public boolean anyEnabled() {
        return dimensions.values().stream().anyMatch(HostCatalogDimBinding::isEnabled);
    }
}
