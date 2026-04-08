package org.yu.flow.engine.evaluator;

import org.yu.flow.auto.util.FlowSystemParamsUtil;
import org.yu.flow.exception.FlowException;
import org.yu.flow.engine.util.SpELUtils;
import org.springframework.expression.EvaluationContext;
import org.springframework.expression.Expression;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class ExpressionEvaluator {
    private static final ExpressionParser PARSER = new SpelExpressionParser();
    private static final Map<String, Expression> EXPR_CACHE = new ConcurrentHashMap<>(1024);
    private static final Map<String, String> CONVERT_CACHE = new ConcurrentHashMap<>(1024);

    private static Expression getCachedExpression(String expressionString) {
        return EXPR_CACHE.computeIfAbsent(expressionString, PARSER::parseExpression);
    }

    /*public Object evaluate(String expr, ExecutionContext context) {
        try {
            return context.getVariable(expr);
        } catch (Exception e) {
            throw new FlowException("EXPRESSION_EVAL_ERROR", "表达式求值失败: " + expr, e);
        }
    }*/

    public static Object evaluate(String expr, ExecutionContext executionContext) {
        try {
            // 以变量 Map 作为根对象，并额外暴露为变量 var（用于 var['x'] 访问）
            EvaluationContext context = new StandardEvaluationContext(executionContext.getVar());
            ((StandardEvaluationContext) context).setVariable("var", executionContext.getVar());

            // 解析 expr，将其中的 系统参数注册到 EvaluationContext，并修改 expr
            String exprFix = FlowSystemParamsUtil.resolveParams(expr, context);
            String finalExpr = CONVERT_CACHE.computeIfAbsent(exprFix, ExpressionConverter::convertJsonStringSupper);
            // 解析表达式（使用缓存）
            Expression exp = getCachedExpression(finalExpr);

            return exp.getValue(context);
            //return SpELUtils.safeParseExpression(ExpressionConverter.convertJsonStringSupper(exprFix), context);
        } catch (Exception e) {
            throw new FlowException("EXPRESSION_EVAL_ERROR", "表达式求值失败: " + expr, e);
        }
    }

    public static Object evaluate(String expr, Object rootObject) {
        try {
            // 设置根对象为 executionContext
            EvaluationContext context = new StandardEvaluationContext(rootObject);

            // 解析 expr，将其中的 系统参数注册到 EvaluationContext，并修改 expr
            String exprFix = FlowSystemParamsUtil.resolveParams(expr, context);
            String finalExpr = CONVERT_CACHE.computeIfAbsent(exprFix, ExpressionConverter::convertJsonStringSupper);
            return SpELUtils.safeParseExpression(finalExpr, context);
        } catch (Exception e) {
            throw new FlowException("EXPRESSION_EVAL_ERROR", "表达式求值失败: " + expr, e);
        }
    }

    public static Object evaluateObj(String expr, Object object) {
        try {
            // 设置根对象为 executionContext
            EvaluationContext context = new StandardEvaluationContext(object);
            // 解析表达式（使用缓存）
            Expression exp = getCachedExpression(ExpressionConverter.convertToBracketNotation(expr));
            return exp.getValue(context);
        } catch (Exception e) {
            throw new FlowException("EXPRESSION_EVAL_ERROR", "表达式求值失败: " + expr, e);
        }
    }

    public void evaluateAssignment(String assignmentExpr, ExecutionContext context) {
        String[] parts = assignmentExpr.split("=", 2);
        if (parts.length != 2) {
            throw new FlowException("INVALID_ASSIGNMENT", "表达式格式错误: " + assignmentExpr);
        }

        String varName = parts[0].trim();
        String expr = parts[1].trim();
        Object value = evaluate(expr, context);
        context.setVar(varName, value);
    }

    public Object[] evaluateArguments(List<String> args, ExecutionContext context) {
        return args.stream()
                .map(arg -> evaluate(arg, context))
                .toArray();
    }
}
