package org.yu.flow.module.oss.support;

import cn.hutool.core.util.StrUtil;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import lombok.Value;
import org.yu.flow.module.host.FlowHostPrincipal;
import org.yu.flow.module.oss.domain.OssObjectDO;
import org.yu.flow.module.oss.dto.OssUploadOptions;

import java.util.Locale;

/**
 * OSS 上传人身份：{@code uploaded_by}（体系内主键）+ {@code uploaded_by_user_type}（{@link FlowHostPrincipal#getUserType()}）。
 */
public final class OssUploaderIdentity {

    private OssUploaderIdentity() {
    }

    public static String normalizeUserType(String userType) {
        return StrUtil.isBlank(userType) ? null : userType.trim().toUpperCase(Locale.ROOT);
    }

    public static Snapshot from(FlowHostPrincipal principal, OssUploadOptions options) {
        String uploadedBy = principal == null ? null : StrUtil.trimToNull(principal.getUserId());
        String userType = principal == null ? null : normalizeUserType(principal.getUserType());
        String name = principal == null ? null : StrUtil.trimToNull(principal.getUsername());
        String deptId = principal == null ? null : StrUtil.trimToNull(principal.getDeptId());
        if (options != null && StrUtil.isNotBlank(options.getUploadedByOverride())) {
            uploadedBy = options.getUploadedByOverride().trim();
            name = StrUtil.blankToDefault(StrUtil.trimToNull(options.getUploadedByNameOverride()), uploadedBy);
            if (StrUtil.isNotBlank(options.getUploadedByUserTypeOverride())) {
                userType = normalizeUserType(options.getUploadedByUserTypeOverride());
            }
        }
        return new Snapshot(uploadedBy, userType, name, deptId);
    }

    public static boolean isSelf(OssObjectDO object, FlowHostPrincipal principal) {
        if (object == null || principal == null) {
            return false;
        }
        return sameUserId(object.getUploadedBy(), principal.getUserId())
                && sameUserType(object.getUploadedByUserType(), principal.getUserType());
    }

    public static boolean sameUserId(String left, String right) {
        String a = StrUtil.trimToNull(left);
        String b = StrUtil.trimToNull(right);
        return a != null && a.equals(b);
    }

    public static boolean sameUserType(String stored, String principalType) {
        String a = normalizeUserType(stored);
        String b = normalizeUserType(principalType);
        return a != null && a.equals(b);
    }

    public static Predicate selfPredicate(Root<OssObjectDO> root, CriteriaBuilder cb,
                                          FlowHostPrincipal principal) {
        if (principal == null
                || StrUtil.isBlank(principal.getUserId())
                || StrUtil.isBlank(normalizeUserType(principal.getUserType()))) {
            return cb.disjunction();
        }
        return cb.and(
                cb.equal(root.get("uploadedBy"), principal.getUserId().trim()),
                cb.equal(root.get("uploadedByUserType"), normalizeUserType(principal.getUserType())));
    }

    @Value
    public static class Snapshot {
        String uploadedBy;
        String uploadedByUserType;
        String uploadedByName;
        String deptId;
    }
}
