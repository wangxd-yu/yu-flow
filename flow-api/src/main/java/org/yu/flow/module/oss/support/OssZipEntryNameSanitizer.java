package org.yu.flow.module.oss.support;

import cn.hutool.core.util.StrUtil;

import java.util.HashSet;
import java.util.Set;

/**
 * Zip 打包条目名消毒，防止路径穿越。
 */
public final class OssZipEntryNameSanitizer {

    private OssZipEntryNameSanitizer() {
    }

    public static String sanitize(String originalName, Set<String> usedNames) {
        String base = StrUtil.blankToDefault(originalName, "file");
        base = base.replace('\\', '/');
        int slash = base.lastIndexOf('/');
        if (slash >= 0) {
            base = base.substring(slash + 1);
        }
        base = base.replace("..", "_").replace("\0", "");
        if (StrUtil.isBlank(base)) {
            base = "file";
        }
        if (usedNames == null) {
            return base;
        }
        String candidate = base;
        int i = 1;
        while (usedNames.contains(candidate)) {
            int dot = base.lastIndexOf('.');
            if (dot > 0) {
                candidate = base.substring(0, dot) + "(" + i + ")" + base.substring(dot);
            } else {
                candidate = base + "(" + i + ")";
            }
            i++;
        }
        usedNames.add(candidate);
        return candidate;
    }
}
