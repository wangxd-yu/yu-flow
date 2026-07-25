package org.yu.flow.module.serviceflow.service;

import org.springframework.stereotype.Component;
import org.yu.flow.module.assetref.AbstractFlowReferenceChecker;
import org.yu.flow.module.assetref.FlowReferenceIndex;

import java.util.List;

/**
 * 检查内部服务是否仍被其他流程的 api 节点引用（targetType=service）。
 * <p>优先走 {@link FlowReferenceIndex}；索引未就绪时降级 LIKE 粗筛。</p>
 */
@Component
public class ServiceFlowReferenceChecker extends AbstractFlowReferenceChecker {

    @Override
    protected String targetType() {
        return "service";
    }

    @Override
    protected List<String> findIndexedLabels(String targetId) {
        return flowReferenceIndex.findServiceReferenceLabels(targetId);
    }

    @Override
    protected String assetNoun() {
        return "内部服务";
    }
}
