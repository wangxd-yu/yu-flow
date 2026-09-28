package org.yu.flow.module.envvar.support;

import cn.hutool.extra.spring.SpringUtil;
import org.yu.flow.engine.evaluator.ExecutionContext;
import org.yu.flow.exception.FlowException;
import org.yu.flow.module.envvar.cache.EnvVariableCacheManager;

import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 环境变量引用语法与执行期取值。
 *
 * <ul>
 *   <li>节点输入参数：{@code $.env.CODE}（与 {@code $.节点ID.字段} 同一套 JSONPath 写法）</li>
 *   <li>httpRequest 的 URL / Header / Query / Body / 认证字段：{@code ${env.CODE}}</li>
 * </ul>
 */
public final class EnvVarRefs {

    public static final String ENV_ROOT = "env";

    private static final Pattern CODE = Pattern.compile("^[A-Z][A-Z0-9_]{0,63}$");
    private static final Pattern INPUT_PATH = Pattern.compile("^\\$\\.env\\.([A-Za-z_][A-Za-z0-9_]*)$");
    private static final Pattern TEMPLATE_REF = Pattern.compile("\\$\\{\\s*env\\.([A-Za-z_][A-Za-z0-9_]*)\\s*}");
    private static final Pattern PATH_REF = Pattern.compile("\\$\\.env\\.([A-Za-z_][A-Za-z0-9_]*)");

    private static volatile EnvVariableCacheManager cacheManager;

    private EnvVarRefs() {
    }

    public static boolean isValidCode(String code) {
        return code != null && CODE.matcher(code).matches();
    }

    /** 输入参数路径是否为 {@code $.env.CODE}；是则返回 CODE，否则 null */
    public static String inputPathCode(String extractPath) {
        if (extractPath == null) {
            return null;
        }
        Matcher m = INPUT_PATH.matcher(extractPath);
        return m.matches() ? m.group(1) : null;
    }

    /** 扫描任意文本（DSL / SQL）中引用到的变量名，用于发布包依赖检查 */
    public static Set<String> scan(String text) {
        Set<String> codes = new LinkedHashSet<>();
        if (text == null || text.isEmpty()) {
            return codes;
        }
        collect(TEMPLATE_REF.matcher(text), codes);
        collect(PATH_REF.matcher(text), codes);
        return codes;
    }

    /**
     * 把模板字段里的 {@code ${env.CODE}} 解析后以 {@code env.CODE} 为键放进节点输入，
     * 让既有的 {@code ${var}} 替换逻辑直接命中；节点自己定义了同名输入时以节点为准。
     */
    public static void injectTemplateRefs(Map<String, Object> inputs, ExecutionContext context, String... templates) {
        for (String template : templates) {
            if (template == null || !template.contains("env.")) {
                continue;
            }
            Matcher m = TEMPLATE_REF.matcher(template);
            while (m.find()) {
                String code = m.group(1);
                String key = ENV_ROOT + "." + code;
                if (!inputs.containsKey(key)) {
                    inputs.put(key, resolve(code, context));
                }
            }
        }
    }

    /**
     * 执行期取值；敏感变量登记到上下文，落库前脱敏。
     * <p>未配置时直接报错：生产缺变量时静默用空串会把请求发到错误地址。</p>
     */
    public static String resolve(String code, ExecutionContext context) {
        EnvVariableCacheManager manager = cacheManager();
        EnvVariableCacheManager.CachedEnvVariable cached = manager == null ? null : manager.get(code).orElse(null);
        if (cached == null) {
            throw new FlowException("ENV_VAR_NOT_FOUND", "环境变量未配置: " + code + "（平台设置 · 环境变量）");
        }
        if (cached.secret() && context != null) {
            context.registerSecret(cached.value());
        }
        return cached.value();
    }

    private static void collect(Matcher m, Set<String> codes) {
        while (m.find()) {
            codes.add(m.group(1));
        }
    }

    private static EnvVariableCacheManager cacheManager() {
        if (cacheManager == null) {
            try {
                cacheManager = SpringUtil.getBean(EnvVariableCacheManager.class);
            } catch (Exception ignored) {
                // 单测等无 Spring 容器场景
            }
        }
        return cacheManager;
    }
}
