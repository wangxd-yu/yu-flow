package org.yu.flow.module.oss.service.impl;

import cn.hutool.core.util.StrUtil;
import jakarta.annotation.Resource;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.yu.flow.auto.dto.PageBean;
import org.yu.flow.config.DemoModeGuard;
import org.yu.flow.exception.FlowException;
import org.yu.flow.module.oss.domain.OssUploadProfileDO;
import org.yu.flow.module.oss.dto.OssUploadProfileDTO;
import org.yu.flow.module.oss.query.OssUploadProfileQueryDTO;
import org.yu.flow.module.oss.repository.OssUploadProfileRepository;
import org.yu.flow.module.oss.service.OssUploadProfileService;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import org.yu.flow.module.oss.config.ConditionalOnOssEnabled;
import org.yu.flow.module.oss.support.OssAccessRule;
import org.yu.flow.module.oss.support.OssAccessRules;
import org.yu.flow.module.oss.support.OssAccessSupport;
import org.yu.flow.module.oss.support.OssProfileCallerAuth;

@ConditionalOnOssEnabled
@Service
public class OssUploadProfileServiceImpl implements OssUploadProfileService {

    private static final ZoneId ZONE_SH = ZoneId.of("Asia/Shanghai");

    @Resource
    private OssUploadProfileRepository ossUploadProfileRepository;

    @Resource
    private DemoModeGuard demoModeGuard;

    @Resource
    private OssProfileCallerAuth ossProfileCallerAuth;

    @Override
    @Transactional
    public OssUploadProfileDO save(OssUploadProfileDO profileDO) {
        validateBasic(profileDO);
        profileDO.setId(null);
        profileDO.setDeleted(0);
        if (ossUploadProfileRepository.existsByCode(profileDO.getCode())) {
            throw new FlowException("OSS_PROFILE_CODE_DUPLICATED", "上传场景编码已存在: " + profileDO.getCode());
        }
        applyDefaults(profileDO);
        LocalDateTime now = LocalDateTime.now(ZONE_SH);
        profileDO.setCreateTime(now);
        profileDO.setUpdateTime(now);
        return ossUploadProfileRepository.save(profileDO);
    }

    @Override
    @Transactional
    public OssUploadProfileDO update(OssUploadProfileDO profileDO) {
        demoModeGuard.checkModifyOrDelete(profileDO.getId(), "OSS 上传场景");
        OssUploadProfileDO existing = requireProfile(profileDO.getId());

        if (profileDO.getName() != null) {
            existing.setName(profileDO.getName());
        }
        if (profileDO.getCode() != null && !profileDO.getCode().equals(existing.getCode())) {
            throw new FlowException("OSS_PROFILE_CODE_IMMUTABLE", "上传场景编码创建后不可修改");
        }
        if (profileDO.getConnectionCode() != null) {
            existing.setConnectionCode(profileDO.getConnectionCode());
        }
        if (profileDO.getVisibility() != null) {
            existing.setVisibility(normalizeVisibility(profileDO.getVisibility()));
        }
        if (profileDO.getBucketOverride() != null) {
            existing.setBucketOverride(profileDO.getBucketOverride());
        }
        if (profileDO.getKeyPattern() != null) {
            existing.setKeyPattern(profileDO.getKeyPattern());
        }
        if (profileDO.getAllowedContentTypes() != null) {
            existing.setAllowedContentTypes(profileDO.getAllowedContentTypes());
        }
        if (profileDO.getAllowedExtensions() != null) {
            existing.setAllowedExtensions(profileDO.getAllowedExtensions());
        }
        if (profileDO.getMaxSizeBytes() != null) {
            existing.setMaxSizeBytes(profileDO.getMaxSizeBytes());
        }
        if (profileDO.getMaxFilesPerRequest() != null) {
            existing.setMaxFilesPerRequest(profileDO.getMaxFilesPerRequest());
        }
        if (profileDO.getQuotaMaxBytes() != null) {
            existing.setQuotaMaxBytes(profileDO.getQuotaMaxBytes());
        }
        if (profileDO.getQuotaMaxFiles() != null) {
            existing.setQuotaMaxFiles(profileDO.getQuotaMaxFiles());
        }
        if (profileDO.getThumbnailEnabled() != null) {
            existing.setThumbnailEnabled(profileDO.getThumbnailEnabled());
        }
        // null 表示清空并回退全局：前端传 null / 不传保持原值；传 0 不合理
        if (profileDO.getThumbnailMaxEdge() != null) {
            existing.setThumbnailMaxEdge(profileDO.getThumbnailMaxEdge() <= 0 ? null : profileDO.getThumbnailMaxEdge());
        }
        if (profileDO.getThumbnailMaxSourceBytes() != null) {
            existing.setThumbnailMaxSourceBytes(
                    profileDO.getThumbnailMaxSourceBytes() <= 0 ? null : profileDO.getThumbnailMaxSourceBytes());
        }
        if (profileDO.getThumbnailJpegQuality() != null) {
            double q = profileDO.getThumbnailJpegQuality();
            existing.setThumbnailJpegQuality(q <= 0 || q > 1 ? null : q);
        }
        if (profileDO.getRequireAuth() != null) {
            existing.setRequireAuth(profileDO.getRequireAuth());
        }
        if (profileDO.getPresignUploadEnabled() != null) {
            existing.setPresignUploadEnabled(profileDO.getPresignUploadEnabled());
        }
        if (profileDO.getBizFieldsSchema() != null) {
            existing.setBizFieldsSchema(profileDO.getBizFieldsSchema());
        }
        if (profileDO.getUploadPerm() != null) {
            existing.setUploadPerm(profileDO.getUploadPerm());
        }
        if (profileDO.getDownloadPerm() != null) {
            existing.setDownloadPerm(profileDO.getDownloadPerm());
        }
        if (profileDO.getCallerPolicy() != null) {
            existing.setCallerPolicy(profileDO.getCallerPolicy());
        }
        if (profileDO.getEnabled() != null) {
            existing.setEnabled(profileDO.getEnabled());
        }
        if (profileDO.getRemark() != null) {
            existing.setRemark(profileDO.getRemark());
        }
        existing.setUpdateTime(LocalDateTime.now(ZONE_SH));
        validateBasic(existing);
        return ossUploadProfileRepository.save(existing);
    }

    @Override
    @Transactional
    public void delete(String id) {
        demoModeGuard.checkModifyOrDelete(id, "OSS 上传场景");
        ossUploadProfileRepository.deleteById(id);
    }

    @Override
    @Transactional
    public void batchDelete(List<String> ids) {
        if (ids == null || ids.isEmpty()) {
            return;
        }
        ids.forEach(id -> demoModeGuard.checkModifyOrDelete(id, "OSS 上传场景"));
        for (String id : ids) {
            delete(id);
        }
    }

    @Override
    public OssUploadProfileDO findById(String id) {
        return ossUploadProfileRepository.findById(id).orElse(null);
    }

    @Override
    public OssUploadProfileDO requireByCode(String code) {
        return ossUploadProfileRepository.findByCode(code)
                .orElseThrow(() -> new FlowException("OSS_PROFILE_NOT_FOUND", "上传场景不存在: " + code));
    }

    @Override
    public PageBean<OssUploadProfileDTO> findPage(OssUploadProfileQueryDTO queryDTO) {
        Pageable pageable = PageRequest.of(
                queryDTO.getPage(), queryDTO.getSize(),
                Sort.by(Sort.Direction.DESC, "createTime")
        );
        Specification<OssUploadProfileDO> spec = (root, cq, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (StrUtil.isNotBlank(queryDTO.getName())) {
                predicates.add(cb.like(root.get("name"), "%" + queryDTO.getName() + "%"));
            }
            if (StrUtil.isNotBlank(queryDTO.getCode())) {
                predicates.add(cb.like(root.get("code"), "%" + queryDTO.getCode() + "%"));
            }
            if (StrUtil.isNotBlank(queryDTO.getConnectionCode())) {
                predicates.add(cb.equal(root.get("connectionCode"), queryDTO.getConnectionCode()));
            }
            if (StrUtil.isNotBlank(queryDTO.getVisibility())) {
                predicates.add(cb.equal(root.get("visibility"), normalizeVisibility(queryDTO.getVisibility())));
            }
            if (queryDTO.getEnabled() != null) {
                predicates.add(cb.equal(root.get("enabled"), queryDTO.getEnabled()));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
        Page<OssUploadProfileDO> page = ossUploadProfileRepository.findAll(spec, pageable);
        List<OssUploadProfileDTO> items = page.getContent().stream()
                .map(OssUploadProfileDTO::fromDO)
                .collect(Collectors.toList());
        return new PageBean<>(items, page.getNumber(), page.getSize(),
                page.getTotalPages(), page.getTotalElements());
    }

    @Override
    public List<OssUploadProfileDTO> listEnabled() {
        return ossUploadProfileRepository.findByEnabled(true).stream()
                .map(OssUploadProfileDTO::fromDO)
                .collect(Collectors.toList());
    }

    private void validateBasic(OssUploadProfileDO profileDO) {
        if (profileDO == null) {
            throw new FlowException("OSS_PARAM_REQUIRED", "上传场景不能为空");
        }
        if (StrUtil.isBlank(profileDO.getCode())) {
            throw new FlowException("OSS_PROFILE_CODE_REQUIRED", "场景编码 code 不能为空");
        }
        profileDO.setCode(profileDO.getCode().trim());
        if (!profileDO.getCode().matches("^[a-z][a-z0-9_]{0,49}$")) {
            throw new FlowException("OSS_PROFILE_CODE_INVALID",
                    "场景编码只能包含小写字母、数字和下划线，且不能以数字开头，最长 50 位");
        }
        if (StrUtil.isBlank(profileDO.getName())) {
            throw new FlowException("OSS_PROFILE_NAME_REQUIRED", "场景名称不能为空");
        }
        if (StrUtil.isBlank(profileDO.getConnectionCode())) {
            throw new FlowException("OSS_CONNECTION_CODE_REQUIRED", "connectionCode 不能为空");
        }
        profileDO.setVisibility(normalizeVisibility(profileDO.getVisibility()));
        if (profileDO.getMaxFilesPerRequest() != null
                && (profileDO.getMaxFilesPerRequest() < 1 || profileDO.getMaxFilesPerRequest() > 100)) {
            throw new FlowException("OSS_PROFILE_LIMIT_INVALID", "单次文件数必须在 1 到 100 之间");
        }
        if (negative(profileDO.getMaxSizeBytes()) || negative(profileDO.getQuotaMaxBytes())
                || negative(profileDO.getQuotaMaxFiles()) || negative(profileDO.getThumbnailMaxSourceBytes())) {
            throw new FlowException("OSS_PROFILE_LIMIT_INVALID", "容量、数量限制不能为负数");
        }
        ossProfileCallerAuth.validateOnSave(profileDO);
    }

    private void applyDefaults(OssUploadProfileDO profileDO) {
        if (profileDO.getEnabled() == null) {
            profileDO.setEnabled(true);
        }
        if (profileDO.getRequireAuth() == null) {
            profileDO.setRequireAuth(true);
        }
        if (profileDO.getPresignUploadEnabled() == null) {
            profileDO.setPresignUploadEnabled(false);
        }
        if (profileDO.getMaxFilesPerRequest() == null) {
            profileDO.setMaxFilesPerRequest(1);
        }
        if (profileDO.getThumbnailEnabled() == null) {
            profileDO.setThumbnailEnabled(false);
        }
        if (profileDO.getDeleted() == null) {
            profileDO.setDeleted(0);
        }
        if (StrUtil.isBlank(profileDO.getKeyPattern())) {
            profileDO.setKeyPattern("{profile}/{yyyy}/{MM}/{uuid}_{filename}");
        }
        if (StrUtil.isBlank(profileDO.getCallerPolicy())
                && !OssUploadProfileDO.VISIBILITY_PUBLIC.equalsIgnoreCase(
                        StrUtil.trim(profileDO.getVisibility()))
                && (profileDO.getRequireAuth() == null || profileDO.getRequireAuth())) {
            OssAccessRules rules = new OssAccessRules();
            OssAccessRule rule = new OssAccessRule();
            rule.setName("已登录用户");
            rule.setPrincipals(OssAccessRule.PRINCIPALS_ANY);
            rule.setUpload(true);
            rule.setDownloadScope(OssAccessRule.SCOPE_SELF);
            rules.setRules(List.of(rule));
            profileDO.setCallerPolicy(OssAccessSupport.toJson(rules));
        }
    }

    private static String normalizeVisibility(String visibility) {
        if (StrUtil.isBlank(visibility)) {
            return OssUploadProfileDO.VISIBILITY_PRIVATE;
        }
        String normalized = visibility.trim().toUpperCase();
        if (!OssUploadProfileDO.VISIBILITY_PUBLIC.equals(normalized)
                && !OssUploadProfileDO.VISIBILITY_PRIVATE.equals(normalized)) {
            throw new FlowException("OSS_VISIBILITY_INVALID", "visibility 仅支持 PUBLIC 或 PRIVATE");
        }
        return normalized;
    }

    private static boolean negative(Number value) {
        return value != null && value.doubleValue() < 0;
    }

    private OssUploadProfileDO requireProfile(String id) {
        return ossUploadProfileRepository.findById(id)
                .orElseThrow(() -> new FlowException("OSS_PROFILE_NOT_FOUND", "上传场景不存在: " + id));
    }
}
