package org.yu.flow.module.alert.query;

import lombok.Data;

@Data
public class AlertChannelQueryDTO {
    private String name;
    private String type;
    private Integer enabled;
    private int page = 1;
    private int size = 50;
}
