package org.yu.flow.module.release.dto;

import lombok.Data;

import java.util.List;

@Data
public class BatchRunRegressionRequestDTO {
    /** API | TASK | SERVICE */
    private String assetType;
    private List<String> assetIds;
    /** 默认 DEV */
    private String envCode;
    /**
     * 无启用套件时策略：SKIP（默认）| FAIL
     */
    private String missingSuitePolicy;
}
