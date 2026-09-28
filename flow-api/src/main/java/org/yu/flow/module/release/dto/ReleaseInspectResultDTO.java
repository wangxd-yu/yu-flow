package org.yu.flow.module.release.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.yu.flow.module.release.support.ModelTableInspector;
import org.yu.flow.module.transfer.dto.TransferReportDTO;

import java.util.ArrayList;
import java.util.List;

/**
 * 发布包预检结果：包信息、逐项动作、依赖检查、发布门禁预演。
 */
@Data
public class ReleaseInspectResultDTO {

    private String releaseCode;
    private String releaseName;
    private String releaseRemark;
    private String sourceEnv;
    private String targetEnv;
    private String exportedBy;
    private String exportedAt;
    private String packageDigest;
    private String changelog;
    private int itemCount;

    /** 逐项动作与依赖检查（复用资产包预检） */
    private TransferReportDTO report;

    /** 将被发布的资产在目标环境的门禁预演 */
    private List<GateItem> gates = new ArrayList<>();

    /** VERIFIED / UNSIGNED / INVALID / UNVERIFIABLE / DISABLED，见 ReleaseSigning */
    private String signatureStatus;

    /** 本环境是否要求发布包必须带有效签名 */
    private boolean signatureRequired;

    /**
     * 需要运维逐项核对的高权限变更（全局宏表达式、开放平台授权增减），非空时导入须显式确认。
     */
    private List<PrivilegedChange> privilegedChanges = new ArrayList<>();

    /** 数据模型对应业务表检查（不存在时附参考 DDL） */
    private List<ModelTableInspector.Result> modelTables = new ArrayList<>();

    /** 为 true 时不允许导入，原因见 blockReasons */
    private boolean blocked;
    private List<String> blockReasons = new ArrayList<>();

    /** 不阻断，但需要运维留意 */
    private List<String> warnings = new ArrayList<>();

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class GateItem {
        private String assetType;
        private String assetId;
        private String assetName;
        private boolean passed;
        private String message;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PrivilegedChange {
        private String assetType;
        /** 宏编码 / 平台编码 */
        private String key;
        private String name;
        private String description;
    }
}
