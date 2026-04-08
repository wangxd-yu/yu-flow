package org.yu.flow.engine.model.step;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.yu.flow.engine.model.PortDefinition;
import org.yu.flow.engine.model.Step;
import org.yu.flow.engine.model.PortNames;
import org.yu.flow.engine.model.NodeType;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * 系统方法调用节点 (System Method Step)
 *
 * <p>该节点允许用户从系统预设的工具方法库中选取一个方法并调用。
 * 其入参通过 {@code inputs} 字段配置的 JSONPath 从上游节点中提取，
 * 并以 SpEL 变量（{@code #varName}）的形式注入到 {@code expression} 中执行。
 * 执行结果存入上下文后，通过 {@code out} 端口传递给下游节点。</p>
 *
 * <h3>DSL 示例：</h3>
 * <pre>{@code
 * {
 *   "type": "systemMethod",
 *   "id": "formatDate_1",
 *   "methodCode": "DATE_FORMAT",
 *   "expression": "T(cn.hutool.core.date.DateUtil).format(#date, #format)",
 *   "inputs": {
 *     "date":   { "extractPath": "$.requestStep_1.createTime" },
 *     "format": { "extractPath": "$.requestStep_1.dateFormat" }
 *   }
 * }
 * }</pre>
 *
 * @author yu-flow
 * @date 2026-03-01
 */
@EqualsAndHashCode(callSuper = true)
@Data
public class SystemMethodStep extends Step {

    /**
     * 方法编码，用于在系统方法注册表中唯一标识一个预设方法。
     * 例如：{@code "DATE_FORMAT"}、{@code "MD5_ENCODE"}、{@code "LIST_FILTER"}。
     * 前端通过此编码展示方法名与参数说明；后端执行时以 expression 为准。
     */
    private String methodCode;

    // ──────────────────────────────────────────────────────────────────────────
    // 端口定义
    // ──────────────────────────────────────────────────────────────────────────

    @Override
    public String getType() {
        return NodeType.SYSTEM_METHOD;
    }

    /**
     * 输入端口：根据 {@code inputs} 字段中配置的变量 key 动态生成。
     *
     * <p>每个 inputs key 对应一个名为 {@code in:var:<key>} 的输入端口，
     * 与前端画布的 Port 命名约定保持一致（参见 adapter.ts 中的 in:var:* 规则）。
     * 若 inputs 未配置，则默认回退到基础输入端口 {@code in}。</p>
     */
    @Override
    @JsonIgnore
    public List<PortDefinition> getInputPorts() {
        Map<String, Object> inputs = getInputs();
        if (inputs == null || inputs.isEmpty()) {
            // 降级：提供一个默认的通用输入端口
            return Collections.singletonList(
                    PortDefinition.input("in", "any", false)
            );
        }

        List<PortDefinition> ports = new ArrayList<>(inputs.size());
        for (String key : inputs.keySet()) {
            // 命名约定：in:var:<key>，与前端画布 Port ID 规则对齐
            ports.add(PortDefinition.input("in:var:" + key, "any", false));
        }
        return ports;
    }

    /**
     * 输出端口：固定为 {@code out}，携带方法执行结果供下游节点通过 {@code $.nodeId.out} 提取。
     */
    @Override
    @JsonIgnore
    public List<PortDefinition> getOutputPorts() {
        return Collections.singletonList(PortDefinition.output(PortNames.OUT));
    }

    /**
     * 自定义验证：方法编码和表达式均不允许为空。
     */
    @Override
    protected List<String> validateCustom() {
        List<String> errors = new ArrayList<>();
        if (methodCode == null || methodCode.trim().isEmpty()) {
            errors.add("SystemMethodStep [" + getId() + "]: methodCode 不能为空");
        }
        return errors;
    }
}
