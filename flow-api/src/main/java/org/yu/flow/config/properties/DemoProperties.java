package org.yu.flow.config.properties;

/**
 * 演示模式安全限制配置。
 *
 * <p>对应 YAML 路径：{@code yu.flow.demo.*}</p>
 * <pre>
 * yu:
 *   flow:
 *     demo-mode: true
 *     demo:
 *       max-steps: 200
 *       max-for-loop-items: 50
 * </pre>
 */
public class DemoProperties {

    /**
     * 单次流程执行允许的最大步骤数。
     * <p>用于防止死循环和过于复杂的流程编排。
     * 执行步骤数超过此值时，引擎将强制终止流程并报错。</p>
     */
    private int maxSteps = 200;

    /**
     * For / ForEach 循环节点允许的最大输入数组元素数。
     * <p>防止传入巨大数组导致线程池耗尽或内存溢出。</p>
     */
    private int maxForLoopItems = 50;

    /**
     * Delay 节点允许的最大等待毫秒数（演示模式）。
     */
    private long maxDelayMs = 5000L;

    public int getMaxSteps() {
        return maxSteps;
    }

    public void setMaxSteps(int maxSteps) {
        this.maxSteps = maxSteps;
    }

    public int getMaxForLoopItems() {
        return maxForLoopItems;
    }

    public void setMaxForLoopItems(int maxForLoopItems) {
        this.maxForLoopItems = maxForLoopItems;
    }

    public long getMaxDelayMs() {
        return maxDelayMs;
    }

    public void setMaxDelayMs(long maxDelayMs) {
        this.maxDelayMs = maxDelayMs;
    }
}
