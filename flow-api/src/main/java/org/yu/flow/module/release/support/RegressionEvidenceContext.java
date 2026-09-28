package org.yu.flow.module.release.support;

import java.util.Map;
import java.util.function.Supplier;

/**
 * 发布包导入期间的来源环境回归证据（线程内有效）。
 *
 * <p>导入即发布时资产刚落到目标环境，还没有本环境的回归记录；目标环境要求回归通过时，
 * 以发布包里记录的来源环境回归结果满足门禁。只在导入流程内生效，日常手工发布不受影响。</p>
 */
public final class RegressionEvidenceContext {

    private static final ThreadLocal<Map<String, String>> EVIDENCE = new ThreadLocal<>();

    private RegressionEvidenceContext() {
    }

    /**
     * @param evidence 键为 {@code 资产类型:资产ID}，值为给人看的证据描述
     */
    public static <T> T runWith(Map<String, String> evidence, Supplier<T> action) {
        Map<String, String> previous = EVIDENCE.get();
        EVIDENCE.set(evidence);
        try {
            return action.get();
        } finally {
            if (previous == null) {
                EVIDENCE.remove();
            } else {
                EVIDENCE.set(previous);
            }
        }
    }

    public static String find(String assetType, String assetId) {
        Map<String, String> evidence = EVIDENCE.get();
        return evidence == null ? null : evidence.get(assetType + ":" + assetId);
    }
}
