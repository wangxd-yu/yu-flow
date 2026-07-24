package org.yu.flow.module.rbac.support;

import cn.hutool.crypto.digest.BCrypt;

/**
 * 管理端密码哈希（BCrypt）。
 */
public final class RbacPasswordUtil {

    private RbacPasswordUtil() {
    }

    public static String hash(String raw) {
        return BCrypt.hashpw(raw);
    }

    public static boolean matches(String raw, String hash) {
        if (raw == null || hash == null) {
            return false;
        }
        try {
            return BCrypt.checkpw(raw, hash);
        } catch (Exception e) {
            return false;
        }
    }
}
