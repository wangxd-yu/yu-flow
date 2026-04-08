package org.yu.flow.auto.druid;

import org.yu.flow.auto.util.InputParamsUtil;

import java.util.Collections;
import java.util.Map;

/**
 * 参数上下文类，用于跟踪参数存在性
 */
public class ParamContext {
    private final Map<String, Object> params;

    public ParamContext(Map<String, Object> params) {
        this.params = params != null ? params : Collections.emptyMap();
    }

    public boolean containsParam(String paramName) {
        return InputParamsUtil.hasParam(params, paramName);
    }

    public Object getParam(String paramName) {
        return InputParamsUtil.resolveParam(params, paramName);
    }
}
