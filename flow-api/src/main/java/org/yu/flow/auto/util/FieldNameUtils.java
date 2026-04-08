package org.yu.flow.auto.util;

/**
 * @author yu-flow
 * @date 2025-03-06 09:58
 */
public class FieldNameUtils {
    /**
     * 将下划线命名转换为驼峰命名
     *
     * @param fieldName 字段名（下划线分割）
     * @return 驼峰命名字段名
     */
    public static String toCamelCase(String fieldName) {
        if (fieldName == null || fieldName.isEmpty()) {
            return fieldName;
        }

        StringBuilder result = new StringBuilder();
        boolean nextUpper = false;

        for (int i = 0; i < fieldName.length(); i++) {
            char currentChar = fieldName.charAt(i);

            if (currentChar == '_') {
                nextUpper = true;
            } else {
                if (nextUpper) {
                    result.append(Character.toUpperCase(currentChar));
                    nextUpper = false;
                } else {
                    result.append(Character.toLowerCase(currentChar));
                }
            }
        }

        return result.toString();
    }
}
