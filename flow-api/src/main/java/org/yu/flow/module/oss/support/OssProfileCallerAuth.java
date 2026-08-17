package org.yu.flow.module.oss.support;

import cn.hutool.core.util.StrUtil;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;
import org.yu.flow.exception.FlowException;
import org.yu.flow.module.host.FlowHostPrincipal;
import org.yu.flow.module.oss.config.ConditionalOnOssEnabled;
import org.yu.flow.module.oss.domain.OssObjectDO;
import org.yu.flow.module.oss.domain.OssUploadProfileDO;
import org.yu.flow.module.oss.repository.OssUploadProfileRepository;
import org.yu.flow.module.rbac.service.RbacService;

import java.util.ArrayList;
import java.util.List;

/**
 * OSS 场景上传 / 下载：登录门禁、访问规则、Flow RBAC 权限码。
 */
@ConditionalOnOssEnabled
@Component
public class OssProfileCallerAuth {

    public static final String CODE_DENIED = OssAccessSupport.CODE_DENIED;

    @Resource
    private RbacService rbacService;

    @Resource
    private OssUploadProfileRepository ossUploadProfileRepository;

    public static boolean isOpenPrincipal(FlowHostPrincipal principal) {
        return OssAccessSupport.isOpenPrincipal(principal);
    }

    public void validateOnSave(OssUploadProfileDO profile) {
        OssAccessSupport.validateOnSave(profile);
    }

    /**
     * 上传闸：场景启用 → 登录 → 宿主访问规则 →（非 OPEN）Flow uploadPerm。
     */
    public void assertUpload(OssUploadProfileDO profile, FlowHostPrincipal principal) {
        if (profile.getEnabled() == null || !profile.getEnabled()) {
            throw new FlowException("OSS_PROFILE_DISABLED", "上传场景已停用: " + profile.getCode());
        }
        boolean requireAuth = profile.getRequireAuth() == null || profile.getRequireAuth();
        if (requireAuth && principal == null) {
            throw new FlowException("RBAC_UNAUTHORIZED", "未登录或凭证无效");
        }
        OssAccessSupport.assertHostUpload(profile, principal);
        if (isOpenPrincipal(principal)) {
            return;
        }
        if (principal != null && StrUtil.isNotBlank(profile.getUploadPerm())) {
            if (!hasFlowPermission(principal, profile.getUploadPerm())) {
                throw new FlowException("RBAC_FORBIDDEN", "无上传权限: " + profile.getUploadPerm());
            }
        }
    }

    /**
     * 私有文件下载/查看闸（公有跳过）。
     */
    public void assertDownload(OssObjectDO object, FlowHostPrincipal principal) {
        if (object == null || OssObjectDO.VISIBILITY_PUBLIC.equals(object.getVisibility())) {
            return;
        }
        OssUploadProfileDO profile = ossUploadProfileRepository.findByCode(object.getProfileCode()).orElse(null);
        assertDownload(profile, principal);
    }

    public void assertDownload(OssUploadProfileDO profile, FlowHostPrincipal principal) {
        OssAccessSupport.assertHostDownload(profile, principal);
    }

    public boolean hasFlowPermission(FlowHostPrincipal principal, String rawPermissions) {
        if (principal == null || isOpenPrincipal(principal) || StrUtil.isBlank(rawPermissions)) {
            return false;
        }
        List<String> required = new ArrayList<>();
        for (String code : rawPermissions.split("[,，\\s]+")) {
            if (StrUtil.isNotBlank(code)) {
                required.add(code.trim());
            }
        }
        required.add("*");
        return rbacService.hasAnyPerm(principal.getUsername(), required.toArray(new String[0]));
    }

    /** 仅 Flow 管理端 JWT：场景 downloadPerm 作为跨范围兜底。宿主用户不走这层。 */
    public boolean grantedByDownloadPerm(OssUploadProfileDO profile, FlowHostPrincipal principal) {
        if (profile == null || !OssAccessSupport.isFlowConsole(principal)) {
            return false;
        }
        return StrUtil.isNotBlank(profile.getDownloadPerm())
                && hasFlowPermission(principal, profile.getDownloadPerm());
    }
}
