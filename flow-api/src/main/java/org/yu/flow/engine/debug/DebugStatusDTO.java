package org.yu.flow.engine.debug;

import lombok.Data;
import lombok.experimental.Accessors;
import org.yu.flow.engine.model.FlowTrace;

import java.util.Map;
import java.util.Set;

/**
 * 调试会话状态响应 DTO。
 *
 * <p>前端通过轮询 {@code GET /debug/session/{id}/status} 获取此对象，
 * 用于判断引擎是否挂起、当前节点信息、变量快照等。</p>
 *
 * @author yu-flow
 * @since 1.0
 */
@Data
@Accessors(chain = true)
public class DebugStatusDTO {

    /** 会话 ID */
    private String sessionId;

    /** 当前状态：RUNNING / SUSPENDED / COMPLETED / CANCELLED */
    private String status;

    /** 当前挂起的节点 ID（仅 SUSPENDED 状态有值） */
    private String suspendedNodeId;

    /** 当前挂起的节点名称 */
    private String suspendedNodeName;

    /** 当前挂起时刻的上下文变量快照（仅 SUSPENDED 状态有值） */
    private Map<String, Object> variables;

    /** 断点列表 */
    private Set<String> breakpoints;

    /** 最终 Trace 报告（仅 COMPLETED 状态有值） */
    private FlowTrace trace;

    /** 错误信息（仅出错时有值） */
    private String errorMessage;

    /**
     * 从 DebugSession 实体构建响应 DTO。
     */
    public static DebugStatusDTO from(DebugSession session) {
        DebugStatusDTO dto = new DebugStatusDTO();
        dto.setSessionId(session.getSessionId());
        dto.setStatus(session.getStatus().name());
        dto.setBreakpoints(session.getBreakpoints());

        switch (session.getStatus()) {
            case SUSPENDED:
                dto.setSuspendedNodeId(session.getSuspendedNodeId());
                dto.setSuspendedNodeName(session.getSuspendedNodeName());
                dto.setVariables(session.getSuspendedVariables());
                break;
            case COMPLETED:
                dto.setTrace(session.getFinalTrace());
                dto.setErrorMessage(session.getErrorMessage());
                break;
            default:
                break;
        }

        return dto;
    }
}
