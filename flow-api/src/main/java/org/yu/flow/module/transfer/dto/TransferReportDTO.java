package org.yu.flow.module.transfer.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 预检与导入共用的报告：{@code dryRun=true} 表示只算不写。
 */
@Data
public class TransferReportDTO {

    private boolean dryRun;

    /** 存在 CONFLICT 项，导入会被拒绝 */
    private boolean blocked;

    private int createCount;

    private int updateCount;

    private int skipCount;

    private int conflictCount;

    private String exportedAt;

    private String exportedBy;

    private String sourceEnv;

    private String contentSource;

    private List<TransferItemDTO> items = new ArrayList<>();

    /** 目标环境缺失的外部依赖（satisfied=false 的才需要关注） */
    private List<TransferRequirementDTO> requirements = new ArrayList<>();

    private List<String> warnings = new ArrayList<>();
}
