package org.yu.flow.module.oss.support;

import cn.hutool.core.util.StrUtil;
import jakarta.annotation.Resource;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Component;
import org.yu.flow.module.host.FlowHostDataScope;
import org.yu.flow.module.host.FlowHostIdentityCatalogService;
import org.yu.flow.module.host.FlowHostPrincipal;
import org.yu.flow.module.oss.config.ConditionalOnOssEnabled;
import org.yu.flow.module.oss.domain.OssObjectDO;
import org.yu.flow.module.oss.domain.OssUploadProfileDO;
import org.yu.flow.module.oss.repository.OssUploadProfileRepository;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * 把访问规则接到上传闸、下载闸和列表过滤。
 */
@ConditionalOnOssEnabled
@Component
public class OssAccessRulesEngine {

    @Resource
    private OssUploadProfileRepository ossUploadProfileRepository;

    @Resource
    private FlowHostIdentityCatalogService hostIdentityCatalogService;

    public OssAccessRules parse(OssUploadProfileDO profile) {
        return OssAccessSupport.parse(profile);
    }

    public boolean canAccessObject(OssObjectDO object, OssUploadProfileDO profile,
                                   FlowHostDataScope incoming, FlowHostPrincipal principal) {
        if (object == null || !OssObjectDO.STATUS_ACTIVE.equals(object.getStatus())) {
            return false;
        }
        if (OssObjectDO.VISIBILITY_PUBLIC.equals(object.getVisibility())) {
            return true;
        }
        if (OssAccessSupport.isFlowConsole(principal)) {
            return OssDataScopeSpecification.canAccessObject(object, incoming, principal);
        }
        OssUploadProfileDO resolved = profile != null ? profile : resolveProfile(object);
        OssAccessSupport.OssDownloadGrant grant =
                OssAccessSupport.mergeDownload(OssAccessSupport.parse(resolved), principal);
        return OssAccessSupport.allowsObject(object, grant, principal, expandedDepts(principal));
    }

    public Specification<OssObjectDO> listSpec(FlowHostDataScope incoming, FlowHostPrincipal principal) {
        if (OssAccessSupport.isFlowConsole(principal)) {
            return OssDataScopeSpecification.withScope(incoming, principal);
        }
        List<OssUploadProfileDO> profiles = ossUploadProfileRepository.findAll();
        String userId = principal != null ? StrUtil.trim(principal.getUserId()) : null;
        Set<String> depts = expandedDepts(principal);
        return (root, cq, cb) -> {
            List<Predicate> allow = new ArrayList<>();
            allow.add(cb.equal(root.get("visibility"), OssObjectDO.VISIBILITY_PUBLIC));
            for (OssUploadProfileDO profile : profiles) {
                if (profile == null || StrUtil.isBlank(profile.getCode())) {
                    continue;
                }
                if (OssUploadProfileDO.VISIBILITY_PUBLIC.equalsIgnoreCase(StrUtil.trim(profile.getVisibility()))) {
                    continue;
                }
                OssAccessSupport.OssDownloadGrant grant =
                        OssAccessSupport.mergeDownload(OssAccessSupport.parse(profile), principal);
                if (grant.none()) {
                    continue;
                }
                String code = profile.getCode();
                if (grant.all) {
                    allow.add(cb.equal(root.get("profileCode"), code));
                    continue;
                }
                List<Predicate> inner = new ArrayList<>();
                if (grant.self && StrUtil.isNotBlank(userId)) {
                    inner.add(OssUploaderIdentity.selfPredicate(root, cb, principal));
                }
                if (grant.dept && depts != null && !depts.isEmpty()) {
                    inner.add(root.get("deptId").in(depts));
                }
                if (!inner.isEmpty()) {
                    allow.add(cb.and(cb.equal(root.get("profileCode"), code),
                            cb.or(inner.toArray(new Predicate[0]))));
                }
            }
            return cb.and(
                    cb.equal(root.get("status"), OssObjectDO.STATUS_ACTIVE),
                    cb.or(allow.toArray(new Predicate[0])));
        };
    }

    public void assertHostUpload(OssUploadProfileDO profile, FlowHostPrincipal principal) {
        OssAccessSupport.assertHostUpload(profile, principal);
    }

    public void assertHostDownload(OssUploadProfileDO profile, FlowHostPrincipal principal) {
        OssAccessSupport.assertHostDownload(profile, principal);
    }

    private Set<String> expandedDepts(FlowHostPrincipal principal) {
        if (hostIdentityCatalogService == null) {
            return Set.of();
        }
        return hostIdentityCatalogService.expandPrincipalDepts(principal);
    }

    public OssUploadProfileDO resolveProfile(OssObjectDO object) {
        if (object == null || StrUtil.isBlank(object.getProfileCode())) {
            return null;
        }
        return ossUploadProfileRepository.findByCode(object.getProfileCode()).orElse(null);
    }
}
