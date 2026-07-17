package org.yu.flow.log.login.query;

import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDateTime;

/**
 * 登录日志分页查询条件 DTO
 */
@Data
public class LoginLogQueryDTO {

    /** 登录账号（模糊匹配） */
    private String account;

    /** 登录状态（1: 成功, 0: 失败） */
    private Integer status;

    /** 登录时间起始（含） */
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime startTime;

    /** 登录时间截止（含） */
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime endTime;

    /** 当前页码，从 1 开始 */
    private int page = 1;

    /** 每页条数 */
    private int size = 20;
}
