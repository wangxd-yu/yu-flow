package org.yu.flow.module.release.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * 冻结前检查结果；有 ERROR 时不允许冻结。
 */
@Data
public class ReleaseCheckResultDTO {

    public static final String LEVEL_ERROR = "ERROR";
    public static final String LEVEL_WARN = "WARN";

    private boolean passed;

    private List<Issue> issues = new ArrayList<>();

    /** 本次调用是否已执行冻结 */
    private boolean frozen;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Issue {
        private String level;
        private String assetType;
        private String assetId;
        private String assetName;
        private String message;
    }

    public void error(String assetType, String assetId, String assetName, String message) {
        issues.add(new Issue(LEVEL_ERROR, assetType, assetId, assetName, message));
    }

    public void warn(String assetType, String assetId, String assetName, String message) {
        issues.add(new Issue(LEVEL_WARN, assetType, assetId, assetName, message));
    }

    public boolean hasError() {
        return issues.stream().anyMatch(i -> LEVEL_ERROR.equals(i.getLevel()));
    }
}
