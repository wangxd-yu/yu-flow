package org.yu.flow.module.alert.dto;

import lombok.Data;

@Data
public class SaveAlertChannelDTO {
    private String name;
    /** WEBHOOK | EMAIL */
    private String type;
    private String configJson;
    private Integer enabled;
}
