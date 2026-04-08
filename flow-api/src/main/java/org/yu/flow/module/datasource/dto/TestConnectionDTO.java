package org.yu.flow.module.datasource.dto;

import lombok.Data;

import javax.validation.constraints.NotBlank;

/**
 * 独立连通性测试请求 DTO
 * 不要求数据入库，仅用于临时 JDBC 连接测试
 */
@Data
public class TestConnectionDTO {

    /** 数据源ID（若是编辑模式测试且密码未修改时传入，后端据此查旧密码） */
    private String id;

    /** JDBC 驱动类名 */
    @NotBlank(message = "driverClassName 不能为空")
    private String driverClassName;

    /** 数据库连接 URL */
    @NotBlank(message = "url 不能为空")
    private String url;

    /** 用户名 */
    @NotBlank(message = "username 不能为空")
    private String username;

    /**
     * 明文密码（前端传入，不存库）。
     * 若要测试已保存数据源的密码，则从数据库取解密值，此字段不传。
     */
    private String password;
}
