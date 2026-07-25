package org.yu.flow.engine.evaluator.expression;

import cn.hutool.extra.spring.SpringUtil;
import org.yu.flow.config.YuFlowProperties;
import org.yu.flow.exception.FlowException;

import java.util.List;
import java.util.Locale;

/**
 * 表达式求值器工厂
 * 根据语言类型返回对应的求值器实现
 *
 * <p>受 {@code yu.flow.security.script-allowed-languages} 白名单约束：
 * 未列入白名单的语言在获取求值器时直接抛出 {@code SCRIPT_LANGUAGE_DISABLED}。
 * 默认仅开放 aviator / spel / javascript；groovy、python 需显式放开。</p>
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
        assertLanguageAllowed(language);

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
     * 校验语言是否在 {@code yu.flow.security.script-allowed-languages} 白名单内。
     * 非 Spring 场景（纯单测直调）无法取到配置时放行，保持既有行为。
     */
    static void assertLanguageAllowed(ExpressionLanguage language) {
        List<String> allowed;
        try {
            YuFlowProperties properties = SpringUtil.getBean(YuFlowProperties.class);
            if (properties == null || properties.getSecurity() == null) {
                return;
            }
            allowed = properties.getSecurity().getScriptAllowedLanguages();
        } catch (Exception e) {
            // 非 Spring 场景不做限制。
            return;
        }
        if (allowed == null || allowed.isEmpty()) {
            return;
        }
        String value = language.getValue();
        for (String item : allowed) {
            if ((item != null && value.equalsIgnoreCase(item.trim()))
                    || normalizeAlias(item) == language) {
                return;
            }
        }
        throw new FlowException("SCRIPT_LANGUAGE_DISABLED",
                "脚本语言 " + value + " 未在白名单内（yu.flow.security.script-allowed-languages），请联系管理员开启");
    }

    /**
     * 兼容 js / py 等别名写法。
     */
    private static ExpressionLanguage normalizeAlias(String item) {
        if (item == null || item.trim().isEmpty()) {
            return null;
        }
        String normalized = item.trim().toLowerCase(Locale.ROOT);
        switch (normalized) {
            case "js":
            case "javascript":
                return ExpressionLanguage.JAVASCRIPT;
            case "py":
            case "python":
                return ExpressionLanguage.PYTHON;
            default:
                return null;
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
