package org.yu.flow.module.release.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 加入下线项。资产在本环境可能已删除，因此名称与匹配键由调用方给出（来自变更扫描或手工填写）。
 */
@Data
public class AddOfflineItemsDTO {

    private List<Item> items = new ArrayList<>();

    @Data
    public static class Item {
        private String assetType;
        private String assetId;
        private String assetName;
        /** 开放平台下线按平台编码匹配目标环境 */
        private String assetKey;
    }
}
