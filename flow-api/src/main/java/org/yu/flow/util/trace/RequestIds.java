package org.yu.flow.util.trace;

import org.slf4j.MDC;

import java.util.UUID;
import java.util.regex.Pattern;

/**
 * 请求 ID：HTTP 请求沿用调用方的 {@code X-Request-Id}（格式合法时），否则生成一个；
 * 定时任务、MQ 消费等没有 HTTP 请求的入口用 {@link #open()} 自己生成。
 *
 * <p>放在 MDC 的 {@value #MDC_KEY} 下，日志行与执行 / 三方 / 任务日志表都按它对线。</p>
 */
public final class RequestIds {

    public static final String HEADER = "X-Request-Id";
    public static final String MDC_KEY = "requestId";

    /** 外部传入的 ID 只接受常见字符，避免日志注入和超长值 */
    private static final Pattern ACCEPTED = Pattern.compile("[A-Za-z0-9._:-]{1,64}");

    private RequestIds() {
    }

    public static String current() {
        return MDC.get(MDC_KEY);
    }

    public static String newId() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    /** 合法则原样返回，否则返回 null */
    public static String accept(String inbound) {
        if (inbound == null) {
            return null;
        }
        String trimmed = inbound.trim();
        return ACCEPTED.matcher(trimmed).matches() ? trimmed : null;
    }

    /**
     * 当前线程没有请求 ID 时生成一个，关闭时还原；已有则什么都不做。
     */
    public static Scope open() {
        String existing = current();
        if (existing != null) {
            return new Scope(existing, false);
        }
        String id = newId();
        MDC.put(MDC_KEY, id);
        return new Scope(id, true);
    }

    public static final class Scope implements AutoCloseable {

        private final String id;
        private final boolean owner;

        private Scope(String id, boolean owner) {
            this.id = id;
            this.owner = owner;
        }

        public String id() {
            return id;
        }

        @Override
        public void close() {
            if (owner) {
                MDC.remove(MDC_KEY);
            }
        }
    }
}
