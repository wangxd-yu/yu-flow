package org.yu.flow.module.sysmacro.dto;

import lombok.Data;

import javax.validation.constraints.NotBlank;

/**
 * 新建和更新系统宏定义的 DTO
 */
@Data
public class SaveSysMacroDTO {

    /** 主键，更新时必传 */
    private String id;

    /** 宏编码 (前端调用的唯一凭证，如: sys_user_id) */
    @NotBlank(message = "宏编码不能为空")
    private String macroCode;

    /** 宏名称 (如: 当前登录用户 ID) */
    @NotBlank(message = "宏名称不能为空")
    private String macroName;

    /** 宏类型 (枚举: VARIABLE 变量, FUNCTION 方法) */
    @NotBlank(message = "宏类型不能为空")
    private String macroType;

    /** 真实的 SpEL 表达式 (如: @userContext.getUserId()) */
    @NotBlank(message = "SpEL 表达式不能为空")
    private String expression;

    /** 作用域 (枚举: ALL 全局, SQL_ONLY 仅SQL, JS_ONLY 仅JS)，默认 ALL */
    private String scope;

    /** 返回值类型 (用于前端 JS 类型推导提示，如 String, Number) */
    private String returnType;

    /** 状态 (1: 启用, 0: 停用) */
    private Integer status;

    /** 入参列表 (仅 FUNCTION 类型有效，逗号分隔，如 date,format) */
    private String macroParams;

    /** 备注说明 */
    private String remark;
}
