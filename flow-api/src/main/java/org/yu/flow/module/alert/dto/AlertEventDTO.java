package org.yu.flow.module.alert.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;
import org.yu.flow.module.alert.domain.AlertEventDO;

import java.time.LocalDateTime;

@Data
public class AlertEventDTO {
    private String id;
    private String ruleId;
    private String ruleName;
    private String fingerprint;
    private String assetType;
    private String assetId;
    private String assetName;
    private String health;
    private Double errorRate;
    private Long failCount;
    private String window;
    private String channelType;
    private String channelId;
    private String status;
    private String payloadJson;
    private String errorMsg;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime firedAt;

    public static AlertEventDTO fromDO(AlertEventDO e) {
        if (e == null) return null;
        AlertEventDTO d = new AlertEventDTO();
        d.setId(e.getId());
        d.setRuleId(e.getRuleId());
        d.setRuleName(e.getRuleName());
        d.setFingerprint(e.getFingerprint());
        d.setAssetType(e.getAssetType());
        d.setAssetId(e.getAssetId());
        d.setAssetName(e.getAssetName());
        d.setHealth(e.getHealth());
        d.setErrorRate(e.getErrorRate());
        d.setFailCount(e.getFailCount());
        d.setWindow(e.getWindow());
        d.setChannelType(e.getChannelType());
        d.setChannelId(e.getChannelId());
        d.setStatus(e.getStatus());
        d.setPayloadJson(e.getPayloadJson());
        d.setErrorMsg(e.getErrorMsg());
        d.setFiredAt(e.getFiredAt());
        return d;
    }
}
