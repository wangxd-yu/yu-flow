package org.yu.flow.engine.log;

/**
 * 原始报文落库策略：控制 MQ 消费日志中 messageBody / messageHeaders 的存储粒度。
 */
public final class LogPayloadMode {

    public static final String SYSTEM_DEFAULT = "SYSTEM_DEFAULT";
    public static final String FULL           = "FULL";
    public static final String MASK           = "MASK";
    public static final String OFF            = "OFF";

    private LogPayloadMode() {}

    /**
     * 将任务级原始 logPayloadMode 解析为运行时有效模式。
     *
     * @param rawMode       任务配置（可为 null）
     * @param globalDefault 全局默认（来自 sys-config / yml）
     * @return FULL / MASK / OFF 之一
     */
    public static String resolve(String rawMode, String globalDefault) {
        String effective = (rawMode == null || SYSTEM_DEFAULT.equals(rawMode)) ? globalDefault : rawMode;
        if (effective == null || SYSTEM_DEFAULT.equals(effective) || effective.isBlank()) {
            return FULL;
        }
        if (!FULL.equals(effective) && !MASK.equals(effective) && !OFF.equals(effective)) {
            return FULL;
        }
        return effective;
    }

    /** OFF 时不存储任何报文 */
    public static boolean shouldStorePayload(String resolved) {
        return !OFF.equals(resolved);
    }

    /** MASK 时落库占位符，不存明文 */
    public static boolean isMask(String resolved) {
        return MASK.equals(resolved);
    }
}
