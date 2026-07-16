package org.yu.flow.engine.evaluator.expression;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Engine;
import org.graalvm.polyglot.HostAccess;
import org.graalvm.polyglot.PolyglotException;
import org.graalvm.polyglot.Value;
import org.yu.flow.exception.FlowException;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * GraalPy 脚本求值器（可选能力）。
 *
 * <p>未安装 GraalPy 时应用可正常启动，也不会在探测阶段抛错。
 * 仅当用户选择 Python 并执行脚本时才会尝试使用；运行时缺失时静默返回 {@code null}。</p>
 *
 * <p>部署环境若需启用 Python，请使用 GraalVM for JDK 17，并安装匹配的 Python 组件。</p>
 */
@Slf4j
public class PythonEvaluatorImpl implements ExpressionEvaluatorStrategy {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final Object ENGINE_LOCK = new Object();
    private static final AtomicBoolean UNAVAILABLE_WARNED = new AtomicBoolean(false);

    /** 惰性创建；探测失败时保持 null，不影响应用启动。 */
    private static volatile Engine sharedEngine;
    private static volatile Boolean runtimeAvailable;

    @Override
    public Object evaluate(String expression, Map<String, Object> context) {
        if (expression == null || expression.trim().isEmpty()) {
            return null;
        }
        if (!isRuntimeAvailable()) {
            if (UNAVAILABLE_WARNED.compareAndSet(false, true)) {
                log.warn("[Python] 当前 JVM 未安装 GraalPy，Python 脚本将静默跳过并返回 null。"
                        + "如需启用，请使用 GraalVM for JDK 17 并安装匹配的 Python 组件。");
            } else {
                log.debug("[Python] GraalPy 不可用，脚本已跳过: {}", truncate(expression, 120));
            }
            return null;
        }

        Engine engine = sharedEngine;
        try (Context pythonContext = Context.newBuilder("python")
                .engine(engine)
                .allowHostAccess(HostAccess.NONE)
                .allowHostClassLookup(className -> false)
                .allowIO(false)
                .allowCreateProcess(false)
                .allowCreateThread(false)
                .allowNativeAccess(false)
                .build()) {
            injectInput(pythonContext, context, expression);

            try {
                Value result = pythonContext.eval("python",
                        "eval(compile(__yu_script__, '<evaluate>', 'eval'), globals())");
                return convertValue(result);
            } catch (PolyglotException expressionError) {
                if (!expressionError.isSyntaxError()) {
                    throw expressionError;
                }
            }

            String wrappedScript = buildFunctionScript(expression);
            pythonContext.eval("python", wrappedScript);
            return convertValue(pythonContext.getBindings("python").getMember("__yu_flow_result__"));
        } catch (PolyglotException e) {
            String errorType = e.isSyntaxError() ? "PYTHON_SYNTAX_ERROR" : "PYTHON_RUNTIME_ERROR";
            throw new FlowException(errorType,
                    "Python 脚本执行失败: " + e.getMessage() + " | 脚本: " + truncate(expression, 200), e);
        } catch (FlowException e) {
            throw e;
        } catch (Exception e) {
            throw new FlowException("PYTHON_EVAL_ERROR",
                    "Python 脚本求值失败: " + truncate(expression, 200) + ", 错误: " + e.getMessage(), e);
        }
    }

    /**
     * 静默探测当前 JVM 是否具备 Python 语言组件。
     * 任何异常都视为不可用，不影响启动。
     */
    public static boolean isRuntimeAvailable() {
        Boolean cached = runtimeAvailable;
        if (cached != null) {
            return cached;
        }
        synchronized (ENGINE_LOCK) {
            if (runtimeAvailable != null) {
                return runtimeAvailable;
            }
            try {
                Engine engine = Engine.newBuilder()
                        .option("engine.WarnInterpreterOnly", "false")
                        .build();
                boolean available = engine.getLanguages().containsKey("python");
                if (available) {
                    sharedEngine = engine;
                    runtimeAvailable = true;
                    log.info("[Python] 已检测到 GraalPy 运行时，Python Evaluate 节点可用。");
                } else {
                    closeQuietly(engine);
                    sharedEngine = null;
                    runtimeAvailable = false;
                    log.debug("[Python] 未检测到 GraalPy 运行时，Python Evaluate 节点将静默跳过。");
                }
            } catch (Throwable t) {
                sharedEngine = null;
                runtimeAvailable = false;
                log.debug("[Python] GraalPy 探测失败，按不可用处理: {}", t.toString());
            }
            return runtimeAvailable;
        }
    }

    private void injectInput(Context pythonContext, Map<String, Object> context, String expression) throws Exception {
        String inputJson = OBJECT_MAPPER.writeValueAsString(context == null ? new LinkedHashMap<>() : context);
        Value bindings = pythonContext.getBindings("python");
        bindings.putMember("__yu_input_json__", inputJson);
        bindings.putMember("__yu_script__", expression);
        pythonContext.eval("python",
                "import json as __yu_json\n"
                        + "input = __yu_json.loads(__yu_input_json__)\n"
                        + "del __yu_input_json__\n"
                        + "for __yu_key, __yu_value in input.items():\n"
                        + "    if __yu_key.isidentifier():\n"
                        + "        globals()[__yu_key] = __yu_value\n");
    }

    private String buildFunctionScript(String expression) {
        StringBuilder script = new StringBuilder("def __yu_flow_main__():\n");
        String[] lines = expression.replace("\r\n", "\n").replace('\r', '\n').split("\n", -1);
        for (String line : lines) {
            script.append("    ").append(line).append('\n');
        }
        script.append("    return locals().get('result')\n")
                .append("__yu_flow_result__ = __yu_flow_main__()\n");
        return script.toString();
    }

    /**
     * 将 Polyglot Value 递归转换为普通 Java 类型。
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
                return value.asLong();
            }
            if (value.fitsInDouble()) {
                return value.asDouble();
            }
            return value.toString();
        }
        if (value.isString()) {
            return value.asString();
        }
        if (value.hasArrayElements()) {
            List<Object> result = new ArrayList<>((int) value.getArraySize());
            for (long i = 0; i < value.getArraySize(); i++) {
                result.add(convertValue(value.getArrayElement(i)));
            }
            return result;
        }
        if (value.hasHashEntries()) {
            Map<String, Object> result = new LinkedHashMap<>();
            Value keys = value.getHashKeysIterator();
            while (keys.hasIteratorNextElement()) {
                Value key = keys.getIteratorNextElement();
                result.put(String.valueOf(convertValue(key)), convertValue(value.getHashValue(key)));
            }
            return result;
        }
        if (value.hasMembers()) {
            Map<String, Object> result = new LinkedHashMap<>();
            for (String key : value.getMemberKeys()) {
                result.put(key, convertValue(value.getMember(key)));
            }
            return result;
        }
        return value.toString();
    }

    private static void closeQuietly(Engine engine) {
        if (engine == null) {
            return;
        }
        try {
            engine.close();
        } catch (Throwable ignored) {
            // 探测失败时的清理不影响主流程
        }
    }

    private String truncate(String text, int maxLength) {
        return text.length() <= maxLength ? text : text.substring(0, maxLength) + "...";
    }
}
