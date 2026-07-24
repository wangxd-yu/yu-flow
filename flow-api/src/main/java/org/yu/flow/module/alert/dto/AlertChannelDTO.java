package org.yu.flow.module.alert.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;
import org.yu.flow.module.alert.domain.AlertChannelDO;

import java.time.LocalDateTime;

@Data
public class AlertChannelDTO {
    private String id;
    private String name;
    private String type;
    private String configJson;
    private Integer enabled;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime createTime;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime updateTime;

    public static AlertChannelDTO fromDO(AlertChannelDO e) {
        if (e == null) return null;
        AlertChannelDTO d = new AlertChannelDTO();
        d.setId(e.getId());
        d.setName(e.getName());
        d.setType(e.getType());
        d.setConfigJson(e.getConfigJson());
        d.setEnabled(e.getEnabled());
        d.setCreateTime(e.getCreateTime());
        d.setUpdateTime(e.getUpdateTime());
        return d;
    }
}
