package org.yu.flow.module.rbac.support;

import cn.hutool.core.util.StrUtil;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.yu.flow.auto.util.JwtTokenUtil;
import org.yu.flow.exception.FlowException;
import org.yu.flow.module.rbac.service.RbacService;

import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * RBAC 权限切面。
 * <p>当注解同时包含 {@code *:view} 与 {@code *:write} 时，按 HTTP 方法分流：
 * GET/HEAD/OPTIONS 只需 view；写方法必须持有 write（避免 view 账号越权写）。</p>
 */
@Slf4j
@Aspect
@Component
public class RequirePermAspect {

    @Resource
    private RbacService rbacService;

    @Before("@within(org.yu.flow.module.rbac.support.RequirePerm) || @annotation(org.yu.flow.module.rbac.support.RequirePerm)")
    public void check(JoinPoint jp) {
        if (!rbacService.isRbacEnabled()) {
            return;
        }
        MethodSignature sig = (MethodSignature) jp.getSignature();
        Method method = sig.getMethod();
        RequirePerm ann = AnnotationUtils.findAnnotation(method, RequirePerm.class);
        if (ann == null) {
            ann = AnnotationUtils.findAnnotation(method.getDeclaringClass(), RequirePerm.class);
        }
        if (ann == null || ann.value().length == 0) {
            return;
        }
        String username = JwtTokenUtil.currentUsername();
        if (StrUtil.isBlank(username)) {
            throw new FlowException("RBAC_UNAUTHORIZED", "未登录或凭证无效");
        }
        String[] required = resolveRequiredPerms(ann.value());
        if (!rbacService.hasAnyPerm(username, required)) {
            throw new FlowException("RBAC_FORBIDDEN", "无权限: " + String.join(",", required));
        }
    }

    /**
     * 从 {@code {xxx:view, xxx:write}} 中按 HTTP 方法选出实际要求的权限码。
     */
    static String[] resolveRequiredPerms(String[] annotated) {
        if (annotated == null || annotated.length == 0) {
            return annotated;
        }
        List<String> views = new ArrayList<>();
        List<String> writes = new ArrayList<>();
        List<String> others = new ArrayList<>();
        for (String code : annotated) {
            if (StrUtil.isBlank(code)) {
                continue;
            }
            String c = code.trim();
            if (c.endsWith(":view")) {
                views.add(c);
            } else if (c.endsWith(":write")) {
                writes.add(c);
            } else {
                others.add(c);
            }
        }
        // 同时声明了 view+write 时才按 HTTP 分流；否则保持原 OR 语义
        if (!views.isEmpty() && !writes.isEmpty()) {
            if (isReadHttpMethod()) {
                return views.toArray(new String[0]);
            }
            return writes.toArray(new String[0]);
        }
        return annotated;
    }

    private static boolean isReadHttpMethod() {
        try {
            ServletRequestAttributes attrs =
                    (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attrs == null) {
                return false;
            }
            HttpServletRequest req = attrs.getRequest();
            if (req == null || req.getMethod() == null) {
                return false;
            }
            String m = req.getMethod().toUpperCase(Locale.ROOT);
            return "GET".equals(m) || "HEAD".equals(m) || "OPTIONS".equals(m);
        } catch (Exception e) {
            return false;
        }
    }
}
