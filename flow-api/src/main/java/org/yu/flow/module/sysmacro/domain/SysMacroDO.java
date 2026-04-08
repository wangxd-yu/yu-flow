package org.yu.flow.module.sysmacro.domain;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import org.hibernate.annotations.GenericGenerator;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import javax.persistence.*;
import java.time.LocalDateTime;

/**
 * 系统全局宏定义字典表实体
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Entity
@Accessors(chain = true)
@EntityListeners(AuditingEntityListener.class)
@Table(name = "flow_sys_macro")
public class SysMacroDO {

    @Id
    @Column(name = "id", nullable = false)
    @GeneratedValue(generator = "snow_id")
    @GenericGenerator(name = "snow_id", strategy = "org.yu.flow.auto.util.SnowIdGenerator")
    private String id;

    /**
     * 宏编码 (前端调用的唯一凭证，如: sys_user_id)
     */
    @Column(name = "macro_code", nullable = false, unique = true, length = 100)
    private String macroCode;

    /**
     * 宏名称 (如: 当前登录用户 ID)
     */
    @Column(name = "macro_name", nullable = false, length = 100)
    private String macroName;

    /**
     * 宏类型 (枚举: VARIABLE 变量, FUNCTION 方法)
     */
    @Column(name = "macro_type", nullable = false, length = 50)
    private String macroType;

    /**
     * 真实的 SpEL 表达式 (如: @userContext.getUserId())
     */
    @Column(name = "expression", nullable = false, length = 500)
    private String expression;

    /**
     * 作用域 (枚举: ALL 全局, SQL_ONLY 仅SQL, JS_ONLY 仅JS)
     */
    @Column(name = "scope", nullable = false, length = 50)
    private String scope;

    /**
     * 返回值类型 (用于前端 JS 类型推导提示，如 String, Number)
     */
    @Column(name = "return_type", length = 50)
    private String returnType;

    /**
     * 入参列表 (仅 FUNCTION 类型有效，逗号分隔，如 date,format)
     */
    @Column(name = "macro_params", length = 255)
    private String macroParams;

    /**
     * 状态 (1: 启用, 0: 停用)
     */
    @Column(name = "status", nullable = false, columnDefinition = "TINYINT DEFAULT 1")
    private Integer status;

    /**
     * 备注说明
     */
    @Column(name = "remark", length = 500)
    private String remark;

    /** 创建者 */
    @Column(name = "create_by", length = 64)
    private String createBy;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    @Column(name = "create_time")
    private LocalDateTime createTime;

    /** 更新者 */
    @Column(name = "update_by", length = 64)
    private String updateBy;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    @Column(name = "update_time")
    private LocalDateTime updateTime;
}
