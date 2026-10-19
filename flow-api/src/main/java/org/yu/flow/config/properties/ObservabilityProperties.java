package org.yu.flow.config.properties;

/**
 * 可观测配置：Actuator 端点的访问控制。
 *
 * <p>对应 YAML 路径：{@code yu.flow.observability.*}</p>
 */
public class ObservabilityProperties {

    /**
     * 访问 health 以外 Actuator 端点（如 prometheus）所需的 Bearer 令牌（建议通过 YU_FLOW_METRICS_TOKEN 注入）。
     * <p>留空时这些端点一律返回 404；health 始终可匿名访问且只返回状态。</p>
     */
    private String metricsToken;

    public String getMetricsToken() {
        return metricsToken;
    }

    public void setMetricsToken(String metricsToken) {
        this.metricsToken = metricsToken;
    }
}
