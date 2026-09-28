package org.yu.flow.log.audit;

import org.yu.flow.util.FlowObjectMapperUtil;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 审计明细 JSON：值可能来自发布包等外部输入，统一经 ObjectMapper 转义，不要手工拼接。
 */
public final class AuditDetail {

    private AuditDetail() {
    }

    /**
     * @param keyValues 键值交替：key1, value1, key2, value2 …
     */
    public static String of(Object... keyValues) {
        if (keyValues.length % 2 != 0) {
            throw new IllegalArgumentException("审计明细键值须成对出现");
        }
        Map<String, Object> detail = new LinkedHashMap<>();
        for (int i = 0; i < keyValues.length; i += 2) {
            detail.put(String.valueOf(keyValues[i]), keyValues[i + 1]);
        }
        try {
            return FlowObjectMapperUtil.flowObjectMapper().writeValueAsString(detail);
        } catch (Exception e) {
            return "{}";
        }
    }
}
