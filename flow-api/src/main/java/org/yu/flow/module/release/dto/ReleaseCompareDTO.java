package org.yu.flow.module.release.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * 两个版本单的差异。内容是否变化依赖冻结时记录的指纹，未冻结的版本单只比较清单。
 */
@Data
public class ReleaseCompareDTO {

    private String baseCode;
    private String targetCode;

    /** 两个版本单都已冻结时为 true，此时 changed 才有意义 */
    private boolean contentComparable;

    private List<Entry> onlyInBase = new ArrayList<>();
    private List<Entry> onlyInTarget = new ArrayList<>();
    private List<Entry> changed = new ArrayList<>();
    private int unchangedCount;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Entry {
        private String assetType;
        private String assetId;
        private String assetName;
        private String baseAction;
        private String targetAction;
    }
}
