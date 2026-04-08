package org.yu.flow.util;

import cn.hutool.core.util.StrUtil;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author yu-flow
 * @date 2025-04-30 12:41
 */
public class ReturnDataUtil {
    /**
     * 将结果集中的字段名转换为驼峰命名
     *
     * @param result 查询结果
     * @return 转换后的结果
     */
    public static List<Map<String, Object>> convertToCamelCase(List<Map<String, Object>> result) {
        List<Map<String, Object>> camelCaseResult = new ArrayList<>();

        for (Map<String, Object> row : result) {
            Map<String, Object> camelCaseRow = new HashMap<>();
            for (Map.Entry<String, Object> entry : row.entrySet()) {
                String camelCaseKey = StrUtil.toCamelCase(entry.getKey());
                camelCaseRow.put(camelCaseKey, entry.getValue());
            }
            camelCaseResult.add(camelCaseRow);
        }

        return camelCaseResult;
    }
}
