package org.yu.flow.engine.model.step;

import lombok.Data;
import lombok.EqualsAndHashCode;
import org.yu.flow.engine.model.NodeType;
import org.yu.flow.engine.model.PortDefinition;
import org.yu.flow.engine.model.PortNames;
import org.yu.flow.engine.model.Step;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * 内部 Flow API 编排调用节点。
 *
 * <p>配置：
 * <ul>
 *   <li>{@code serviceId} — 目标 Flow API 实体 ID</li>
 *   <li>{@code inputs} — V3 参数映射（JSONPath extractPath），执行时组装为 {@code @FP}</li>
 *   <li>{@code args} — 旧版参数映射（{@code #macro} / {@code @FP.x} 表达式），仅作兼容</li>
 *   <li>{@code output} — 可选：额外写入的上下文变量名（主结果仍在 {@code {nodeId}.out}）</li>
 * </ul>
 */
@EqualsAndHashCode(callSuper = true)
@Data
public class ApiServiceCallStep extends Step {

    /** 目标 Flow API 的主键 ID */
    private String serviceId;

    /**
     * Jackson 多态遗留字段；勿依赖。真实类型由 {@link #getType()} 固定为 {@code api}。
     */
    private String type;

    /**
     * 旧版入参：key → InputParamsUtil 表达式。
     * 新流程请使用 {@link #getInputs()}。
     */
    private Map<String, String> args;

    /** 可选：将返回值额外写入该变量名 */
    private String output;

    @Override
    public String getType() {
        return NodeType.API;
    }

    @Override
    public List<PortDefinition> getOutputPorts() {
        return Arrays.asList(PortDefinition.output(PortNames.OUT));
    }
}
