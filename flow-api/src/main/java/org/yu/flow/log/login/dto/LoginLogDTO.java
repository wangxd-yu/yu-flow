package org.yu.flow.log.login.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;
import org.yu.flow.log.login.domain.LoginLogDO;

import java.time.LocalDateTime;

/**
 * 登录日志 DTO（API 响应）
 */
@Data
public class LoginLogDTO {

    private String id;

    /** 登录账号 */
    private String account;

    /** 客户端 IP */
    private String ip;

    /** IP 归属地区 */
    private String region;

    /** 浏览器 User-Agent */
    private String userAgent;

    /** 登录状态（1: 成功, 0: 失败） */
    private Integer status;

    /** 登录结果信息 */
    private String msg;

    /** 登录耗时（毫秒） */
    private Long duration;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime createTime;

    /**
     * DO → DTO
     */
    public static LoginLogDTO fromDO(LoginLogDO entity) {
        if (entity == null) return null;
        LoginLogDTO dto = new LoginLogDTO();
        dto.setId(entity.getId());
        dto.setAccount(entity.getAccount());
        dto.setIp(entity.getIp());
        dto.setRegion(entity.getRegion());
        dto.setUserAgent(entity.getUserAgent());
        dto.setStatus(entity.getStatus());
        dto.setMsg(entity.getMsg());
        dto.setDuration(entity.getDuration());
        dto.setCreateTime(entity.getCreateTime());
        return dto;
    }
}
