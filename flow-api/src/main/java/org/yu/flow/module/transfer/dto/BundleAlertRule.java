package org.yu.flow.module.transfer.dto;

import lombok.Data;
import org.yu.flow.module.alert.domain.AlertRuleDO;

import java.util.ArrayList;
import java.util.List;

/**
 * 告警规则。通道 ID 各环境不同，按通道名称在目标环境重新对应；通道配置（webhook / 密钥）不随包迁移。
 */
@Data
public class BundleAlertRule {

    private AlertRuleDO rule;

    private List<String> channelNames = new ArrayList<>();
}
