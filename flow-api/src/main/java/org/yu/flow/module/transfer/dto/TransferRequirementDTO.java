package org.yu.flow.module.transfer.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 资产包对目标环境的外部依赖（按 code 引用，不随包搬运）。
 */
@Data
public class TransferRequirementDTO {

    public static final String KIND_DATASOURCE = "DATASOURCE";
    public static final String KIND_MQ = "MQ_CONNECTION";
    public static final String KIND_OSS = "OSS_CONNECTION";
    public static final String KIND_TEMPLATE = "RESPONSE_TEMPLATE";
    public static final String KIND_ENV_VAR = "ENV_VAR";
    /** 告警通道，按名称匹配 */
    public static final String KIND_ALERT_CHANNEL = "ALERT_CHANNEL";

    private String kind;

    /** 创建占位时用到的非敏感属性，如数据源 dbType、MQ mqType、告警通道 type */
    private Map<String, String> attributes = new LinkedHashMap<>();

    /** 数据源 / 连接 code、响应模板 ID，或环境变量名 */
    private String key;

    /** 环境变量的说明，导出时带上，告诉运维该填什么 */
    private String remark;

    /** 目标环境是否已具备；导出包里恒为 null，由导入预检填充 */
    private Boolean satisfied;

    /** 引用它的资产名称，方便定位 */
    private List<String> usedBy = new ArrayList<>();
}
