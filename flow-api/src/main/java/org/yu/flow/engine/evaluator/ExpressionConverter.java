package org.yu.flow.engine.evaluator;

import lombok.extern.slf4j.Slf4j;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
public class ExpressionConverter {

    // 正则表达式匹配 ${xxx.xxx} 格式
    //private static final Pattern PATTERN = Pattern.compile("\\$\\{([^}]+)\\}");
    // 匹配 "${...}" 或 ${...}
    private static final Pattern PATTERN = Pattern.compile("\"(\\$\\{[^}]+\\})\"|(\\$\\{[^}]+\\})");

    /**
     * 匹配 ${任意.属性.路径}
     * 将匹配到的${xxx.student.name}替换为['xxx']['student']['name']
     * @param expression
     * @return
     */
    public static String convertJsonStringSupper(String expression) {
        Matcher matcher = PATTERN.matcher(expression);
        StringBuffer sb = new StringBuffer();

        String content;
        while (matcher.find()) {
            // 判断匹配的是哪种情况
            if (matcher.group(1) != null) {
                // 情况1：匹配到 "${...}"，去掉外层引号
                content = matcher.group(1); // 获取${...}部分
            } else {
                // 情况2：匹配到 ${...}，直接使用
                content = matcher.group(2);
            }

            // 处理内部可能存在的引号（如${"xxx"}）
            String propertyPath = content.substring(2, content.length()-1); // 去掉${和}

            // 转换为 var['xxx']['yyy'] 格式（避免 SpEL 将 ['x'] 解析为 List 字面量）
            String replacement = convertToVarBracketNotation(propertyPath);

            // 替换为${['xxx']['yyy']}格式
            matcher.appendReplacement(sb, replacement);
        }

        matcher.appendTail(sb);
        log.debug("EL表达式转换结果: {}", sb);
        return sb.toString();
    }

    // 添加对默认值的支持，如 ${studentInfo.name:张三}
    private static final Pattern PATTERN_WITH_DEFAULT = Pattern.compile("\\$\\{(.+?)(?::(.+?))?\\}");

    public static String convertWithDefault(String expression) {
        Matcher matcher = PATTERN_WITH_DEFAULT.matcher(expression);
        if (matcher.matches()) {
            String key = matcher.group(1);
            String defaultValue = matcher.group(2);
            if (defaultValue != null) {
                return "var.getOrDefault('" + key + "', '" + defaultValue + "')";
            }
            return "var['" + key + "']";
        }
        return expression;
    }

    /**
     * 将所有点表达式转换为['key']格式
     * 处理情况：
     * 1. 单属性：name -> ['name']
     * 2. 多级属性：student.name -> ['student']['name']
     * 3. 数组索引：students[0].name -> ['students'][0]['name']
     */
    public static String convertToBracketNotation(String dotExpression) {
        String[] parts = dotExpression.split("\\.");
        StringBuilder sb = new StringBuilder();

        for (String part : parts) {
            // 处理数组索引情况（如addresses[0]）
            if (part.endsWith("]")) {
                sb.append(part);
            } else {
                // 对普通属性添加方括号
                //if (i > 0) sb.append("."); // 保持SpEL语法，实际可以去掉
                sb.append("['").append(part).append("']");
            }
        }

        return sb.toString();
    }

    public static String convertToVarBracketNotation(String dotExpression) {
        if (dotExpression == null || dotExpression.isEmpty()) {
            return "#var";
        }

        String[] parts = dotExpression.split("\\.");
        StringBuilder sb = new StringBuilder("#var");
        for (String part : parts) {
            if (part == null || part.isEmpty()) {
                continue;
            }
            int bracketIndex = part.indexOf('[');
            if (bracketIndex >= 0) {
                String base = part.substring(0, bracketIndex);
                if (!base.isEmpty()) {
                    sb.append("['").append(base).append("']");
                }
                sb.append(part.substring(bracketIndex));
            } else {
                sb.append("['").append(part).append("']");
            }
        }
        return sb.toString();
    }

    /*public static void main(String[] args) {
        // 测试单个表达式转换
        String singleExpr = "${studentInfo.name}";
        // [DEBUG] removed println // 输出: var['studentInfo.name']

        // 测试完整JSON字符串转换
        String jsonStr = "{name:${studentInfo.name}, age:${studentInfo.age}, scores:{'语文':${chineseScore}, '数学':${mathScore}}}";
        String converted = convertJsonString(jsonStr);
        // [DEBUG] removed println
        // 输出: {name:var['studentInfo.name'], age:var['studentInfo.age'], scores:{'语文':var['chineseScore'], '数学':var['mathScore']}}
    }*/

    // 测试用例
    public static void main(String[] args) {
        /*// [DEBUG] removed println
        // 输出: ['user']

        // [DEBUG] removed println
        // 输出: ['user'].name

        // [DEBUG] removed println
        // 输出: ['user'].address.city

        // [DEBUG] removed println*/
        // 输出: 前缀['obj']中缀['obj'].prop后缀
    }
}
