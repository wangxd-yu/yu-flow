package org.yu.flow.module.host.dto;

import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
public class HostCatalogApiMetaDTO {

    private String id;
    private String name;
    private String url;
    private String serviceType;
    private Integer publishStatus;
    private Boolean hasUnpublishedChanges;
}
