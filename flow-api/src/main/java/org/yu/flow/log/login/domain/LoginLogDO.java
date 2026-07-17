package org.yu.flow.log.login.domain;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.GenericGenerator;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * 登录日志实体
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Entity
@EntityListeners(AuditingEntityListener.class)
@Table(name = "flow_log_login")
public class LoginLogDO {

    @Id
    @Column(name = "id", nullable = false, length = 64)
    @GeneratedValue(generator = "snow_id")
    @GenericGenerator(name = "snow_id", strategy = "org.yu.flow.auto.util.SnowIdGenerator")
    private String id;

    /**
     * 登录账号
     */
    @Column(name = "account", nullable = false, length = 100)
    private String account;

    /**
     * 客户端 IP
     */
    @Column(name = "ip", length = 64)
    private String ip;

    /**
     * IP 归属地区（如：中国-广东-深圳）
     */
    @Column(name = "region", length = 255)
    private String region;

    /**
     * 浏览器 User-Agent
     */
    @Column(name = "user_agent", length = 512)
    private String userAgent;

    /**
     * 登录状态（1: 成功, 0: 失败）
     */
    @Column(name = "status", nullable = false, columnDefinition = "TINYINT DEFAULT 0")
    private Integer status;

    /**
     * 登录结果信息（如：登录成功 / 用户名或密码错误）
     */
    @Column(name = "msg", length = 255)
    private String msg;

    /**
     * 登录耗时（毫秒）
     */
    @Column(name = "duration")
    private Long duration;

    /**
     * 登录时间
     */
    @CreatedDate
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    @Column(name = "create_time", updatable = false)
    private LocalDateTime createTime;
}
