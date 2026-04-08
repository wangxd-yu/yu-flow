package org.yu.flow.auto.util;

import org.yu.flow.exception.FlowException;
import org.yu.flow.module.sysmacro.cache.CachedMacro;
import org.yu.flow.module.sysmacro.cache.SysMacroCacheManager;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.context.expression.BeanFactoryResolver;
import org.springframework.expression.EvaluationContext;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 系统参数解析工具 —— 100% 基于全局宏字典（Semantic Layer）
 *
 * <p>所有系统参数的获取能力均由 {@link SysMacroCacheManager} 统一接管。
 * 不再存在硬编码的 switch-case 分支，传入的参数标识即为宏字典中的 macro_code。</p>
 *
 * <h3>调用示例</h3>
 * <pre>
 *   FlowSystemParamsUtil.getParams("UUID")          → 32位无连字符UUID
 *   FlowSystemParamsUtil.getParams("DATE_TIME")     → 当前标准日期时间
 *   FlowSystemParamsUtil.getParams("GET_ENV", Map.of("p0", "spring.datasource.url")) → 环境变量值
 * </pre>
 *
 * @author yu-flow
 * @date 2025-05-03 15:45
 */
@Component
public class FlowSystemParamsUtil {

    /** 匹配 ${#macroCode} 占位符的正则 */
    private static final Pattern MACRO_PLACEHOLDER = Pattern.compile("\\$\\{(#\\w+)\\}");

    /** 静态单例引用，供静态方法调用实例方法 */
    private static FlowSystemParamsUtil INSTANCE;

    private final SysMacroCacheManager sysMacroCacheManager;
    private final BeanFactory beanFactory;

    public FlowSystemParamsUtil(SysMacroCacheManager sysMacroCacheManager, BeanFactory beanFactory) {
        INSTANCE = this;
        this.sysMacroCacheManager = sysMacroCacheManager;
        this.beanFactory = beanFactory;
    }

    // ============================= 公共 API =============================

    /**
     * 获取系统参数（无上下文参数版本）
     *
     * @param macroCode 宏编码（如 "UUID"、"DATE_TIME"）
     * @return 宏表达式的求值结果
     */
    public static Object getParams(String macroCode) {
        return getParams(macroCode, null);
    }

    /**
     * 获取系统参数（带上下文参数版本，支持方法宏入参）
     *
     * @param macroCode     宏编码（如 "UUID"、"GET_ENV"）
     * @param contextParams 上下文参数（如 GET_ENV 需要 {p0: "spring.datasource.url"}）
     * @return 宏表达式的求值结果
     */
    public static Object getParams(String macroCode, Map<String, ?> contextParams) {
        return INSTANCE.evaluate(macroCode, contextParams);
    }

    /**
     * 解析表达式中的 ${#macroCode} 占位符，求值后注入到 SpEL 上下文中。
     *
     * <p>示例：输入 {@code "name = ${#UUID}"} → 求值 UUID 宏 → 输出 {@code "name = #UUID"}，
     * 同时将求值结果 set 为 context 中的变量 {@code #UUID}。</p>
     *
     * @param expr    原始表达式
     * @param context SpEL 求值上下文
     * @return 替换占位符后的表达式
     */
    public static String resolveParams(String expr, EvaluationContext context) {
        Matcher matcher = MACRO_PLACEHOLDER.matcher(expr);
        StringBuffer result = new StringBuffer();

        while (matcher.find()) {
            String fullMatch = matcher.group(0);   // ${#UUID}
            String varRef    = matcher.group(1);    // #UUID
            String macroCode = varRef.substring(1); // UUID

            try {
                Object value = getParams(macroCode);
                context.setVariable(macroCode, value);
                matcher.appendReplacement(result, "#" + macroCode);
            } catch (Exception e) {
                // 宏求值失败，保留原始占位符不影响整体表达式
                matcher.appendReplacement(result, Matcher.quoteReplacement(fullMatch));
            }
        }

        matcher.appendTail(result);
        return result.toString();
    }

    // ============================= 核心求值引擎 =============================

    /**
     * 从宏缓存中获取已编译的 SpEL 表达式并在带 BeanResolver 的上下文中求值。
     *
     * <p>通过 {@link SysMacroCacheManager} 的 L1 本地缓存以 O(1) 复杂度获取预编译的
     * SpEL Expression 对象，并在注入了 {@link BeanFactoryResolver}（支持 {@code @beanName} 引用）
     * 和当前请求上下文参数的 {@link StandardEvaluationContext} 中执行求值。</p>
     *
     * @param macroCode     宏编码，对应数据库 flow_sys_macro 表的 macro_code 字段
     * @param contextParams 当前请求的参数上下文（可为 null），宏表达式可通过 #变量名 引用
     * @return 宏表达式的求值结果
     * @throws FlowException 当宏编码在缓存中未命中时抛出 MACRO_NOT_FOUND 异常
     */
    private Object evaluate(String macroCode, Map<String, ?> contextParams) {
        CachedMacro cachedMacro = sysMacroCacheManager.getMacro(macroCode);
        if (cachedMacro == null) {
            throw new FlowException("MACRO_NOT_FOUND",
                    "宏定义未找到或未启用，请检查宏编码是否正确：" + macroCode);
        }

        StandardEvaluationContext context = new StandardEvaluationContext();
        // 注入 BeanResolver，使 SpEL 中 @beanName 语法可引用 Spring 容器中的 Bean
        // 例如 GET_ENV 宏的表达式 @environment.getProperty(#p0) 需要解析 @environment
        context.setBeanResolver(new BeanFactoryResolver(beanFactory));

        if (contextParams != null && !contextParams.isEmpty()) {
            @SuppressWarnings("unchecked")
            Map<String, Object> vars = (Map<String, Object>) (Map<String, ?>) contextParams;
            context.setVariables(vars);
        }

        return cachedMacro.getCompiledExpression().getValue(context);
    }
}
