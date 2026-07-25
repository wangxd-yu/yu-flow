package org.yu.flow.module.api.service;

import org.springframework.stereotype.Component;
import org.yu.flow.module.assetref.AbstractFlowReferenceChecker;
import org.yu.flow.module.assetref.FlowReferenceIndex;

import java.util.List;

/**
 * 检查 Flow API 是否仍被其他流程的 api 节点引用（targetType=api 或未指定）。
 * <p>优先走 {@link FlowReferenceIndex}；索引未就绪时降级 LIKE 粗筛。</p>
 */
@Component
public class FlowApiReferenceChecker extends AbstractFlowReferenceChecker {

    @Override
    protected String targetType() {
        return "api";
    }

    @Override
    protected List<String> findIndexedLabels(String targetId) {
        return flowReferenceIndex.findApiReferenceLabels(targetId);
    }

    @Override
    protected String assetNoun() {
        return "接口";
    }
}
