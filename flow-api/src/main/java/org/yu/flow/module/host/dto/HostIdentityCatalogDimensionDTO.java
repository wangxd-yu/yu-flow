package org.yu.flow.module.host.dto;

import lombok.Data;
import lombok.experimental.Accessors;

import java.util.ArrayList;
import java.util.List;

@Data
@Accessors(chain = true)
public class HostIdentityCatalogDimensionDTO {

    /** 宿主声明提供该维度 */
    private boolean supported;
    /** true 时快照不预拉，管理端按关键字远程搜 */
    private boolean searchable;
    private List<HostIdentityCatalogItemDTO> items = new ArrayList<>();
}
