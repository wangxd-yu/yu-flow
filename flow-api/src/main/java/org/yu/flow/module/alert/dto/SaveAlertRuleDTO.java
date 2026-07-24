package org.yu.flow.module.alert.dto;

import lombok.Data;

@Data
public class SaveAlertRuleDTO {
    private String name;
    private Integer enabled;
    private String scopeAssetTypes;
    private String window;
    private String minHealth;
    private Integer topN;
    private String channelIds;
    private Integer intervalMinutes;
    private Integer dedupMinutes;
}
