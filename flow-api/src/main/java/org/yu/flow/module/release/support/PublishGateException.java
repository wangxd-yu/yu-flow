package org.yu.flow.module.release.support;

import org.yu.flow.exception.FlowException;
import org.yu.flow.module.release.dto.PublishGateResultDTO;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 发布门禁未通过。
 */
public class PublishGateException extends FlowException {

    private final PublishGateResultDTO result;

    public PublishGateException(PublishGateResultDTO result) {
        super("PUBLISH_GATE_BLOCKED",
                result != null && result.getMessage() != null ? result.getMessage() : "发布门禁未通过",
                null,
                buildContext(result),
                null,
                Severity.ERROR);
        this.result = result;
    }

    private static Map<String, Object> buildContext(PublishGateResultDTO result) {
        Map<String, Object> ctx = new LinkedHashMap<>();
        if (result != null) {
            ctx.put("gate", result);
        }
        return ctx;
    }

    public PublishGateResultDTO getResult() {
        return result;
    }
}
