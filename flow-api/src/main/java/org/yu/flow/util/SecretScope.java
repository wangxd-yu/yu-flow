package org.yu.flow.util;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 一次执行（含嵌套调用的内部服务）解析到的敏感值集合，供落库前按值脱敏。
 *
 * <p>执行入口打开作用域后，同线程里新建的执行上下文都登记到同一个集合；并行分支复制上下文时共享该集合，
 * 因此入口在写执行日志时能拿到整条调用链用到的敏感值。嵌套打开复用外层集合，由最外层负责清理。</p>
 *
 * <pre>
 * try (SecretScope secrets = SecretScope.open()) {
 *     ... 执行 ...
 *     log.setErrorMsg(secrets.mask(errorMsg));
 * }
 * </pre>
 */
public final class SecretScope implements AutoCloseable {

    private static final ThreadLocal<Set<String>> CURRENT = new ThreadLocal<>();

    private final Set<String> values;
    private final boolean owner;

    private SecretScope(Set<String> values, boolean owner) {
        this.values = values;
        this.owner = owner;
    }

    public static SecretScope open() {
        Set<String> current = CURRENT.get();
        if (current != null) {
            return new SecretScope(current, false);
        }
        Set<String> created = ConcurrentHashMap.newKeySet();
        CURRENT.set(created);
        return new SecretScope(created, true);
    }

    /** 当前作用域的集合；没有打开作用域时返回一个独立的新集合 */
    public static Set<String> currentOrNew() {
        Set<String> current = CURRENT.get();
        return current != null ? current : ConcurrentHashMap.newKeySet();
    }

    public Set<String> values() {
        return values;
    }

    public String mask(String text) {
        return SecretMasker.mask(text, values);
    }

    @Override
    public void close() {
        if (owner) {
            CURRENT.remove();
        }
    }
}
