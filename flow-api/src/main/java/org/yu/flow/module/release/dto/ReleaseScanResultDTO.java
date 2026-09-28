package org.yu.flow.module.release.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * 变更扫描结果：自基线以来有变化的资产（建议更新）与基线中已被删除的资产（建议下线）。
 */
@Data
public class ReleaseScanResultDTO {

    /** 扫描起点 */
    private String since;

    /** 基线版本号；手工指定起点时为空 */
    private String baselineCode;

    private List<Candidate> candidates = new ArrayList<>();

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Candidate {
        private String assetType;
        private String assetId;
        private String assetKey;
        private String name;
        private String detail;
        /** UPSERT / OFFLINE */
        private String action;
        private String reason;
        /** 不可选的原因写在 reason 里（如未发布、敏感配置） */
        private boolean selectable;
    }
}
