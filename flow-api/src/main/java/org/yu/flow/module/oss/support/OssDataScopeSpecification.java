package org.yu.flow.module.oss.support;

import cn.hutool.core.util.StrUtil;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;
import org.yu.flow.module.host.FlowHostDataScope;
import org.yu.flow.module.host.FlowHostPrincipal;
import org.yu.flow.module.host.FlowHostScopeType;
import org.yu.flow.module.oss.domain.OssObjectDO;

import java.util.ArrayList;
import java.util.List;

/**
 * OSS 台账数据范围 JPA Specification。
 */
public final class OssDataScopeSpecification {

    private OssDataScopeSpecification() {
    }

    public static Specification<OssObjectDO> withScope(FlowHostDataScope scope, FlowHostPrincipal principal) {
        return (root, cq, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("status"), OssObjectDO.STATUS_ACTIVE));

            if (scope == null || scope.getType() == null) {
                return cb.disjunction();
            }
            switch (scope.getType()) {
                case ALL -> {
                    // no extra filter
                }
                case SELF -> {
                    if (principal == null || StrUtil.isBlank(principal.getUserId())) {
                        return cb.disjunction();
                    }
                    predicates.add(cb.equal(root.get("uploadedBy"), principal.getUserId()));
                }
                case DEPT_LIST -> {
                    if (scope.getDeptIds() == null || scope.getDeptIds().isEmpty()) {
                        return cb.disjunction();
                    }
                    predicates.add(root.get("deptId").in(scope.getDeptIds()));
                }
                case USER_LIST -> {
                    if (scope.getUserIds() == null || scope.getUserIds().isEmpty()) {
                        return cb.disjunction();
                    }
                    predicates.add(root.get("uploadedBy").in(scope.getUserIds()));
                }
                case DENY -> {
                    return cb.disjunction();
                }
                default -> {
                    return cb.disjunction();
                }
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    public static boolean canAccessObject(OssObjectDO object, FlowHostDataScope scope, FlowHostPrincipal principal) {
        if (object == null || !OssObjectDO.STATUS_ACTIVE.equals(object.getStatus())) {
            return false;
        }
        if (scope == null || scope.getType() == null) {
            return false;
        }
        return switch (scope.getType()) {
            case ALL -> true;
            case SELF -> principal != null
                    && StrUtil.isNotBlank(principal.getUserId())
                    && principal.getUserId().equals(object.getUploadedBy());
            case DEPT_LIST -> object.getDeptId() != null
                    && scope.getDeptIds() != null
                    && scope.getDeptIds().contains(object.getDeptId());
            case USER_LIST -> object.getUploadedBy() != null
                    && scope.getUserIds() != null
                    && scope.getUserIds().contains(object.getUploadedBy());
            case DENY -> false;
        };
    }
}
