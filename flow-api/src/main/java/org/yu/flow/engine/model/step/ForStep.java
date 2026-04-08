package org.yu.flow.engine.model.step;

import lombok.Data;
import lombok.EqualsAndHashCode;
import org.yu.flow.engine.model.PortDefinition;
import org.yu.flow.engine.model.Step;
import org.yu.flow.engine.model.PortNames;
import org.yu.flow.engine.model.NodeType;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * ForStep - 并发分散节点 (Scatter-Gather 模式之 Scatter 端)
 *
 * <h2>核心设计：发射即忘 (Fire-and-Forget)</h2>
 * <p>ForStep 的角色是一个"发射台"：</p>
 * <ol>
 *   <li>将输入数组拆分为 N 个元素</li>
 *   <li>为每个元素 submit 一个异步 Callable 到线程池</li>
 *   <li>每条子任务携带独立的上下文副本，从 {@code item} 端口出发执行循环体</li>
 *   <li>主线程 <b>立刻 return null</b>，让引擎的 while 循环终止 —— 主线程生命结束</li>
 * </ol>
 *
 * <h2>端口设计（精简）</h2>
 * <ul>
 *   <li><b>输入端口 list</b>：接收目标数组（data flow）</li>
 *   <li><b>输入端口 start</b>：可选控制流触发信号</li>
 *   <li><b>输出端口 item</b>：每条并发分支从此端口出发，进入循环体</li>
 * </ul>
 * <p>
 * ⚠️ 注意：<b>不再有 done 端口</b>。图拓扑上，每条分支线路直接在 CollectStep 的
 * {@code item} 入口处汇聚，CollectStep 才是真正的屏障节点。
 * </p>
 *
 * <h2>空数组旁路（防死锁）</h2>
 * <p>当输入为 null 或 [] 时，ForStep 通过 {@code collectStepId} 字段找到配对的 CollectStep，
 * 直接在其并发屏障中注入 totalCount=0 的完成信号，CollectStep 立即以空 List 触发下游。</p>
 *
 * <h2>上下文注入（每条分支）</h2>
 * <pre>
 *   context["{forStepId}"] = {
 *     "item":       当前元素值,
 *     "index":      当前索引 (0-based),
 *     "totalCount": 总元素数
 *   }
 * </pre>
 */
@EqualsAndHashCode(callSuper = true)
@Data
public class ForStep extends Step {

    /**
     * 配置：与此 ForStep 配对的 CollectStep 节点 ID。
     * <p><b>必须配置</b>：用于空数组时直接旁路通知 CollectStep，防止无限等待（死锁）。</p>
     */
    private String collectStepId;

    /**
     * 分支执行超时（毫秒），由 CollectStep 侧读取使用（默认 30 秒）。
     */
    private long timeoutMs = 30_000L;

    @Override
    public String getType() {
        return NodeType.FOR;
    }

    @Override
    public List<PortDefinition> getInputPorts() {
        return Arrays.asList(
                PortDefinition.input("list", "array", false),
                PortDefinition.input("start", "any", false)
        );
    }

    @Override
    public List<PortDefinition> getOutputPorts() {
        // 只有 item 端口——每条并发分支从此出发，在图拓扑上物理汇聚到 CollectStep
        return Collections.singletonList(PortDefinition.output(PortNames.ITEM));
    }
}
