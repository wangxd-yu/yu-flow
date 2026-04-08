package org.yu.flow.module.sysmacro.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 系统宏字典 VO（前端代码提示专用）
 *
 * <p>仅暴露前端所需的最小字段集，不包含任何后端执行细节（如 SpEL 表达式、scope 等），
 * 保证接口轻量、安全。</p>
 *
 * yu-flow
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class SysMacroDictVO {

    /** 宏编码，前端调用凭证（如 sys_user_id） */
    private String macroCode;

    /** 宏名称，用于 UI 显示（如 当前登录用户 ID） */
    private String macroName;

    /** 宏类型：VARIABLE 变量 / FUNCTION 方法 */
    private String macroType;

    /** 返回值类型，用于前端类型推导（如 String, Number） */
    private String returnType;

    /** 入参列表，仅 FUNCTION 类型有效（逗号分隔，如 date,format） */
    private String macroParams;
}
