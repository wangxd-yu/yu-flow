package org.yu.flow.module.rbac.support;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 方法/类级权限校验。
 * <ul>
 *   <li>一般：{@link #value()} 为 OR（满足其一即可）；拥有 {@code *} 视为超管。</li>
 *   <li>若同时声明 {@code *:view} 与 {@code *:write}：GET/HEAD/OPTIONS 只需 view，
 *       写方法（POST/PUT/PATCH/DELETE 等）必须持有 write。</li>
 * </ul>
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface RequirePerm {

    /** 所需权限码 */
    String[] value();
}
