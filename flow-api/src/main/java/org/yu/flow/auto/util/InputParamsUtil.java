package org.yu.flow.auto.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.yu.flow.engine.evaluator.ExpressionEvaluator;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;

import java.util.HashMap;
import java.util.Map;

/**
 * 处理输入参数
 *
 * @author yu-flow
 * @date 2025-05-24 13:34
 */
public class InputParamsUtil {
    /**
     * 参数来源
     * 前缀：# 系统参数
     * 前缀：@ , 上下文传参，包括： "FP"：functionParams(方法传参), "BP":bodyParams(body请求传参), "QP":queryParams(query请求传参)
     * 前缀：无,按上下文传参优先级查找对象，FP -> BP -> QP。主要用于 单节点flow时，直接从 BP、QP查找变量
     */

    private static final ExpressionParser parser = new SpelExpressionParser();
    private static final String[] SEARCH_ORDER = {"@FP", "@BP", "@QP"};

    public static boolean hasParam(Map<String, ?> params, String keyExpression) {
        return InputParamsUtil.resolveParam(params, keyExpression) != null;
    }

    /**
     * 从参数Map中获取指定数据
     *
     * @param params        参数Map (包含FP、BP、QP三个键)
     * @param keyExpression 键表达式
     * @return 找到的值或null
     */
    public static Object resolveParam(Map<String, ?> params, String keyExpression) {
        if (keyExpression == null || keyExpression.isEmpty()) {
            return null;
        }

        // 处理全局宏字典 (#macroCode)，如 #UUID、#DATE_TIME、#GET_ENV
        if (keyExpression.startsWith("#")) {
            return FlowSystemParamsUtil.getParams(keyExpression.substring(1), params);
        }

        // 处理指上下文传参 (@FP.student.name)
        if (keyExpression.startsWith("@")) {
            if (params == null) {
                return null;
            }
            return ExpressionEvaluator.evaluateObj(keyExpression, params);
        }

        // 处理不指定key的情况 (student.name)
        return resolveUnspecifiedParam(params, keyExpression);
    }



    /**
     * 解析指定了参数源的表达式
     */
    private static Object resolveSpecificParam(Map<String, ?> params, String expression) {
        String[] parts = expression.split("\\.", 2);
        if (parts.length < 2) {
            return null;
        }

        String paramSource = parts[0];
        Object rootObject = params.get(paramSource);
        if (rootObject == null) {
            return null;
        }

        return resolveValue(rootObject, parts[1]);
    }

    /**
     * 解析未指定参数源的表达式 (按顺序查找)
     */
    private static Object resolveUnspecifiedParam(Map<String, ?> params, String expression) {
        //先查找params 本身
        Object result = resolveValue(params, expression);
        if (result != null) {
            return result;
        }
        for (String source : SEARCH_ORDER) {
            Object rootObject = params.get(source);
            if (rootObject == null) {
                continue;
            }

            result = resolveValue(rootObject, expression);
            if (result != null) {
                return result;
            }
        }
        return null;
    }

    /**
     * 根据输入类型选择解析方式
     */
    private static Object resolveValue(Object rootObject, String path) {
        if (rootObject instanceof JsonNode) {
            return resolveJsonPath((JsonNode) rootObject, path);
        } else {
            return ExpressionEvaluator.evaluateObj(path, rootObject);
        }
    }

    /**
     * 使用Jackson原生方式解析JsonNode路径
     */
    private static Object resolveJsonPath(JsonNode node, String path) {
        JsonNode current = node;
        for (String part : cn.hutool.core.util.StrUtil.split(path, '.')) {
            if (current == null) {
                return null;
            }
            current = current.get(part);
        }

        if (current == null) {
            return null;
        }

        // 根据节点类型返回合适的Java对象
        if (current.isTextual()) {
            return current.asText();
        } else if (current.isNumber()) {
            return current.numberValue();
        } else if (current.isBoolean()) {
            return current.asBoolean();
        } else if (current.isObject() || current.isArray()) {
            return current;
        }

        return null;
    }

}
