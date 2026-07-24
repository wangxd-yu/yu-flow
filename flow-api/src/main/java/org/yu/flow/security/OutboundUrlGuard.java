package org.yu.flow.security;

import cn.hutool.core.util.StrUtil;

import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.Locale;
import java.util.Set;

/**
 * 出站 URL 校验：限制 scheme，拦截云元数据 / 链路本地地址，降低 SSRF 风险。
 * 私网/环回默认放行（流程常调内网 API）；可通过 {@code blockPrivate} 收紧。
 */
public final class OutboundUrlGuard {

    private static final Set<String> BLOCKED_HOSTS = Set.of(
            "metadata.google.internal",
            "metadata.google",
            "instance-data"
    );

    private OutboundUrlGuard() {
    }

    public static URI validate(String url, boolean allowHttp) {
        return validate(url, allowHttp, false);
    }

    /**
     * @param allowHttp    是否允许 http
     * @param blockPrivate 为 true 时拒绝 loopback / site-local（更严的 SSRF 策略）
     */
    public static URI validate(String url, boolean allowHttp, boolean blockPrivate) {
        if (StrUtil.isBlank(url)) {
            throw new IllegalArgumentException("URL 不能为空");
        }
        String raw = url.trim();
        String lowerRaw = raw.toLowerCase(Locale.ROOT);
        if (lowerRaw.startsWith("javascript:") || lowerRaw.startsWith("data:")
                || lowerRaw.startsWith("file:") || lowerRaw.startsWith("jar:")) {
            throw new IllegalArgumentException("禁止的 URL scheme");
        }
        URI uri;
        try {
            uri = URI.create(raw);
        } catch (Exception e) {
            throw new IllegalArgumentException("URL 非法: " + e.getMessage());
        }
        String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
        if ("https".equals(scheme)) {
            // ok
        } else if ("http".equals(scheme)) {
            if (!allowHttp) {
                throw new IllegalArgumentException("仅允许 https URL");
            }
        } else {
            throw new IllegalArgumentException("仅允许 http/https URL");
        }
        String host = uri.getHost();
        if (StrUtil.isBlank(host)) {
            throw new IllegalArgumentException("URL 缺少 host");
        }
        String hostLower = host.toLowerCase(Locale.ROOT);
        if (BLOCKED_HOSTS.contains(hostLower) || hostLower.endsWith(".metadata.google.internal")) {
            throw new IllegalArgumentException("禁止访问云元数据主机");
        }
        if ("169.254.169.254".equals(hostLower) || "metadata".equals(hostLower)) {
            throw new IllegalArgumentException("禁止访问链路本地元数据地址");
        }
        try {
            InetAddress[] addrs = InetAddress.getAllByName(host);
            for (InetAddress addr : addrs) {
                if (addr.isLinkLocalAddress() || addr.isMulticastAddress()) {
                    throw new IllegalArgumentException("禁止访问链路本地/组播地址: " + host);
                }
                if (blockPrivate && (addr.isAnyLocalAddress() || addr.isLoopbackAddress()
                        || addr.isSiteLocalAddress())) {
                    throw new IllegalArgumentException("禁止访问内网/环回地址: " + host);
                }
            }
        } catch (UnknownHostException e) {
            throw new IllegalArgumentException("无法解析主机: " + host);
        }
        return uri;
    }
}
