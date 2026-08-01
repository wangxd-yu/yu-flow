package org.yu.flow.module.mq.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;
import lombok.experimental.Accessors;
import org.yu.flow.module.mq.domain.MqConnectionDO;

import java.time.LocalDateTime;

/**
 * MQ 连接配置 DTO（列表 / 详情，不回传密码密文）
 *
 * @author yu-flow
 */
@Data
@Accessors(chain = true)
public class MqConnectionDTO {

    private String id;

    private String name;

    private String code;

    /** MQ 类型：RABBITMQ / KAFKA */
    private String mqType;

    private String servers;

    private String virtualHost;

    private String username;

    /** 是否已配置密码（前端展示用，不回传密文） */
    private Boolean hasPassword;

    private Boolean enabled;

    /** 健康状态：HEALTHY / UNHEALTHY / UNKNOWN */
    private String healthStatus;

    private String lastErrorMsg;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime lastTestTime;

    private String info;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime createTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime updateTime;

    public static MqConnectionDTO fromDO(MqConnectionDO entity) {
        if (entity == null) {
            return null;
        }
        return new MqConnectionDTO()
                .setId(entity.getId())
                .setName(entity.getName())
                .setCode(entity.getCode())
                .setMqType(entity.getMqType())
                .setServers(entity.getServers())
                .setVirtualHost(entity.getVirtualHost())
                .setUsername(entity.getUsername())
                .setHasPassword(entity.getPassword() != null && !entity.getPassword().isEmpty())
                .setEnabled(entity.getEnabled())
                .setHealthStatus(entity.getHealthStatus())
                .setLastErrorMsg(entity.getLastErrorMsg())
                .setLastTestTime(entity.getLastTestTime())
                .setInfo(entity.getInfo())
                .setCreateTime(entity.getCreateTime())
                .setUpdateTime(entity.getUpdateTime());
    }
}
