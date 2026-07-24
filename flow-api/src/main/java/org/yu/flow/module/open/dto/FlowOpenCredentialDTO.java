package org.yu.flow.module.open.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;
import org.yu.flow.module.open.domain.FlowOpenCredentialDO;

import java.util.Date;

@Data
public class FlowOpenCredentialDTO {
    private String id;
    private String platformId;
    private String appKey;
    /** 仅创建/轮换时返回一次 */
    private String appSecret;
    private String secretHint;
    private Integer status;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date expireAt;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date createTime;

    public static FlowOpenCredentialDTO fromDO(FlowOpenCredentialDO d) {
        if (d == null) return null;
        FlowOpenCredentialDTO dto = new FlowOpenCredentialDTO();
        dto.setId(d.getId());
        dto.setPlatformId(d.getPlatformId());
        dto.setAppKey(d.getAppKey());
        dto.setSecretHint(d.getSecretHint());
        dto.setStatus(d.getStatus());
        dto.setExpireAt(d.getExpireAt());
        dto.setCreateTime(d.getCreateTime());
        return dto;
    }
}
