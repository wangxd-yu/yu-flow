package org.yu.flow.module.alert.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;
import org.yu.flow.module.alert.domain.AlertRuleDO;

import java.time.LocalDateTime;

@Data
public class AlertRuleDTO {
    private String id;
    private String name;
    private Integer enabled;
    private String scopeAssetTypes;
    private String window;
    private String minHealth;
    private Integer topN;
    private String channelIds;
    private Integer intervalMinutes;
    private Integer dedupMinutes;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime createTime;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime updateTime;

    public static AlertRuleDTO fromDO(AlertRuleDO e) {
        if (e == null) return null;
        AlertRuleDTO d = new AlertRuleDTO();
        d.setId(e.getId());
        d.setName(e.getName());
        d.setEnabled(e.getEnabled());
        d.setScopeAssetTypes(e.getScopeAssetTypes());
        d.setWindow(e.getWindow());
        d.setMinHealth(e.getMinHealth());
        d.setTopN(e.getTopN());
        d.setChannelIds(e.getChannelIds());
        d.setIntervalMinutes(e.getIntervalMinutes());
        d.setDedupMinutes(e.getDedupMinutes());
        d.setCreateTime(e.getCreateTime());
        d.setUpdateTime(e.getUpdateTime());
        return d;
    }
}
