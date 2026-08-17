package org.yu.flow.module.host.dto;

import lombok.Data;
import org.yu.flow.module.host.HostCatalogDimBinding;

import java.util.Map;

@Data
public class SaveHostCatalogSettingsDTO {

    /** key = USER_TYPE / ROLE / PERMISSION / DEPT / USER */
    private Map<String, HostCatalogDimBinding> settings;
}
