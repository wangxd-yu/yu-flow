package org.yu.flow.module.open.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;
import org.yu.flow.module.open.domain.FlowOpenPlatformDO;

import java.time.LocalDateTime;

@Data
public class FlowOpenPlatformDTO {
    private String id;
    private String name;
    private String code;
    private Integer status;
    private String contact;
    private String remark;
    private String ipAllowlist;
    /** 0关 1开 */
    private Integer openCallLogEnabled;
    private Integer rateLimitQps;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime expireAt;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime createTime;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime updateTime;
    private Long credentialCount;
    private Long grantCount;

    public static FlowOpenPlatformDTO fromDO(FlowOpenPlatformDO d) {
        if (d == null) return null;
        FlowOpenPlatformDTO dto = new FlowOpenPlatformDTO();
        dto.setId(d.getId());
        dto.setName(d.getName());
        dto.setCode(d.getCode());
        dto.setStatus(d.getStatus());
        dto.setContact(d.getContact());
        dto.setRemark(d.getRemark());
        dto.setIpAllowlist(d.getIpAllowlist());
        dto.setOpenCallLogEnabled(d.getOpenCallLogEnabled());
        dto.setRateLimitQps(d.getRateLimitQps());
        dto.setExpireAt(d.getExpireAt());
        dto.setCreateTime(d.getCreateTime());
        dto.setUpdateTime(d.getUpdateTime());
        return dto;
    }
}
