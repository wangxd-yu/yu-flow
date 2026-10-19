package org.yu.flow.module.api.runtime;

import java.util.HashMap;
import java.util.Map;

/** 运行时入参的取值工具：网关、流程内调用、调试运行传入的键名不完全一致 */
final class ApiParams {

    private ApiParams() {
    }

    /** 按顺序返回第一个存在的键对应的值（值可以是 null） */
    static Object first(Map<String, Object> source, String... keys) {
        if (source == null || keys == null) {
            return null;
        }
        for (String key : keys) {
            if (source.containsKey(key)) {
                return source.get(key);
            }
        }
        return null;
    }

    /** 非 Map 返回空的可变 Map；键统一转成字符串 */
    static Map<String, Object> toObjectMap(Object value) {
        if (!(value instanceof Map<?, ?> source)) {
            return new HashMap<>();
        }
        Map<String, Object> result = new HashMap<>(source.size());
        source.forEach((k, v) -> {
            if (k != null) {
                result.put(String.valueOf(k), v);
            }
        });
        return result;
    }
}
