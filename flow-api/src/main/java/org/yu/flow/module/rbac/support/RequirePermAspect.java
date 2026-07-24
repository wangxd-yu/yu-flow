package org.yu.flow.module.rbac.support;

import cn.hutool.core.util.StrUtil;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.stereotype.Component;
import org.yu.flow.auto.util.JwtTokenUtil;
import org.yu.flow.exception.FlowException;
import org.yu.flow.module.rbac.service.RbacService;

import jakarta.annotation.Resource;
import java.lang.reflect.Method;

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
        if (!rbacService.hasAnyPerm(username, ann.value())) {
            throw new FlowException("RBAC_FORBIDDEN", "无权限: " + String.join(",", ann.value()));
        }
    }
}
