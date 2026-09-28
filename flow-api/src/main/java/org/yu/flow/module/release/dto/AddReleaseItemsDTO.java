package org.yu.flow.module.release.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class AddReleaseItemsDTO {

    private List<AssetRefDTO> items = new ArrayList<>();

    /** 是否按引用关系自动补齐依赖（调用的接口 / 内部服务、接口的响应模板），默认 true */
    private Boolean includeDependencies;

    /** MANUAL（默认）/ SCAN：来自变更扫描 */
    private String origin;

    @Data
    public static class AssetRefDTO {
        private String assetType;
        private String assetId;
    }
}
