package org.yu.flow.engine.evaluator.expression;

/**
 * 表达式求值器工厂
 * 根据语言类型返回对应的求值器实现
 */
public class ExpressionEvaluatorFactory {

    // 单例实例 (无状态，可复用)
    private static final AviatorEvaluatorImpl AVIATOR_EVALUATOR = new AviatorEvaluatorImpl();
    private static final SpelEvaluatorImpl SPEL_EVALUATOR = new SpelEvaluatorImpl();
    private static final JavaScriptEvaluatorImpl JAVASCRIPT_EVALUATOR = new JavaScriptEvaluatorImpl();
    private static final PythonEvaluatorImpl PYTHON_EVALUATOR = new PythonEvaluatorImpl();
    private static final GroovyEvaluatorImpl GROOVY_EVALUATOR = new GroovyEvaluatorImpl();

    /**
     * 根据语言类型获取对应的表达式求值器
     *
     * @param language 语言类型字符串，null 或未知值默认返回 Aviator
     * @return 对应的表达式求值器
     */
    public static ExpressionEvaluatorStrategy getEvaluator(String language) {
        ExpressionLanguage lang = ExpressionLanguage.fromString(language);
        return getEvaluator(lang);
    }

    /**
     * 根据语言枚举获取对应的表达式求值器
     *
     * @param language 语言枚举
     * @return 对应的表达式求值器
     */
    public static ExpressionEvaluatorStrategy getEvaluator(ExpressionLanguage language) {
        if (language == null) {
            return AVIATOR_EVALUATOR;
        }

        switch (language) {
            case SPEL:
                return SPEL_EVALUATOR;
            case JAVASCRIPT:
                return JAVASCRIPT_EVALUATOR;
            case PYTHON:
                return PYTHON_EVALUATOR;
            case GROOVY:
                return GROOVY_EVALUATOR;
            case AVIATOR:
            default:
                return AVIATOR_EVALUATOR;
        }
    }

    /**
     * 获取 Aviator 求值器 (默认)
     */
    public static ExpressionEvaluatorStrategy getAviator() {
        return AVIATOR_EVALUATOR;
    }

    /**
     * 获取 SpEL 求值器
     */
    public static ExpressionEvaluatorStrategy getSpel() {
        return SPEL_EVALUATOR;
    }

    /**
     * 获取 JavaScript (GraalJS) 求值器
     */
    public static ExpressionEvaluatorStrategy getJavaScript() {
        return JAVASCRIPT_EVALUATOR;
    }

    /**
     * 获取 Python (GraalPy) 求值器
     */
    public static ExpressionEvaluatorStrategy getPython() {
        return PYTHON_EVALUATOR;
    }

    /**
     * 获取 Groovy 求值器
     */
    public static ExpressionEvaluatorStrategy getGroovy() {
        return GROOVY_EVALUATOR;
    }
}
