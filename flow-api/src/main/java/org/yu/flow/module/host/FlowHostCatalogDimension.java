package org.yu.flow.module.host;

import cn.hutool.core.util.StrUtil;
import org.yu.flow.exception.FlowException;

/**
 * 宿主身份目录维度，与 {@link CallerPolicy} 字段一一对应。
 */
public enum FlowHostCatalogDimension {

    /** {@code CallerPolicy.userTypes} / {@code FlowHostPrincipal.userType} */
    USER_TYPE,
    /** {@code CallerPolicy.roles} */
    ROLE,
    /** {@code CallerPolicy.permissions} */
    PERMISSION,
    /** {@code CallerPolicy.deptIds} */
    DEPT,
    /** {@code CallerPolicy.userIds} */
    USER;

    public static FlowHostCatalogDimension parse(String raw) {
        if (StrUtil.isBlank(raw)) {
            throw new FlowException("HOST_CATALOG_DIMENSION_INVALID", "dimension 不能为空");
        }
        for (FlowHostCatalogDimension d : values()) {
            if (d.name().equalsIgnoreCase(raw.trim())) {
                return d;
            }
        }
        throw new FlowException("HOST_CATALOG_DIMENSION_INVALID",
                "dimension 仅支持 USER_TYPE / ROLE / PERMISSION / DEPT / USER");
    }
}
