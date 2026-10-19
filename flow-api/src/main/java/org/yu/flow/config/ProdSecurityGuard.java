package org.yu.flow.config;

import cn.hutool.core.convert.Convert;
import cn.hutool.core.util.StrUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import org.yu.flow.exception.FlowException;
import org.yu.flow.module.sysconfig.support.YuFlowRuntimeSettings;
import org.yu.flow.module.sysconfig.support.YuFlowRuntimeSettings.Keys;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 本实例环境为 PROD（{@code yu.flow.release.current-env}）时检查安全档位。
 *
 * <p>阻断项：让匿名访问、伪造或重放开放平台请求、跳过证书校验成为可能的开关。启动时发现即拒绝启动
 * （在 Web 端口开始监听之前），运行期也不允许通过系统配置或发布包把它们改回去。
 * {@code YU_FLOW_ALLOW_INSECURE_PROD=true} 可放行，只打 ERROR 日志。</p>
 *
 * <p>告警项：可用性与合规上的取舍（限流在 Redis 故障时放行、Groovy / Python 脚本、MQ 报文全文落库、
 * 没配发布包签名密钥、没开资产编辑锁），只在启动时打 WARN。</p>
 */
@Slf4j
@Component
public class ProdSecurityGuard implements SmartInitializingSingleton {

    public static final String ERROR_CODE = "PROD_INSECURE_CONFIG";

    public record Finding(boolean blocking, String problem, String fix) {
        @Override
        public String toString() {
            return problem + "（" + fix + "）";
        }
    }

    private final YuFlowProperties properties;
    private final YuFlowRuntimeSettings runtimeSettings;
    private final Environment environment;

    public ProdSecurityGuard(YuFlowProperties properties, YuFlowRuntimeSettings runtimeSettings,
                             Environment environment) {
        this.properties = properties;
        this.runtimeSettings = runtimeSettings;
        this.environment = environment;
    }

    public boolean isProd() {
        String env = properties.getRelease() == null ? null : properties.getRelease().getCurrentEnv();
        return env != null && "PROD".equals(env.trim().toUpperCase(Locale.ROOT));
    }

    private boolean allowInsecure() {
        return properties.getSecurity() != null && properties.getSecurity().isAllowInsecureProd();
    }

    @Override
    public void afterSingletonsInstantiated() {
        if (!isProd()) {
            return;
        }
        List<Finding> findings = findings();
        List<Finding> blocking = new ArrayList<>();
        for (Finding f : findings) {
            if (f.blocking()) {
                blocking.add(f);
            } else {
                log.warn("[ProdSecurityGuard] 生产实例建议调整：{}", f);
            }
        }
        if (blocking.isEmpty()) {
            log.info("[ProdSecurityGuard] 生产实例安全档位检查通过");
            return;
        }
        if (allowInsecure()) {
            blocking.forEach(f -> log.error("[ProdSecurityGuard] 生产实例存在不安全档位，已由 YU_FLOW_ALLOW_INSECURE_PROD 放行：{}", f));
            return;
        }
        StringBuilder msg = new StringBuilder("本实例环境为 PROD，以下档位不安全，拒绝启动：");
        for (int i = 0; i < blocking.size(); i++) {
            msg.append("\n  ").append(i + 1).append(". ").append(blocking.get(i));
        }
        msg.append("\n确需临时放行可设 YU_FLOW_ALLOW_INSECURE_PROD=true");
        throw new IllegalStateException(msg.toString());
    }

    /** 当前生效值下的全部问题（系统配置覆盖 yml 的项按系统配置算） */
    public List<Finding> findings() {
        List<Finding> out = new ArrayList<>();
        YuFlowProperties.Security sec = properties.getSecurity();
        if (sec != null) {
            if (!sec.isFailOnInsecureDefaults()) {
                out.add(new Finding(true, "关闭了弱默认密钥 / 口令启动拦截", "设 YU_FLOW_FAIL_ON_INSECURE_DEFAULTS=true"));
            }
            if (sec.isAllowYmlAdminFallback()) {
                out.add(new Finding(true, "允许 yml 管理员账号兜底登录", "设 YU_FLOW_ALLOW_YML_ADMIN_FALLBACK=false"));
            }
            if (sec.isAllowIngressAuthNone()) {
                out.add(new Finding(true, "允许接口免鉴权（authMode=NONE）", "设 YU_FLOW_ALLOW_INGRESS_AUTH_NONE=false"));
            }
            if (sec.isAllowIgnoreSsl()) {
                out.add(new Finding(true, "允许 HTTP 节点跳过证书校验（ignoreSsl）", "设 YU_FLOW_ALLOW_IGNORE_SSL=false，自签名服务请配置受信 CA"));
            }
        }
        if (environment.getProperty("yu.flow.host-demo.enabled", Boolean.class, false)) {
            out.add(new Finding(true, "开启了宿主演示接口 /yu-demo/**", "设 YU_FLOW_HOST_DEMO_ENABLED=false"));
        }
        if (properties.getPrivacy() != null && properties.getPrivacy().isDevSessionEnabled()) {
            out.add(new Finding(true, "开启了隐私联调会话接口", "设 YU_FLOW_PRIVACY_DEV_SESSION=false"));
        }
        if (runtimeSettings.isOpenAllowPlainSecret()) {
            out.add(new Finding(true, "开放平台接受明文 Secret", "系统配置 OPEN_ALLOW_PLAIN_SECRET 或 YU_FLOW_OPEN_ALLOW_PLAIN_SECRET 设为 false"));
        }
        if (!runtimeSettings.isOpenNonceFailClosed()) {
            out.add(new Finding(true, "开放平台 nonce 校验在 Redis 故障时放行（可被重放）", "系统配置 OPEN_NONCE_FAIL_CLOSED 或 YU_FLOW_OPEN_NONCE_FAIL_CLOSED 设为 true"));
        }
        if (runtimeSettings.isOpenAllowDirectPath()) {
            out.add(new Finding(true, "开放平台允许绕过统一入口直接调用接口路径", "系统配置 OPEN_ALLOW_DIRECT_PATH 或 YU_FLOW_OPEN_ALLOW_DIRECT_PATH 设为 false"));
        }

        if (runtimeSettings.isIngressRateLimitFailOpen()) {
            out.add(new Finding(false, "限流在 Redis 故障时放行", "可设 YU_FLOW_INGRESS_RATE_LIMIT_FAIL_OPEN=false，或在接口上单独设置故障时拒绝"));
        }
        List<String> languages = runtimeSettings.getScriptAllowedLanguages();
        if (languages != null) {
            for (String lang : languages) {
                String l = StrUtil.trimToEmpty(lang).toLowerCase(Locale.ROOT);
                if ("groovy".equals(l) || "python".equals(l)) {
                    out.add(new Finding(false, "脚本白名单包含 " + l + "（沙箱逃逸面较大）", "不用时从 YU_FLOW_SCRIPT_ALLOWED_LANGUAGES 移除"));
                }
            }
        }
        if ("FULL".equalsIgnoreCase(runtimeSettings.getMqLogPayloadMode())) {
            out.add(new Finding(false, "MQ 原始报文全文落库", "含个人信息时改为 MASK 或 OFF（YU_FLOW_MQ_LOG_PAYLOAD_MODE）"));
        }
        YuFlowProperties.Release release = properties.getRelease();
        if (release != null && StrUtil.isBlank(release.getSigningKey())) {
            out.add(new Finding(false, "没有配置发布包签名密钥，生产导入要求验签，导入会失败", "设 YU_FLOW_RELEASE_SIGNING_KEY（与开发环境相同）"));
        }
        if (release != null && !release.isLockAssetEditing()) {
            out.add(new Finding(false, "没有锁定资产编辑，生产上仍可直接改编排", "设 YU_FLOW_LOCK_ASSET_EDITING=true"));
        }
        return out;
    }

    /**
     * 系统配置写入前的检查：PROD 下不允许把阻断项对应的键改成不安全值。
     *
     * @return 拒绝原因；允许写入时返回 null
     */
    public String sysConfigRejectReason(String configKey, String configValue, Integer status) {
        if (!isProd() || allowInsecure() || configKey == null) {
            return null;
        }
        if (status != null && status == 0) {
            return null;
        }
        Boolean value = StrUtil.isBlank(configValue) ? null : Convert.toBool(configValue.trim(), null);
        if (value == null) {
            return null;
        }
        return switch (configKey.trim()) {
            case Keys.OPEN_ALLOW_PLAIN_SECRET -> value ? "生产实例不允许开放平台接受明文 Secret" : null;
            case Keys.OPEN_NONCE_FAIL_CLOSED -> value ? null : "生产实例不允许 nonce 校验在 Redis 故障时放行";
            case Keys.OPEN_ALLOW_DIRECT_PATH -> value ? "生产实例不允许开放平台绕过统一入口" : null;
            default -> null;
        };
    }

    /** 同 {@link #sysConfigRejectReason}，不允许时抛出 */
    public void assertSysConfigAllowed(String configKey, String configValue, Integer status) {
        String reason = sysConfigRejectReason(configKey, configValue, status);
        if (reason != null) {
            throw new FlowException(ERROR_CODE, reason + "（" + configKey + "）");
        }
    }
}
