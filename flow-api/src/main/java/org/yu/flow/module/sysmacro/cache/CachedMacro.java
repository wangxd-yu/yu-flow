package org.yu.flow.module.sysmacro.cache;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.yu.flow.module.sysmacro.domain.SysMacroDO;
import org.springframework.expression.Expression;

/**
 * 宏定义本地缓存条目
 *
 * <p>将数据库中的 {@link SysMacroDO} 实体与预编译好的 SpEL {@link Expression} 对象
 * 捆绑存储在一起，避免运行时重复解析 SpEL 字符串带来的 CPU 开销。</p>
 *
 * <h3>设计理由</h3>
 * <ul>
 *   <li><b>sysMacro</b>：保留原始的数据库实体，包含 macroCode、macroType、scope、
 *       returnType 等元数据，供动态 API 引擎在执行前进行作用域判断和类型推导。</li>
 *   <li><b>compiledExpression</b>：SpEL {@code ExpressionParser.parseExpression()} 的输出。
 *       该对象是线程安全的，可在多线程环境下并发调用 {@code getValue()}，
 *       一次编译即可反复执行，显著降低热路径上的 CPU 消耗。</li>
 * </ul>
 *
 * yu-flow
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class CachedMacro {

    /**
     * 原始的数据库实体对象（包含 macroCode, macroType, scope, returnType 等元数据）
     */
    private SysMacroDO sysMacro;

    /**
     * 预编译好的 SpEL 表达式对象（线程安全，可并发调用 getValue）
     */
    private Expression compiledExpression;
}
