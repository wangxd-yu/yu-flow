package org.yu.flow.engine.evaluator.expression;

/**
 * 表达式语言枚举
 * 支持 Aviator (默认)、SpEL 和 JavaScript (GraalJS) 三种表达式引擎
 */
public enum ExpressionLanguage {
    /**
     * AviatorScript - 默认表达式引擎
     * 语法: 变量直接引用 (如 age > 18)
     */
    AVIATOR,

    /**
     * Spring Expression Language
     * 语法: 变量使用 # 前缀 (如 #age > 18)
     * 支持 T() 调用静态方法
     */
    SPEL,

    /**
     * JavaScript (GraalJS) - 高级脚本引擎
     * 语法: 标准 ECMAScript，适合处理复杂的 JSON/数组变换
     * 通过 input 变量访问流程上下文输入
     */
    JAVASCRIPT;

    /**
     * 从字符串解析语言类型，默认返回 AVIATOR
     */
    public static ExpressionLanguage fromString(String language) {
        if ("spel".equalsIgnoreCase(language)) {
            return SPEL;
        }
        if ("js".equalsIgnoreCase(language) || "javascript".equalsIgnoreCase(language)) {
            return JAVASCRIPT;
        }
        return AVIATOR; // 默认
    }
}
