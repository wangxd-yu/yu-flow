package org.yu.flow.engine.debug;

import lombok.Data;

import java.util.Map;

/**
 * 调试恢复/单步跳过请求 DTO。
 *
 * <p>前端在调试面板中查看变量后，通过此 DTO 向后端发送恢复指令。</p>
 *
 * @author yu-flow
 * @since 1.0
 */
@Data
public class DebugResumeDTO {

    /**
     * 操作类型：
     * <ul>
     *   <li>{@code "resume"} — 继续运行到下一个断点或流程结束 (F8)</li>
     *   <li>{@code "stepOver"} — 执行当前节点后，在下一个节点入口处再次挂起 (F6)</li>
     * </ul>
     */
    private String action;

    /**
     * 可选的变量修改映射。
     * <p>前端用户在调试面板中直接修改的变量，键值对将在引擎恢复执行前
     * 注入回 ExecutionContext。null 或空 Map 表示不修改变量。</p>
     */
    private Map<String, Object> variableUpdates;
}
