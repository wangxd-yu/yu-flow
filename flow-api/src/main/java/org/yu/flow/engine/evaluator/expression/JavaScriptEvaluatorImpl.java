package org.yu.flow.engine.evaluator.expression;

import cn.hutool.extra.spring.SpringUtil;
import lombok.extern.slf4j.Slf4j;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Engine;
import org.graalvm.polyglot.HostAccess;
import org.graalvm.polyglot.PolyglotException;
import org.graalvm.polyglot.Value;
import org.graalvm.polyglot.proxy.ProxyExecutable;
import org.yu.flow.exception.FlowException;
import org.yu.flow.module.sysmacro.cache.CachedMacro;
import org.yu.flow.module.sysmacro.cache.SysMacroCacheManager;
import org.springframework.expression.Expression;
import org.springframework.expression.spel.support.StandardEvaluationContext;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * JavaScript (GraalJS) 表达式求值器实现
 *
 * <p>基于 GraalVM 21.3.10（最后支持 Java 8 的版本线），使用 Polyglot API。
 * 完整支持 ES6+ 语法（箭头函数、模板字符串、解构、let/const、Promise 等）。
 *
 * <p>核心设计思路:
 * <ol>
 *   <li><b>Engine 级别缓存</b>: 类级别静态持有 {@link Engine} 实例，
 *       多次调用共享已编译的 IR 缓存，极大降低 JS 引擎初始化开销。</li>
 *   <li><b>Context 一次性使用</b>: 每次 evaluate 调用都新建 {@link Context}，
 *       确保线程安全和沙箱隔离；执行完毕后立即关闭释放资源。</li>
 *   <li><b>安全沙箱</b>: 禁用系统 I/O (allowIO=false)、进程创建 (allowCreateProcess=false)、
 *       环境变量 (allowEnvironmentAccess=NONE)、线程 (allowCreateThread=false)、
 *       Native 访问 (allowNativeAccess=false)。禁止 JS 反向查找任何 Java 类。</li>
 *   <li><b>变量注入</b>: 将流程输入 Map 序列化为 JSON，通过 {@code JSON.parse()} 转为
 *       原生 JS 对象注入，确保 JS 数组拥有完整的 filter/map/reduce 等原生方法。</li>
 *   <li><b>结果转换</b>: 将 JS 执行结果递归转换为 Java 的 Map / List / String / Number / Boolean。</li>
 *   <li><b>全局宏注入</b>: 在 JS 执行前，从 {@link SysMacroCacheManager} 获取所有非 SQL_ONLY 宏，
 *       将 VARIABLE 宏求值后注入为 JS 全局变量，将 FUNCTION 宏包装为 {@link ProxyExecutable}
 *       注入为可调用的 JS 函数。</li>
 * </ol>
 *
 * <p>使用示例 (在 data 中指定 language 为 "js"):
 * <pre>{@code
 *   "language": "js",
 *   "expression": "input.items.filter(x => x.price > 100).map(x => x.name)"
 * }</pre>
 *
 * @see ExpressionEvaluatorStrategy
 * @see ExpressionEvaluatorFactory
 */
@Slf4j
public class JavaScriptEvaluatorImpl implements ExpressionEvaluatorStrategy {

    /**
     * 类级别缓存的 GraalVM Engine —— 所有 JS Context 共享此 Engine，
     * 内部保存着已编译的 AST/IR 代码缓存，避免重复编译。
     */
    private static final Engine SHARED_ENGINE = Engine.newBuilder()
            .option("engine.WarnInterpreterOnly", "false")
            .build();

    /** Jackson ObjectMapper - 用于 Java Map → JSON 字符串 */
    private static final com.fasterxml.jackson.databind.ObjectMapper OBJECT_MAPPER =
            new com.fasterxml.jackson.databind.ObjectMapper();

    /** 共享的 Spring SpEL 类型转换器，支持增强的自动类型转换 */
    private static final org.springframework.expression.TypeConverter CUSTOM_TYPE_CONVERTER;
    static {
        org.springframework.core.convert.support.DefaultConversionService conversionService = 
                new org.springframework.core.convert.support.DefaultConversionService();
        // 1. 兼容旧 Date 格式
        conversionService.addConverter(String.class, java.util.Date.class, 
                source -> source == null || source.isEmpty() ? null : cn.hutool.core.date.DateUtil.parse(source));
        // 2. 兼容 Java 8 Time 及 DateTimeFormatter (TemporalAccessor)
        //    支持多种日期格式：ISO带T (2026-03-19T21:25:21.842)、空格分隔、纯日期
        conversionService.addConverter(String.class, java.time.temporal.TemporalAccessor.class,
                source -> {
                    if (source == null || source.isEmpty()) return null;
                    try {
                        // 优先尝试 ISO 标准格式（带 T）：2026-03-19T21:25:21.842
                        return java.time.LocalDateTime.parse(source);
                    } catch (Exception e1) {
                        try {
                            // 尝试空格分隔格式：2026-03-19 21:25:21
                            return java.time.LocalDateTime.parse(source,
                                    java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
                        } catch (Exception e2) {
                            try {
                                // 尝试纯日期格式：2026-03-19
                                return java.time.LocalDate.parse(source).atStartOfDay();
                            } catch (Exception e3) {
                                throw new IllegalArgumentException("无法解析日期字符串: " + source);
                            }
                        }
                    }
                });
                
        CUSTOM_TYPE_CONVERTER = new org.springframework.expression.spel.support.StandardTypeConverter(conversionService);
    }

    // ============================= 内部类 =============================

    /**
     * 动态宏函数代理 —— 将 SpEL 编译表达式包装为 GraalJS 可调用的函数对象。
     *
     * <p>当 JS 脚本中调用一个 FUNCTION 类型的宏时（如 {@code sys_format_date('yyyy-MM-dd')}），
     * GraalVM 会回调本类的 {@link #execute(Value...)} 方法，内部将参数转换后
     * 委派给 SpEL 表达式执行引擎完成实际计算。</p>
     *
     * <h3>参数传递约定</h3>
     * <ul>
     *   <li>JS 函数调用时传入的参数，会依次以 {@code #p0, #p1, #p2, ...} 的形式
     *       注入到 SpEL 的 {@link StandardEvaluationContext} 中。</li>
     *   <li>如果 {@code requestContext} 不为空，其所有 key-value 也会同时注入到
     *       SpEL 上下文中，使得宏表达式内部可以引用当前请求的参数。</li>
     * </ul>
     */
    public static class DynamicMacroFunction implements ProxyExecutable {

        /** 预编译好的 SpEL 表达式对象（线程安全） */
        private final Expression compiledSpel;

        /** 当前请求的入参上下文（可为 null） */
        private final Map<String, Object> requestContext;

        /** 宏定义参数名列表 (逗号分隔，如 "format,date") */
        private final String macroParams;

        public DynamicMacroFunction(Expression compiledSpel, Map<String, Object> requestContext, String macroParams) {
            this.compiledSpel = compiledSpel;
            this.requestContext = requestContext;
            this.macroParams = macroParams;
        }

        /**
         * GraalVM 调用入口 —— JS 中调用该函数时触发。
         *
         * @param arguments JS 传入的实参列表
         * @return SpEL 表达式的求值结果
         */
        @Override
        public Object execute(Value... arguments) {
            // ╔══════════════════════════════════════════════════════════════════╗
            // ║  ⚠️ 安全警告：此处需要结合系统现有的安全策略/黑名单配置            ║
            // ║                                                                  ║
            // ║  当前使用 StandardEvaluationContext，拥有完整的 SpEL 能力，        ║
            // ║  包括 T() 类型引用、Runtime.exec() 等危险操作。                    ║
            // ║                                                                  ║
            // ║  生产环境上线前，务必实现以下安全措施之一：                          ║
            // ║  1. 使用 SimpleEvaluationContext 替代（最严格，禁用类型引用）       ║
            // ║  2. 自定义 TypeLocator 实现类型白名单                             ║
            // ║  3. 自定义 MethodResolver 实现方法黑名单                          ║
            // ║  4. 对 macro.getExpression() 进行正则预校验，拦截危险模式           ║
            // ║                                                                  ║
            // ║  参考：Spring 官方建议对用户输入的表达式使用 SimpleEvaluationContext ║
            // ╚══════════════════════════════════════════════════════════════════╝
            StandardEvaluationContext spelCtx = new StandardEvaluationContext();
            spelCtx.setTypeConverter(CUSTOM_TYPE_CONVERTER);

            if (arguments != null) {
                // 1. 按位置注入 #p0, #p1... 兜底兼容
                for (int i = 0; i < arguments.length; i++) {
                    Object javaArg = convertValue(arguments[i]);
                    spelCtx.setVariable("p" + i, javaArg);
                }
                
                // 2. 按 macro_params (如 "format,date") 动态注入具名变量（如 #format, #date）
                if (this.macroParams != null && !this.macroParams.trim().isEmpty()) {
                    String[] paramNames = this.macroParams.split(",");
                    for (int i = 0; i < Math.min(arguments.length, paramNames.length); i++) {
                        String pName = paramNames[i].trim();
                        if (!pName.isEmpty()) {
                            spelCtx.setVariable(pName, convertValue(arguments[i]));
                        }
                    }
                }
            }

            // 将当前请求上下文参数注入 SpEL 上下文（宏内部可引用请求参数）
            if (requestContext != null && !requestContext.isEmpty()) {
                spelCtx.setVariables(requestContext);
            }

            return compiledSpel.getValue(spelCtx);
        }
    }

    // ============================= 核心方法 =============================

    @Override
    public Object evaluate(String expression, Map<String, Object> context) {
        if (expression == null || expression.trim().isEmpty()) {
            return null;
        }

        // 每次调用新建 Context (线程安全 + 沙箱隔离)
        try (Context jsContext = Context.newBuilder("js")
                .engine(SHARED_ENGINE)
                // ---- 安全配置 ----
                .allowHostAccess(HostAccess.ALL)           // 允许 JS 访问注入的 Java 对象
                .allowHostClassLookup(className -> false)   // 禁止 JS 反向查找任何 Java 类
                .allowIO(false)                             // 禁用文件 I/O
                .allowCreateProcess(false)                  // 禁用进程创建
                .allowCreateThread(false)                   // 禁用线程创建
                .allowNativeAccess(false)                   // 禁用 Native 调用
                .allowExperimentalOptions(true)
                .option("js.ecmascript-version", "2021")    // 启用 ES2021 语法
                .build()) {

            // ---- 全局宏注入 (Semantic Layer) ----
            injectMacros(jsContext, context);

            // ---- 变量注入: 顶级变量绑定 + 向后兼容 input 对象 ----
            // 将整个 Map 序列化为 JSON，再在 JS 中解析为原生对象。
            // 同时将每个 key-value 都绑定为顶级变量，用户直接写 items.filter(...)
            // 而非冗余的 input.items.filter(...)。
            // input 对象仍保留，作为向后兼容别名。
            String inputJson;
            if (context != null && !context.isEmpty()) {
                inputJson = OBJECT_MAPPER.writeValueAsString(context);
            } else {
                inputJson = "{}";
            }

            // 构建包装脚本：先 JSON.parse 得到原生 JS 对象，
            // 再将每个属性解构为顶级变量 + 保留 input 别名
            StringBuilder scriptBuilder = new StringBuilder();
            scriptBuilder.append("(function() {\n");
            scriptBuilder.append("  var input = JSON.parse(").append(quoteForJs(inputJson)).append(");\n");
            // 将 input 中的每个 key 解构为同名顶级变量
            if (context != null && !context.isEmpty()) {
                for (String key : context.keySet()) {
                    // 校验 key 是否为合法 JS 标识符，防止注入
                    if (isValidJsIdentifier(key)) {
                        scriptBuilder.append("  var ").append(key).append(" = input[")
                                .append(quoteForJs(key)).append("];\n");
                    }
                }
            }
            scriptBuilder.append("  return (").append(expression).append(");\n");
            scriptBuilder.append("})()");

            String wrappedScript = scriptBuilder.toString();

            // ---- 执行脚本 ----
            Value result = jsContext.eval("js", wrappedScript);

            // ---- 结果转换：Value -> Java 类型 ----
            return convertValue(result);

        } catch (PolyglotException e) {
            // 区分语法错误和运行时错误，统一包装为 FlowException
            String errorType = e.isSyntaxError() ? "JS_SYNTAX_ERROR" : "JS_RUNTIME_ERROR";
            String detail = e.isGuestException()
                    ? "JavaScript 运行时错误: " + e.getMessage()
                    : "JavaScript 引擎错误: " + e.getMessage();
            throw new FlowException(errorType, detail + " | 脚本: " + truncate(expression, 200), e);

        } catch (FlowException e) {
            throw e;
        } catch (Exception e) {
            throw new FlowException("JS_EVAL_ERROR",
                    "JavaScript 表达式求值失败: " + truncate(expression, 200) + ", 错误: " + e.getMessage(), e);
        }
    }

    // ============================= 宏注入 =============================

    /**
     * 将全局宏字典中符合作用域的宏注入到 JS 执行上下文中。
     *
     * <p>注入策略：
     * <ul>
     *   <li><b>VARIABLE</b> 宏：在注入前立即执行 SpEL 求值，将结果值绑定为 JS 全局变量。</li>
     *   <li><b>FUNCTION</b> 宏：包装为 {@link DynamicMacroFunction} 代理对象，
     *       绑定为 JS 全局函数，延迟到 JS 调用时才执行 SpEL 求值。</li>
     *   <li><b>SQL_ONLY</b> 作用域的宏跳过，不注入到 JS 环境。</li>
     * </ul>
     *
     * @param jsContext GraalVM JS 上下文
     * @param context   当前请求的参数上下文
     */
    private void injectMacros(Context jsContext, Map<String, Object> context) {
        try {
            // 通过 Hutool SpringUtil 获取 SysMacroCacheManager 实例
            // 注意：此处依赖 Spring 容器已初始化完成，在非 Spring 环境（如单元测试）中
            // 可能返回 null，此时优雅跳过宏注入
            SysMacroCacheManager cacheManager;
            try {
                cacheManager = SpringUtil.getBean(SysMacroCacheManager.class);
            } catch (Exception e) {
                // Spring 容器未初始化或未找到 Bean，跳过宏注入
                log.debug("[JS宏注入] Spring 容器未就绪或未找到 SysMacroCacheManager，跳过宏注入。原因: {}", e.getMessage());
                return;
            }

            if (cacheManager == null) {
                return;
            }

            Map<String, CachedMacro> allMacros = cacheManager.getAllCachedMacros();
            if (allMacros == null || allMacros.isEmpty()) {
                return;
            }

            // 获取 JS 全局绑定对象
            Value bindings = jsContext.getBindings("js");

            for (Map.Entry<String, CachedMacro> entry : allMacros.entrySet()) {
                CachedMacro macro = entry.getValue();
                if (macro == null || macro.getSysMacro() == null) {
                    continue;
                }

                String macroCode = macro.getSysMacro().getMacroCode();
                String macroType = macro.getSysMacro().getMacroType();
                String scope = macro.getSysMacro().getScope();

                // 过滤：SQL_ONLY 作用域的宏不注入 JS 环境
                if ("SQL_ONLY".equalsIgnoreCase(scope)) {
                    continue;
                }

                try {
                    if ("VARIABLE".equalsIgnoreCase(macroType)) {
                        // ---- VARIABLE 宏：立即求值并注入 ----
                        // ╔══════════════════════════════════════════════════════════════════╗
                        // ║  ⚠️ 安全警告：此处需要结合系统现有的安全策略/黑名单配置            ║
                        // ╚══════════════════════════════════════════════════════════════════╝
                        StandardEvaluationContext spelCtx = new StandardEvaluationContext();
                        spelCtx.setTypeConverter(CUSTOM_TYPE_CONVERTER);
                        // 注入当前请求参数到 SpEL 上下文
                        if (context != null && !context.isEmpty()) {
                            spelCtx.setVariables(context);
                        }
                        Object evaluatedValue = macro.getCompiledExpression().getValue(spelCtx);
                        bindings.putMember(macroCode, evaluatedValue);

                    } else if ("FUNCTION".equalsIgnoreCase(macroType)) {
                        // ---- FUNCTION 宏：包装为代理函数，延迟求值 ----
                        DynamicMacroFunction proxyFunc = new DynamicMacroFunction(
                                macro.getCompiledExpression(), context, macro.getSysMacro().getMacroParams());
                        bindings.putMember(macroCode, proxyFunc);
                    }
                } catch (Exception e) {
                    // 单条宏注入失败不影响其他宏和脚本执行（异常隔离）
                    log.warn("[JS宏注入] 宏 '{}' 注入失败，已跳过。类型={}, 错误={}",
                            macroCode, macroType, e.getMessage());
                }
            }

            log.debug("[JS宏注入] 宏注入完成，共处理 {} 条宏定义。", allMacros.size());

        } catch (Exception e) {
            // 宏注入整体失败不应阻断 JS 脚本执行（降级策略：仅打日志）
            log.warn("[JS宏注入] 宏注入过程发生异常，脚本将在无宏环境中执行。错误: {}", e.getMessage());
        }
    }

    // ============================= 工具方法 =============================

    /**
     * 校验字符串是否为合法的 JavaScript 标识符（防止 key 注入）
     * 允许字母、数字、下划线、$，首字符不能是数字
     */
    private boolean isValidJsIdentifier(String name) {
        if (name == null || name.isEmpty()) return false;
        char first = name.charAt(0);
        if (!Character.isLetter(first) && first != '_' && first != '$') return false;
        for (int i = 1; i < name.length(); i++) {
            char c = name.charAt(i);
            if (!Character.isLetterOrDigit(c) && c != '_' && c != '$') return false;
        }
        return true;
    }

    /**
     * 将 GraalVM {@link Value} 递归转换为标准 Java 类型。
     * <ul>
     *   <li>null / undefined → null</li>
     *   <li>boolean → Boolean</li>
     *   <li>number (可转 long 且无精度丢失) → Long</li>
     *   <li>number (浮点) → Double</li>
     *   <li>string → String</li>
     *   <li>array → List&lt;Object&gt;</li>
     *   <li>object → Map&lt;String, Object&gt;</li>
     *   <li>其它 → toString()</li>
     * </ul>
     *
     * <p>此方法为 {@code public static}，以便内部类 {@link DynamicMacroFunction} 复用。</p>
     */
    public static Object convertValue(Value value) {
        if (value == null || value.isNull()) {
            return null;
        }
        if (value.isBoolean()) {
            return value.asBoolean();
        }
        if (value.isNumber()) {
            if (value.fitsInLong()) {
                long longVal = value.asLong();
                // 检查是否有小数部分
                if (value.fitsInDouble()) {
                    double doubleVal = value.asDouble();
                    if (doubleVal != (double) longVal) {
                        return doubleVal;
                    }
                }
                return longVal;
            }
            if (value.fitsInDouble()) {
                return value.asDouble();
            }
        }
        if (value.isString()) {
            return value.asString();
        }
        // 数组类型
        if (value.hasArrayElements()) {
            long size = value.getArraySize();
            List<Object> list = new ArrayList<>((int) size);
            for (long i = 0; i < size; i++) {
                list.add(convertValue(value.getArrayElement(i)));
            }
            return list;
        }
        // 普通对象
        if (value.hasMembers()) {
            Map<String, Object> map = new HashMap<>();
            for (String key : value.getMemberKeys()) {
                map.put(key, convertValue(value.getMember(key)));
            }
            return map;
        }
        // 兜底
        return value.toString();
    }

    /**
     * 将字符串安全地转义为 JS 字符串字面量 (单引号包裹)
     */
    private String quoteForJs(String str) {
        StringBuilder sb = new StringBuilder("'");
        for (int i = 0; i < str.length(); i++) {
            char c = str.charAt(i);
            switch (c) {
                case '\\': sb.append("\\\\"); break;
                case '\'': sb.append("\\'"); break;
                case '\n': sb.append("\\n"); break;
                case '\r': sb.append("\\r"); break;
                case '\t': sb.append("\\t"); break;
                default: sb.append(c);
            }
        }
        sb.append("'");
        return sb.toString();
    }

    /**
     * 截断过长的脚本文本，用于错误消息中展示
     */
    private String truncate(String text, int maxLen) {
        if (text == null) return "null";
        if (text.length() <= maxLen) return text;
        return text.substring(0, maxLen) + "...";
    }
}
