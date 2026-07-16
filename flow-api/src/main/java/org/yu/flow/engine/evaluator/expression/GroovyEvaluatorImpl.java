package org.yu.flow.engine.evaluator.expression;

import cn.hutool.extra.spring.SpringUtil;
import groovy.lang.Binding;
import groovy.lang.GroovyClassLoader;
import groovy.lang.Script;
import org.codehaus.groovy.control.CompilerConfiguration;
import org.codehaus.groovy.control.customizers.SecureASTCustomizer;
import org.codehaus.groovy.runtime.InvokerHelper;
import org.yu.flow.config.YuFlowProperties;
import org.yu.flow.exception.FlowException;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.regex.Pattern;

/**
 * Groovy 动态脚本求值器。
 *
 * <p>已编译脚本按内容哈希缓存。每个不同脚本使用独立类加载器，缓存淘汰时关闭
 * 类加载器，使对应 Class 可在无引用后卸载。该实现是进程内防御式限制，不是
 * 面向不可信用户的强安全边界。</p>
 */
public class GroovyEvaluatorImpl implements ExpressionEvaluatorStrategy {

    private static final int DEFAULT_CACHE_SIZE = 256;
    private static final ConcurrentHashMap<String, CompiledScript> COMPILED_CACHE =
            new ConcurrentHashMap<>();
    private static final ConcurrentLinkedQueue<String> INSERTION_ORDER =
            new ConcurrentLinkedQueue<>();

    private static final Pattern DANGEROUS_SCRIPT = Pattern.compile(
            "(?is)(?:\\b(?:Runtime|ProcessBuilder|System|ClassLoader|GroovyClassLoader|"
                    + "Thread|File|Files|Paths|URL|URI|Socket|ServerSocket|Class)\\b\\s*(?:\\.|::|\\()"
                    + "|\\.\\s*(?:getClass|forName|getClassLoader|loadClass|newInstance|"
                    + "invoke|invokeMethod|execute|start|exit|halt)\\s*\\()");

    @Override
    public Object evaluate(String expression, Map<String, Object> context) {
        if (expression == null || expression.trim().isEmpty()) {
            return null;
        }
        validateScript(expression);

        String cacheKey = sha256(expression);
        try {
            CompiledScript compiled = COMPILED_CACHE.get(cacheKey);
            if (compiled == null) {
                CompiledScript candidate = compile(expression, cacheKey);
                CompiledScript existing = COMPILED_CACHE.putIfAbsent(cacheKey, candidate);
                if (existing == null) {
                    compiled = candidate;
                    INSERTION_ORDER.offer(cacheKey);
                    evictIfNecessary();
                } else {
                    candidate.close();
                    compiled = existing;
                }
            }

            Map<String, Object> variables = new LinkedHashMap<>();
            if (context != null) {
                variables.putAll(context);
            }
            variables.put("input", context == null
                    ? Collections.emptyMap()
                    : Collections.unmodifiableMap(new LinkedHashMap<>(context)));
            variables.put("spring", new RestrictedSpringAccess());

            Binding binding = new Binding(variables);
            Script script = InvokerHelper.createScript(compiled.scriptClass, binding);
            return script.run();
        } catch (FlowException e) {
            throw e;
        } catch (Exception e) {
            throw new FlowException("GROOVY_RUNTIME_ERROR",
                    "Groovy 脚本执行失败: " + e.getMessage()
                            + " | 脚本: " + truncate(expression, 200), e);
        }
    }

    private CompiledScript compile(String expression, String cacheKey) {
        CompilerConfiguration configuration = new CompilerConfiguration();
        SecureASTCustomizer secure = new SecureASTCustomizer();
        secure.setPackageAllowed(false);
        secure.setMethodDefinitionAllowed(false);
        secure.setClosuresAllowed(true);
        // 间接导入检查会把 Groovy 生成的 java.lang.Object/Closure 也视为未授权导入，
        // 导致普通集合脚本无法编译；显式导入仍由白名单限制，危险入口另做源码校验。
        secure.setIndirectImportCheckEnabled(false);
        secure.setImportsWhitelist(Arrays.asList(
                "java.security.MessageDigest",
                "java.nio.charset.StandardCharsets",
                "java.util.Base64",
                "javax.crypto.Mac",
                "javax.crypto.spec.SecretKeySpec"
        ));
        secure.setStarImportsWhitelist(Collections.emptyList());
        secure.setStaticImportsWhitelist(Collections.emptyList());
        secure.setStaticStarImportsWhitelist(Collections.emptyList());
        configuration.addCompilationCustomizers(secure);

        GroovyClassLoader loader = new GroovyClassLoader(
                GroovyEvaluatorImpl.class.getClassLoader(), configuration, false);
        try {
            Class<?> parsed = loader.parseClass(expression,
                    "YuFlowScript_" + cacheKey.substring(0, 16) + ".groovy");
            if (!Script.class.isAssignableFrom(parsed)) {
                throw new FlowException("GROOVY_COMPILE_ERROR", "Groovy 脚本未编译为可执行 Script");
            }
            @SuppressWarnings("unchecked")
            Class<? extends Script> scriptClass = (Class<? extends Script>) parsed;
            return new CompiledScript(scriptClass, loader);
        } catch (FlowException e) {
            closeLoader(loader);
            throw e;
        } catch (Exception e) {
            closeLoader(loader);
            throw new FlowException("GROOVY_COMPILE_ERROR",
                    "Groovy 脚本编译失败: " + e.getMessage()
                            + " | 脚本: " + truncate(expression, 200), e);
        }
    }

    private void validateScript(String expression) {
        if (DANGEROUS_SCRIPT.matcher(expression).find()) {
            throw new FlowException("GROOVY_SECURITY_ERROR",
                    "Groovy 脚本包含被禁止的 JVM、反射、进程、线程或 I/O 操作");
        }
    }

    private void evictIfNecessary() {
        int maximumSize = resolveCacheSize();
        while (COMPILED_CACHE.size() > maximumSize) {
            String oldestKey = INSERTION_ORDER.poll();
            if (oldestKey == null) {
                return;
            }
            CompiledScript removed = COMPILED_CACHE.remove(oldestKey);
            if (removed != null) {
                removed.close();
            }
        }
    }

    private int resolveCacheSize() {
        try {
            YuFlowProperties properties = SpringUtil.getBean(YuFlowProperties.class);
            if (properties != null && properties.getEngine() != null) {
                return Math.max(16, properties.getEngine().getGroovyScriptCacheSize());
            }
        } catch (Exception ignored) {
            // 非 Spring 场景使用安全默认值。
        }
        return DEFAULT_CACHE_SIZE;
    }

    private static String sha256(String script) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(script.getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder(digest.length * 2);
            for (byte value : digest) {
                result.append(String.format("%02x", value & 0xff));
            }
            return result.toString();
        } catch (Exception e) {
            throw new FlowException("GROOVY_CACHE_ERROR", "无法计算 Groovy 脚本缓存键", e);
        }
    }

    private static String truncate(String text, int maxLength) {
        return text.length() <= maxLength ? text : text.substring(0, maxLength) + "...";
    }

    private static void closeLoader(GroovyClassLoader loader) {
        loader.clearCache();
        try {
            loader.close();
        } catch (IOException ignored) {
            // URLClassLoader.close 的清理失败不覆盖原始脚本错误。
        }
    }

    static int compiledCacheSize() {
        return COMPILED_CACHE.size();
    }

    static void clearCompiledCache() {
        for (Map.Entry<String, CompiledScript> entry : COMPILED_CACHE.entrySet()) {
            if (COMPILED_CACHE.remove(entry.getKey(), entry.getValue())) {
                entry.getValue().close();
            }
        }
        INSERTION_ORDER.clear();
    }

    /**
     * 脚本内名为 spring 的受限访问门面。
     */
    public static final class RestrictedSpringAccess {
        public Object getBean(String beanName) {
            if (beanName == null || beanName.trim().isEmpty()) {
                throw new FlowException("GROOVY_BEAN_ACCESS_DENIED", "Spring Bean 名称不能为空");
            }
            List<String> allowedBeans = Collections.emptyList();
            try {
                YuFlowProperties properties = SpringUtil.getBean(YuFlowProperties.class);
                if (properties != null
                        && properties.getSecurity() != null
                        && properties.getSecurity().getScriptAllowedBeans() != null) {
                    allowedBeans = properties.getSecurity().getScriptAllowedBeans();
                }
            } catch (Exception e) {
                // 非 Spring 场景按空白名单处理。
            }
            if (!allowedBeans.contains(beanName)) {
                throw new FlowException("GROOVY_BEAN_ACCESS_DENIED",
                        "Groovy 脚本无权访问 Spring Bean: " + beanName);
            }
            try {
                return SpringUtil.getBean(beanName);
            } catch (Exception e) {
                throw new FlowException("GROOVY_BEAN_ACCESS_ERROR",
                        "获取 Spring Bean 失败: " + beanName + ", 错误: " + e.getMessage(), e);
            }
        }
    }

    private static final class CompiledScript {
        private final Class<? extends Script> scriptClass;
        private final GroovyClassLoader classLoader;

        private CompiledScript(Class<? extends Script> scriptClass, GroovyClassLoader classLoader) {
            this.scriptClass = scriptClass;
            this.classLoader = classLoader;
        }

        private void close() {
            closeLoader(classLoader);
        }
    }
}
