package org.yu.flow.engine.model.step;

import lombok.Data;
import lombok.EqualsAndHashCode;
import org.yu.flow.engine.model.NodeType;
import org.yu.flow.engine.model.PortDefinition;
import org.yu.flow.engine.model.PortNames;
import org.yu.flow.engine.model.Step;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Record 数据构造节点 (Object Builder)
 *
 * <p>将字面量与上游引用拼装成 Map，供 Evaluate / HttpRequest 等下游通过
 * {@code $.recordId.out.fieldName} 引用。
 *
 * <p>配置 {@code schema} + 可选 {@code inputs.payload}（总入口）:
 * <ul>
 *   <li>{@code "apiid": "498"} — 字符串字面量</li>
 *   <li>{@code "count": 3} / {@code "ok": true} / {@code "x": null} — 类型化字面量</li>
 *   <li>{@code "timestamp": "$.schedule.triggerTime"} — 绝对 JsonPath（逐字段连线）</li>
 *   <li>{@code "apiid": "apiid"} / {@code "$.name"} — 相对 {@code inputs.payload} 解析</li>
 *   <li>{@code "token": "${hmac.out}"} — 变量引用</li>
 * </ul>
 *
 * <p>输出存储在 {@code {nodeId}.out}（兼容 {@code {nodeId}.result}）。
 */
@EqualsAndHashCode(callSuper = true)
@Data
public class RecordStep extends Step {

    /**
     * 输出对象 schema：key → 字面量 / JsonPath / ${ref} / {extractPath}
     */
    private Map<String, Object> schema;

    @Override
    public String getType() {
        return NodeType.RECORD;
    }

    @Override
    public List<PortDefinition> getInputPorts() {
        // 与 Evaluate 一致：无独立控制流 in，由 in:var 字段连线驱动执行
        return Collections.emptyList();
    }

    @Override
    public List<PortDefinition> getOutputPorts() {
        return Arrays.asList(PortDefinition.output(PortNames.OUT));
    }
}
