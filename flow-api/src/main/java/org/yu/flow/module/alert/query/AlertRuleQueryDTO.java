package org.yu.flow.module.alert.query;

import lombok.Data;

@Data
public class AlertRuleQueryDTO {
    private String name;
    private Integer enabled;
    private int page = 1;
    private int size = 20;
}
