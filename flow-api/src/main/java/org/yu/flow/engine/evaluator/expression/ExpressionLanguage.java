package org.yu.flow.engine.evaluator.expression;

/**
 * 表达式语言枚举
 * 支持 Aviator (默认)、SpEL、JavaScript (GraalJS)、Python (GraalPy) 和 Groovy
 */
public enum ExpressionLanguage {
    /**
     * AviatorScript - 默认表达式引擎
     * 语法: 变量直接引用 (如 age > 18)
     */
    AVIATOR("aviator"),

    /**
     * Spring Expression Language
     * 语法: 变量使用 # 前缀 (如 #age > 18)
     * 支持 T() 调用静态方法
     */
    SPEL("spel"),

    /**
     * JavaScript (GraalJS) - 高级脚本引擎
     * 语法: 标准 ECMAScript，适合处理复杂的 JSON/数组变换
     * 通过 input 变量访问流程上下文输入
     */
    JAVASCRIPT("javascript"),

    /**
     * Python (GraalPy) - 高级脚本引擎
     */
    PYTHON("python"),

    /**
     * Groovy - JVM 动态脚本引擎
     */
    GROOVY("groovy");

    private final String value;

    ExpressionLanguage(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

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
        if ("py".equalsIgnoreCase(language) || "python".equalsIgnoreCase(language)) {
            return PYTHON;
        }
        if ("groovy".equalsIgnoreCase(language)) {
            return GROOVY;
        }
        return AVIATOR; // 默认
    }
}
