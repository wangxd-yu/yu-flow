package org.yu.flow.engine.evaluator;

import org.yu.flow.auto.util.FlowSystemParamsUtil;
import org.yu.flow.engine.evaluator.spel.MacroSpelContexts;
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

    public static Object evaluate(String expr, ExecutionContext executionContext) {
        try {
            StandardEvaluationContext context = MacroSpelContexts.create(executionContext.getVar());
            context.setVariable("var", executionContext.getVar());

            String exprFix = FlowSystemParamsUtil.resolveParams(expr, context);
            String finalExpr = CONVERT_CACHE.computeIfAbsent(exprFix, ExpressionConverter::convertJsonStringSupper);
            Expression exp = getCachedExpression(finalExpr);

            return exp.getValue(context);
        } catch (Exception e) {
            throw new FlowException("EXPRESSION_EVAL_ERROR", "表达式求值失败: " + expr, e);
        }
    }

    public static Object evaluate(String expr, Object rootObject) {
        try {
            EvaluationContext context = MacroSpelContexts.create(rootObject);

            String exprFix = FlowSystemParamsUtil.resolveParams(expr, context);
            String finalExpr = CONVERT_CACHE.computeIfAbsent(exprFix, ExpressionConverter::convertJsonStringSupper);
            return SpELUtils.safeParseExpression(finalExpr, context);
        } catch (Exception e) {
            throw new FlowException("EXPRESSION_EVAL_ERROR", "表达式求值失败: " + expr, e);
        }
    }

    public static Object evaluateObj(String expr, Object object) {
        try {
            EvaluationContext context = MacroSpelContexts.create(object);
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
