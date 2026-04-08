package org.yu.flow.module.sysmacro.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;
import org.yu.flow.module.sysmacro.domain.SysMacroDO;

import java.time.LocalDateTime;

/**
 * 系统全局宏定义 DTO (API 响应)
 */
@Data
public class SysMacroDTO {

    private String id;

    /** 宏编码 (前端调用的唯一凭证，如: sys_user_id) */
    private String macroCode;

    /** 宏名称 (如: 当前登录用户 ID) */
    private String macroName;

    /** 宏类型 (枚举: VARIABLE 变量, FUNCTION 方法) */
    private String macroType;

    /** 真实的 SpEL 表达式 (如: @userContext.getUserId()) */
    private String expression;

    /** 作用域 (枚举: ALL 全局, SQL_ONLY 仅SQL, JS_ONLY 仅JS) */
    private String scope;

    /** 返回值类型 (用于前端 JS 类型推导提示，如 String, Number) */
    private String returnType;

    /** 入参列表 (仅 FUNCTION 类型有效，逗号分隔，如 date,format) */
    private String macroParams;

    /** 状态 (1: 启用, 0: 停用) */
    private Integer status;

    /** 备注说明 */
    private String remark;

    private String createBy;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime createTime;

    private String updateBy;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime updateTime;

    /**
     * DO → DTO
     */
    public static SysMacroDTO fromDO(SysMacroDO entity) {
        if (entity == null) return null;

        SysMacroDTO dto = new SysMacroDTO();
        dto.setId(entity.getId());
        dto.setMacroCode(entity.getMacroCode());
        dto.setMacroName(entity.getMacroName());
        dto.setMacroType(entity.getMacroType());
        dto.setExpression(entity.getExpression());
        dto.setScope(entity.getScope());
        dto.setReturnType(entity.getReturnType());
        dto.setMacroParams(entity.getMacroParams());
        dto.setStatus(entity.getStatus());
        dto.setRemark(entity.getRemark());
        dto.setCreateBy(entity.getCreateBy());
        dto.setCreateTime(entity.getCreateTime());
        dto.setUpdateBy(entity.getUpdateBy());
        dto.setUpdateTime(entity.getUpdateTime());
        return dto;
    }

    /**
     * DTO → DO（用于创建/更新时的转换）
     */
    public SysMacroDO toDO() {
        SysMacroDO entity = new SysMacroDO();
        entity.setMacroCode(this.macroCode);
        entity.setMacroName(this.macroName);
        entity.setMacroType(this.macroType);
        entity.setExpression(this.expression);
        entity.setScope(this.scope);
        entity.setReturnType(this.returnType);
        entity.setMacroParams(this.macroParams);
        entity.setStatus(this.status);
        entity.setRemark(this.remark);
        return entity;
    }
}
