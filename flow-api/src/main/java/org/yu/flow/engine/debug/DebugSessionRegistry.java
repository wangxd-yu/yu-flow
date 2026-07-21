package org.yu.flow.engine.debug;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.util.*;
import java.util.concurrent.*;

/**
 * 调试会话注册表（Spring 单例）。
 *
 * <p>管理所有活跃的 {@link DebugSession} 实例，提供会话的创建、查找、销毁接口，
 * 并通过后台定时清理线程自动回收超时/已完成的会话，防止内存泄漏。</p>
 *
 * <h3>容量安全</h3>
 * <ul>
 *   <li>同时活跃的调试会话数量上限为 {@link #MAX_SESSIONS}（默认 20）。
 *       超出限制时创建请求将被拒绝并抛出异常。</li>
 *   <li>后台每 60 秒扫描一次，清除已过期/已完成且超过 2 分钟的会话。</li>
 *   <li><b>分布式：</b>会话仅存本机内存。多节点部署时调试轮询须落到创建会话的同一实例
 *       （网关会话粘性 / 单实例调试），否则会 404。</li>
 * </ul>
 *
 * @author yu-flow
 * @since 1.0
 */
@Slf4j
@Component
public class DebugSessionRegistry {

    /** 同时活跃的最大调试会话数 */
    private static final int MAX_SESSIONS = 20;

    /** 已完成会话的保留时间（毫秒），超过后自动清理 */
    private static final long COMPLETED_SESSION_RETENTION_MS = 2 * 60 * 1000L;

    /** 活跃会话映射表：sessionId → DebugSession */
    private final ConcurrentHashMap<String, DebugSession> sessions = new ConcurrentHashMap<>();

    /** 后台清理调度器 */
    private ScheduledExecutorService cleanupScheduler;

    @PostConstruct
    public void init() {
        cleanupScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "debug-session-cleanup");
            t.setDaemon(true);
            return t;
        });
        cleanupScheduler.scheduleAtFixedRate(this::cleanupExpiredSessions, 60, 60, TimeUnit.SECONDS);
        log.info("[DebugSessionRegistry] 调试会话注册表已初始化，最大并发会话数={}", MAX_SESSIONS);
    }

    @PreDestroy
    public void destroy() {
        // 取消所有活跃会话
        sessions.values().forEach(DebugSession::cancel);
        sessions.clear();
        if (cleanupScheduler != null && !cleanupScheduler.isShutdown()) {
            cleanupScheduler.shutdown();
        }
        log.info("[DebugSessionRegistry] 调试会话注册表已销毁");
    }

    /**
     * 创建新的调试会话。
     *
     * @param breakpoints  断点节点 ID 集合
     * @return 新创建的 DebugSession
     * @throws IllegalStateException 如果活跃会话数超出限制
     */
    public DebugSession createSession(Set<String> breakpoints) {
        if (sessions.size() >= MAX_SESSIONS) {
            throw new IllegalStateException(
                    "调试会话数已达上限（" + MAX_SESSIONS + "），请先关闭已有的调试会话。");
        }
        String sessionId = UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        DebugSession session = new DebugSession(sessionId, breakpoints);
        sessions.put(sessionId, session);
        log.info("[DebugSessionRegistry] 创建调试会话: sessionId={}, 断点数={}, 当前活跃会话数={}",
                sessionId, breakpoints != null ? breakpoints.size() : 0, sessions.size());
        return session;
    }

    /**
     * 根据 sessionId 获取会话。
     *
     * @param sessionId 会话 ID
     * @return 对应的 DebugSession，不存在时返回 null
     */
    public DebugSession getSession(String sessionId) {
        return sessions.get(sessionId);
    }

    /**
     * 移除并销毁指定会话。
     *
     * @param sessionId 会话 ID
     */
    public void removeSession(String sessionId) {
        DebugSession session = sessions.remove(sessionId);
        if (session != null) {
            session.cancel();
            log.info("[DebugSessionRegistry] 移除调试会话: sessionId={}", sessionId);
        }
    }

    /**
     * 获取所有活跃会话的摘要信息（用于管理 API 或监控面板）。
     */
    public List<Map<String, Object>> listSessions() {
        List<Map<String, Object>> result = new ArrayList<>();
        for (DebugSession session : sessions.values()) {
            Map<String, Object> info = new LinkedHashMap<>();
            info.put("sessionId", session.getSessionId());
            info.put("status", session.getStatus().name());
            info.put("suspendedNodeId", session.getSuspendedNodeId());
            info.put("suspendedNodeName", session.getSuspendedNodeName());
            info.put("createTime", session.getCreateTime());
            info.put("breakpointCount", session.getBreakpoints().size());
            result.add(info);
        }
        return result;
    }

    /**
     * 获取当前活跃会话数量。
     */
    public int getActiveSessionCount() {
        return sessions.size();
    }

    /**
     * 定时清理过期/已完成的会话。
     */
    private void cleanupExpiredSessions() {
        try {
            int removedCount = 0;
            Iterator<Map.Entry<String, DebugSession>> it = sessions.entrySet().iterator();
            while (it.hasNext()) {
                Map.Entry<String, DebugSession> entry = it.next();
                DebugSession session = entry.getValue();

                boolean shouldRemove = false;

                // 1. 会话已过期（超过 2 倍超时时间）
                if (session.isExpired()) {
                    session.cancel();
                    shouldRemove = true;
                }

                // 2. 会话已完成且保留时间已过
                if (session.getStatus() == DebugSession.Status.COMPLETED
                        || session.getStatus() == DebugSession.Status.CANCELLED) {
                    long elapsed = System.currentTimeMillis() - session.getCreateTime();
                    if (elapsed > session.getTimeoutMs() + COMPLETED_SESSION_RETENTION_MS) {
                        shouldRemove = true;
                    }
                }

                if (shouldRemove) {
                    it.remove();
                    removedCount++;
                }
            }

            if (removedCount > 0) {
                log.info("[DebugSessionRegistry] 定期清理完成，清除 {} 个过期会话，剩余活跃会话数={}",
                        removedCount, sessions.size());
            }
        } catch (Exception e) {
            log.error("[DebugSessionRegistry] 定期清理异常", e);
        }
    }
}
