package org.yu.flow.module.transfer.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 资产包对目标环境的外部依赖（按 code 引用，不随包搬运）。
 */
@Data
public class TransferRequirementDTO {

    public static final String KIND_DATASOURCE = "DATASOURCE";
    public static final String KIND_MQ = "MQ_CONNECTION";
    public static final String KIND_OSS = "OSS_CONNECTION";
    public static final String KIND_TEMPLATE = "RESPONSE_TEMPLATE";

    private String kind;

    /** 数据源 / 连接 code，或响应模板 ID */
    private String key;

    /** 目标环境是否已具备；导出包里恒为 null，由导入预检填充 */
    private Boolean satisfied;

    /** 引用它的资产名称，方便定位 */
    private List<String> usedBy = new ArrayList<>();
}
