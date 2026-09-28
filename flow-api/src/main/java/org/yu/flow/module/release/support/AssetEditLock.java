package org.yu.flow.module.release.support;

import java.util.function.Supplier;

/**
 * 资产编辑锁的线程内放行开关：发布包导入与回滚是锁定环境下唯一合法的资产变更入口。
 */
public final class AssetEditLock {

    private static final ThreadLocal<Boolean> UNLOCKED = ThreadLocal.withInitial(() -> Boolean.FALSE);

    private AssetEditLock() {
    }

    public static <T> T runUnlocked(Supplier<T> action) {
        boolean previous = UNLOCKED.get();
        UNLOCKED.set(Boolean.TRUE);
        try {
            return action.get();
        } finally {
            if (previous) {
                UNLOCKED.set(Boolean.TRUE);
            } else {
                UNLOCKED.remove();
            }
        }
    }

    public static boolean isUnlocked() {
        return UNLOCKED.get();
    }
}
