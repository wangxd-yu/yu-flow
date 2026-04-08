package org.yu.flow.engine.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

/**
 * 条件分支case定义
 */
@Data
public class ConditionCase {
    private String expression;    // Spring EL条件表达式
    @JsonProperty("throw")
    private String throwError;    // 满足条件时抛出的错误码
    private String setVar;        // 满足条件时设置的变量（格式：varName=expression）

    /**
     * 快捷构造方法：带表达式和错误抛出
     */
    public static ConditionCase of(String expression, String throwError) {
        ConditionCase cc = new ConditionCase();
        cc.setExpression(expression);
        cc.setThrowError(throwError);
        return cc;
    }

    /**
     * 快捷构造方法：带表达式和变量设置
     */
    public static ConditionCase of(String expression, String varName, String varValueExpr) {
        ConditionCase cc = new ConditionCase();
        cc.setExpression(expression);
        cc.setSetVar(varName + "=" + varValueExpr);
        return cc;
    }
}
