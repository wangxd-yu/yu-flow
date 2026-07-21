package org.yu.flow.engine.debug;

import lombok.Data;
import lombok.experimental.Accessors;
import org.yu.flow.engine.model.ExecutionLog;
import org.yu.flow.engine.model.FlowTrace;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 调试会话状态响应 DTO。
 *
 * <p>前端通过轮询 {@code GET /debug/session/{id}/status} 获取此对象。
 * stepLogs 支持分页，避免长流程一次性下发超大 Trace。</p>
 *
 * <p><b>分布式：</b>会话存本机内存，多节点需会话粘性或仅在启动会话的节点轮询。</p>
 */
@Data
@Accessors(chain = true)
public class DebugStatusDTO {

    private String sessionId;
    private String status;
    private String suspendedNodeId;
    private String suspendedNodeName;
    private Map<String, Object> variables;
    private Set<String> breakpoints;

    /**
     * 分页后的 Trace 摘要（含本页 stepLogs；不含未请求的历史步）。
     */
    private FlowTrace trace;

    private String errorMessage;

    /** stepLogs 总数 */
    private int stepLogTotal;

    private int stepLogOffset;
    private int stepLogLimit;

    /** 是否还有更多 stepLogs */
    private boolean stepLogHasMore;

    public static DebugStatusDTO from(DebugSession session) {
        return from(session, 0, 50);
    }

    public static DebugStatusDTO from(DebugSession session, int stepOffset, int stepLimit) {
        int offset = Math.max(0, stepOffset);
        int limit = stepLimit <= 0 ? 50 : Math.min(stepLimit, 200);

        DebugStatusDTO dto = new DebugStatusDTO();
        dto.setSessionId(session.getSessionId());
        dto.setStatus(session.getStatus().name());
        dto.setBreakpoints(session.getBreakpoints());
        dto.setStepLogOffset(offset);
        dto.setStepLogLimit(limit);

        switch (session.getStatus()) {
            case SUSPENDED:
                dto.setSuspendedNodeId(session.getSuspendedNodeId());
                dto.setSuspendedNodeName(session.getSuspendedNodeName());
                dto.setVariables(session.getSuspendedVariables());
                break;
            case COMPLETED:
                dto.setErrorMessage(session.getErrorMessage());
                break;
            default:
                break;
        }

        FlowTrace active = session.getActiveTrace();
        attachPagedTrace(dto, active, offset, limit);
        return dto;
    }

    private static void attachPagedTrace(DebugStatusDTO dto, FlowTrace source, int offset, int limit) {
        if (source == null) {
            dto.setStepLogTotal(0);
            dto.setStepLogHasMore(false);
            return;
        }
        List<ExecutionLog> all = source.getStepLogs() != null
                ? source.getStepLogs() : Collections.emptyList();
        int total = all.size();
        dto.setStepLogTotal(total);

        int from = Math.min(offset, total);
        int to = Math.min(from + limit, total);
        List<ExecutionLog> page = from < to
                ? new ArrayList<>(all.subList(from, to))
                : Collections.emptyList();
        dto.setStepLogHasMore(to < total);

        FlowTrace slim = new FlowTrace();
        slim.setTraceId(source.getTraceId());
        slim.setStartTime(source.getStartTime());
        slim.setEndTime(source.getEndTime());
        slim.setTotalDurationMs(source.getTotalDurationMs());
        slim.setStatus(source.getStatus());
        slim.setErrorMsg(source.getErrorMsg());
        slim.setDslContentHash(source.getDslContentHash());
        // 分页响应不塞全文 DSL / 全量 global I/O，降低轮询体积
        slim.setGlobalInputs(offset == 0 ? source.getGlobalInputs() : null);
        slim.setGlobalOutputs(to >= total ? source.getGlobalOutputs() : null);
        slim.setStepLogs(page);
        dto.setTrace(slim);
    }
}
