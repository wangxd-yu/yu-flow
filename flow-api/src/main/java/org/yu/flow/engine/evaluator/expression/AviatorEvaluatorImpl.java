package org.yu.flow.engine.evaluator.expression;

import com.googlecode.aviator.AviatorEvaluator;
import com.googlecode.aviator.AviatorEvaluatorInstance;
import com.googlecode.aviator.Feature;
import com.googlecode.aviator.Options;
import org.yu.flow.exception.FlowException;

import java.util.Map;

/**
 * Aviator 表达式求值器实现
 *
 * <p>安全与性能设计:
 * <ol>
 *   <li><b>独立实例</b>: 使用 {@link AviatorEvaluator#newInstance()} 创建独立实例，
 *       避免与全局实例产生状态污染。</li>
 *   <li><b>编译缓存</b>: 通过 {@code compile(expression, true)} 启用表达式编译缓存，
 *       相同表达式字符串仅编译一次，后续调用直接复用字节码，性能提升 5-10 倍。</li>
 *   <li><b>Feature 白名单沙箱</b>: 通过 {@link Options#FEATURE_SET} 显式声明允许的语言特性，
 *       仅保留赋值、控制流、Lambda 等安全特性；禁用 {@code NewInstance}（Java 对象实例化）、
 *       {@code Module}（模块加载）、{@code InternalVars}（内部变量如 __env__）、
 *       {@code StaticMethods / StaticFields}（Java 静态方法/字段访问）等危险特性。</li>
 * </ol>
 *
 * <p>AviatorScript 语法特点:
 * <ul>
 *   <li>变量直接引用，无需前缀: {@code age >= 18}</li>
 *   <li>字符串使用单引号: {@code 'Hello'}</li>
 *   <li>支持数学运算和逻辑运算</li>
 * </ul>
 *
 * @see <a href="https://github.com/killme2008/aviator">Aviator GitHub</a>
 */
public class AviatorEvaluatorImpl implements ExpressionEvaluatorStrategy {

    /** 独立的 Aviator 引擎实例，线程安全，内部持有编译缓存 */
    private static final AviatorEvaluatorInstance aviator;

    static {
        aviator = AviatorEvaluator.newInstance();

        // ── Feature 白名单沙箱 ──
        // 仅允许以下安全的脚本特性；未列入的特性（如 NewInstance, Module,
        // InternalVars, StaticMethods, StaticFields, ExceptionHandle 等）将被禁用。
        // 基础运算（算术、比较、字符串拼接）属于核心语法，不受 Feature 白名单控制，始终可用。
        aviator.setOption(Options.FEATURE_SET, Feature.asSet(
                Feature.Assignment,       // 变量赋值: a = 1
                Feature.Return,           // return 语句
                Feature.If,               // if/elsif/else 条件
                Feature.ForLoop,          // for 循环
                Feature.WhileLoop,        // while 循环
                Feature.Let,              // let 局部变量声明
                Feature.LexicalScope,     // 词法作用域
                Feature.Lambda,           // lambda 表达式: lambda(x) -> x * 2 end
                Feature.Fn,               // fn 函数定义: fn add(a, b) a + b end
                Feature.StringInterpolation // 字符串插值: "Hello #{name}"
        ));
    }

    @Override
    public Object evaluate(String expression, Map<String, Object> context) {
        if (expression == null || expression.trim().isEmpty()) {
            return null;
        }

        try {
            // compile(expression, cached=true):
            // 以表达式字符串为 key 缓存编译后的字节码，相同表达式仅编译一次。
            // execute(context): 以 context Map 作为变量环境执行已编译的表达式。
            return aviator.compile(expression, true).execute(context);
        } catch (Exception e) {
            throw new FlowException("AVIATOR_EVAL_ERROR",
                    "Aviator 表达式求值失败: " + expression + ", 错误: " + e.getMessage(), e);
        }
    }

    @Override
    public boolean evaluateBoolean(String expression, Map<String, Object> context) {
        Object result = evaluate(expression, context);
        if (result instanceof Boolean) {
            return (Boolean) result;
        }
        if (result == null) {
            return false;
        }
        // Aviator 可能返回数字类型作为布尔
        if (result instanceof Number) {
            return ((Number) result).doubleValue() != 0;
        }
        return Boolean.parseBoolean(result.toString());
    }
}
