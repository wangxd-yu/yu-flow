package org.yu.flow.module.mq.query;

import lombok.Data;

/**
 * MQ 连接配置分页查询条件
 *
 * @author yu-flow
 */
@Data
public class MqConnectionQueryDTO {

    /** 连接名称（模糊匹配） */
    private String name;

    /** 连接编码（模糊匹配） */
    private String code;

    /** MQ 类型：RABBITMQ / KAFKA */
    private String mqType;

    /** 启用状态：0=停用，1=启用 */
    private Boolean enabled;

    /** 页码（从 0 开始，默认 0） */
    private int page = 0;

    /** 每页条数（默认 10） */
    private int size = 10;
}
