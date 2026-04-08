package org.yu.flow.util;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.CopyOptions;

public class BeanMergeUtil {

    /**
     * 将source中非null属性合并到target
     * @param source 源对象
     * @param target 目标对象
     */
    public static void mergeNonNullProperties(Object source, Object target,
                                       boolean ignoreNull,
                                       String... ignoreProperties) {
        CopyOptions copyOptions = CopyOptions.create()
                .setIgnoreNullValue(ignoreNull)
                .setOverride(true)
                .setIgnoreProperties(ignoreProperties); // 忽略特定字段

        BeanUtil.copyProperties(source, target, copyOptions);
    }
}
