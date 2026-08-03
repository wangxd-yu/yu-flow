package org.yu.flow.engine.log;

/**
 * 日志策略模式枚举。
 *
 * <p>控制各类流程资产（接口/任务/MQ任务/服务）在执行时是否记录日志及记录粒度：</p>
 * <ul>
 *   <li>{@code SYSTEM_DEFAULT} — 继承全局配置 {@code yu.flow.engine.default-log-mode}，新建资产的默认值</li>
 *   <li>{@code OFF}            — 完全关闭，任何情况下不落库（适合高频探活、心跳接口）</li>
 *   <li>{@code ERROR_ONLY}     — 仅在执行失败/报错时记录日志摘要与错误信息（生产推荐，成本极低）</li>
 *   <li>{@code ALL}            — 全量记录：成功与失败均落库，包含 FlowTrace 快照（适合核心业务审计）</li>
 * </ul>
 *
 * @author yu-flow
 */
public final class LogMode {

    public static final String SYSTEM_DEFAULT = "SYSTEM_DEFAULT";
    public static final String OFF            = "OFF";
    public static final String ERROR_ONLY     = "ERROR_ONLY";
    public static final String ALL            = "ALL";

    private LogMode() {}

    /**
     * 将资产级原始 logMode 解析为运行时有效模式。
     *
     * <p>若资产未配置（null 或 SYSTEM_DEFAULT）则回退到全局默认；
     * 全局默认为空或不合法时最终兜底为 {@code ERROR_ONLY}。</p>
     *
     * @param rawMode       资产配置的 logMode 原始值（可为 null）
     * @param globalDefault 全局默认模式（来自 YuFlowProperties.Engine.defaultLogMode）
     * @return 解析后的有效模式，保证是 OFF / ERROR_ONLY / ALL 三者之一
     */
    public static String resolve(String rawMode, String globalDefault) {
        // 资产未设置或设为继承全局
        String effective = (rawMode == null || SYSTEM_DEFAULT.equals(rawMode)) ? globalDefault : rawMode;
        // 全局默认也可能为 null 或 SYSTEM_DEFAULT —— 最终兜底
        if (effective == null || SYSTEM_DEFAULT.equals(effective) || effective.isBlank()) {
            return ERROR_ONLY;
        }
        // 非法值兜底
        if (!OFF.equals(effective) && !ERROR_ONLY.equals(effective) && !ALL.equals(effective)) {
            return ERROR_ONLY;
        }
        return effective;
    }

    /**
     * 根据已解析的有效模式与本次执行结果决定是否落库日志摘要行。
     *
     * @param resolvedMode 经 {@link #resolve} 解析后的有效模式
     * @param isSuccess    本次执行是否成功
     * @return true 表示应落库；false 表示跳过
     */
    public static boolean shouldRecord(String resolvedMode, boolean isSuccess) {
        switch (resolvedMode) {
            case ALL:        return true;
            case ERROR_ONLY: return !isSuccess;
            case OFF:        return false;
            default:         return !isSuccess; // 防御兜底
        }
    }

    /**
     * 是否应记录 FlowTrace 快照（全量模式下才记录）。
     *
     * @param resolvedMode 经 {@link #resolve} 解析后的有效模式
     * @param isSuccess    本次执行是否成功（保留参数以对齐调用方；当前仅看模式）
     * @return true 表示应记录完整 Trace 快照
     */
    public static boolean shouldRecordTrace(String resolvedMode, boolean isSuccess) {
        return ALL.equals(resolvedMode);
    }

    /**
     * 是否应记录原始报文 / 消息头。
     *
     * <p>与摘要日志同行：{@code ERROR_ONLY} 失败、{@code ALL} 全量、幂等 SKIPPED 等凡落库的日志行
     * 都应带上报文，便于对账；成本由 {@code yu-flow.mq.log-body-max-chars} 截断与保留天数兜底。
     * Trace 快照仍仅见 {@link #shouldRecordTrace}。</p>
     */
    public static boolean shouldRecordPayload(String resolvedMode, boolean isSuccess) {
        return shouldRecord(resolvedMode, isSuccess);
    }

    /**
     * 将旧版 {@code logEnabled} 布尔值转换为对应的 logMode 枚举值（向后兼容）。
     *
     * @param logEnabled 旧布尔值（null 视为继承全局）
     * @return 对应的 logMode 枚举字符串
     */
    public static String fromLegacyBoolean(Boolean logEnabled) {
        if (logEnabled == null) return SYSTEM_DEFAULT;
        return Boolean.TRUE.equals(logEnabled) ? ALL : OFF;
    }
}
