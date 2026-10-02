package org.yu.flow.module.rbac.support;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 每个管理端接口要么挂 {@link RequirePerm}（类或方法级），要么在下面的白名单里写明自带的鉴权方式。
 * 新增端点忘了挂权限码时这里会失败，避免权限矩阵随代码漂移。
 */
class RequirePermCoverageTest {

    /** 类名#方法名（或类名#*）→ 不挂权限码的理由 */
    private static final Map<String, String> SELF_AUTHORIZED = Map.ofEntries(
            Map.entry("FlowLoginController#*", "登录前接口（验证码、SM2 公钥、登录），网关对 /flow-api/login 放行"),
            Map.entry("AuthController#*", "当前用户自身操作（me / logout / 改密），网关已强制管理端 JWT，任何登录用户可用"),
            Map.entry("ReleaseController#currentEnv", "顶栏环境标识，只返回环境编码与开关状态；网关已强制管理端 JWT"),
            Map.entry("ExcelSignedDownloadController#*", "短期签名链（HMAC + TTL），网关对 /flow-api/download/excel 放行"),
            Map.entry("OpenOssUploadController#*", "开放入口：网关先做 AppKey HMAC 鉴权再交给 MVC"),
            Map.entry("OssObjectController#*", "宿主身份 FlowHostAuthSupport.requirePrincipal + 数据范围，逐方法校验"),
            Map.entry("OssUploadController#*", "上传场景访问规则在服务层校验（OssAccessSupport.assertHostUpload）"),
            Map.entry("OssMultipartUploadController#*", "上传场景访问规则在服务层校验（同 OssUploadController）"),
            Map.entry("OssPresignUploadController#*", "上传场景访问规则在服务层校验（同 OssUploadController）"));

    @Test
    void everyEndpointRequiresPermissionOrIsExplicitlyExempt() throws Exception {
        ClassPathScanningCandidateComponentProvider scanner = new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AnnotationTypeFilter(RestController.class));
        List<String> uncovered = new ArrayList<>();
        int endpoints = 0;
        for (BeanDefinition bd : scanner.findCandidateComponents("org.yu.flow")) {
            Class<?> type = Class.forName(bd.getBeanClassName());
            if (type.getProtectionDomain().getCodeSource().getLocation().getPath().contains("test-classes")) {
                continue;
            }
            boolean classLevel = AnnotatedElementUtils.hasAnnotation(type, RequirePerm.class);
            for (Method m : type.getDeclaredMethods()) {
                if (!AnnotatedElementUtils.hasAnnotation(m, RequestMapping.class)) {
                    continue;
                }
                endpoints++;
                if (classLevel || AnnotatedElementUtils.hasAnnotation(m, RequirePerm.class)) {
                    continue;
                }
                String key = type.getSimpleName() + "#" + m.getName();
                if (!SELF_AUTHORIZED.containsKey(key) && !SELF_AUTHORIZED.containsKey(type.getSimpleName() + "#*")) {
                    uncovered.add(key);
                }
            }
        }
        assertTrue(endpoints > 300, "扫描到的端点过少，扫描范围可能有误: " + endpoints);
        assertTrue(uncovered.isEmpty(), "以下端点既没有 @RequirePerm，也不在 SELF_AUTHORIZED 白名单里：\n"
                + String.join("\n", new TreeSet<>(uncovered)));
    }
}
