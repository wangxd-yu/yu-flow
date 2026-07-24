package org.yu.flow.module.datasource.domain;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;
import org.yu.flow.module.datasource.dto.DataSourceWallConfig;

import java.time.LocalDateTime;

@Data
public class DataSourceDO {

    // -------- 健康状态常量 --------
    public static final String HEALTH_HEALTHY      = "HEALTHY";
    public static final String HEALTH_UNHEALTHY    = "UNHEALTHY";
    public static final String HEALTH_UNKNOWN       = "UNKNOWN";
    /** 熔断状态：连续失败次数达到阈值后进入，大幅降低探测频率 */
    public static final String HEALTH_CIRCUIT_OPEN = "CIRCUIT_OPEN";

    /** 连续失败多少次后触发熔断 */
    public static final int CIRCUIT_OPEN_THRESHOLD = 5;

    private String id;
    private String name;
    /** 数据源全局唯一编码，用于跨环境关联 */
    private String code;
    private String dbType; // mysql/postgresql/highgo
    private String driverClassName;
    private String url;
    private String username;
    private String password;
    private Integer initialSize = 5;
    private Integer minIdle = 5;
    private Integer maxActive = 20;
    private Integer status = 1; // 0-停用,1-启用

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime createTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime updateTime;

    /** 系统数据源（1 = 如 [DEFAULT]，连接只读、仅可改安全墙） */
    private Integer isSystem = 0;

    /** SQL 安全墙（产品化配置） */
    private DataSourceWallConfig wallConfig;

    // -------- 健康度追踪字段 --------
    /** 连接健康度：HEALTHY / UNHEALTHY / CIRCUIT_OPEN / UNKNOWN */
    private String healthStatus = HEALTH_UNKNOWN;
    /** 连续连接失败次数 */
    private Integer errorCount = 0;
    /** 最后一次连接失败的异常堆栈/简述 */
    private String lastErrorMsg;

    public boolean systemDataSource() {
        return isSystem != null && isSystem == 1;
    }
}
