package org.yu.flow.engine.evaluator.spel;

import org.springframework.expression.EvaluationException;
import org.springframework.expression.TypeLocator;
import org.springframework.expression.spel.SpelEvaluationException;
import org.springframework.expression.spel.SpelMessage;
import org.springframework.expression.spel.support.StandardTypeLocator;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

/**
 * SpEL {@link TypeLocator} 白名单：仅允许安全工具类（精选 Hutool / java.time），
 * 拒绝 {@code Runtime}/{@code ProcessBuilder}/{@code ClassLoader} 及 Hutool 网络/文件类。
 */
public final class SafeTypeLocator implements TypeLocator {

    /** 包前缀或精确 FQCN（无尾点表示精确匹配） */
    private static final Set<String> PACKAGE_PREFIXES = new LinkedHashSet<>(Arrays.asList(
            "java.time.",
            "java.math.",
            "java.text.",
            "java.util.UUID",
            "java.util.Optional",
            "java.util.Collections",
            "java.util.Objects",
            "java.lang.Math",
            "java.lang.String",
            "java.lang.Integer",
            "java.lang.Long",
            "java.lang.Double",
            "java.lang.Float",
            "java.lang.Boolean",
            "java.lang.Character",
            "java.lang.Byte",
            "java.lang.Short",
            "java.lang.Number",
            "org.yu.flow.engine.evaluator.expression.",
            "org.yu.flow.auto.util.SnowIdGenerator",
            "org.yu.flow.auto.util.FlowSystemParamsUtil"
    ));

    /**
     * Hutool 仅放行纯计算/编解码工具；禁止 FileUtil / HttpUtil / RuntimeUtil 等旁路出站与本地 IO。
     */
    private static final Set<String> HUTOOL_ALLOW = new LinkedHashSet<>(Arrays.asList(
            "cn.hutool.core.util.StrUtil",
            "cn.hutool.core.util.IdUtil",
            "cn.hutool.core.util.NumberUtil",
            "cn.hutool.core.util.ObjectUtil",
            "cn.hutool.core.util.BooleanUtil",
            "cn.hutool.core.util.ArrayUtil",
            "cn.hutool.core.util.HexUtil",
            "cn.hutool.core.util.RandomUtil",
            "cn.hutool.core.util.CharUtil",
            "cn.hutool.core.date.DateUtil",
            "cn.hutool.core.date.LocalDateTimeUtil",
            "cn.hutool.core.codec.Base64",
            "cn.hutool.core.codec.Base32",
            "cn.hutool.crypto.digest.DigestUtil",
            "cn.hutool.json.JSONUtil"
    ));

    private static final Set<String> DENY_SIMPLE = new LinkedHashSet<>(Arrays.asList(
            "Runtime", "ProcessBuilder", "System", "Class", "ClassLoader",
            "Thread", "ThreadGroup", "Method", "Field", "Constructor",
            "Unsafe", "ScriptEngine", "ScriptEngineManager", "MethodHandle",
            "Lookup", "AccessibleObject", "Proxy", "Compiler",
            "FileUtil", "HttpUtil", "HttpRequest", "HttpResponse", "RuntimeUtil",
            "ClassUtil", "ReflectUtil", "JarClassLoader", "URLUtil", "IoUtil",
            "FileReader", "FileWriter", "ZipUtil", "ProcessUtil"
    ));

    private final TypeLocator delegate = new StandardTypeLocator();

    @Override
    public Class<?> findType(String typeName) throws EvaluationException {
        if (typeName == null || typeName.isBlank()) {
            throw new SpelEvaluationException(SpelMessage.TYPE_NOT_FOUND, typeName);
        }
        String name = typeName.trim();
        String simple = name.contains(".") ? name.substring(name.lastIndexOf('.') + 1) : name;
        if (DENY_SIMPLE.contains(simple)) {
            throw new SpelEvaluationException(SpelMessage.TYPE_NOT_FOUND, typeName);
        }
        String lower = name.toLowerCase(Locale.ROOT);
        if (lower.contains("runtime") || lower.contains("processbuilder")
                || lower.contains("classloader") || lower.contains("reflect")
                || lower.contains("unsafe") || lower.startsWith("javax.script")
                || lower.startsWith("jdk.internal") || lower.startsWith("sun.")) {
            throw new SpelEvaluationException(SpelMessage.TYPE_NOT_FOUND, typeName);
        }
        if (HUTOOL_ALLOW.contains(name)) {
            return delegate.findType(name);
        }
        // 拒绝其余 cn.hutool.*（含短名 DateUtil 若解析到非白名单包）
        if (name.startsWith("cn.hutool.")) {
            throw new SpelEvaluationException(SpelMessage.TYPE_NOT_FOUND, typeName);
        }
        boolean allowed = false;
        for (String prefix : PACKAGE_PREFIXES) {
            if (prefix.endsWith(".")) {
                if (name.startsWith(prefix)) {
                    allowed = true;
                    break;
                }
            } else if (name.equals(prefix)) {
                allowed = true;
                break;
            }
        }
        if (!allowed) {
            throw new SpelEvaluationException(SpelMessage.TYPE_NOT_FOUND, typeName);
        }
        return delegate.findType(name);
    }
}
