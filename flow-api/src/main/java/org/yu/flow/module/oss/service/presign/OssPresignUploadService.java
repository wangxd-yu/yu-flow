package org.yu.flow.module.oss.service.presign;

import cn.hutool.core.util.StrUtil;
import io.minio.StatObjectResponse;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.yu.flow.config.DemoModeGuard;
import org.yu.flow.config.YuFlowProperties;
import org.yu.flow.exception.FlowException;
import org.yu.flow.module.host.FlowHostPrincipal;
import org.yu.flow.module.oss.client.OssStorageService;
import org.yu.flow.module.oss.domain.OssConnectionDO;
import org.yu.flow.module.oss.domain.OssObjectDO;
import org.yu.flow.module.oss.domain.OssUploadProfileDO;
import org.yu.flow.module.oss.dto.OssPresignUploadInitDTO;
import org.yu.flow.module.oss.dto.OssUploadOptions;
import org.yu.flow.module.oss.dto.OssUploadResultDTO;
import org.yu.flow.module.oss.repository.OssConnectionRepository;
import org.yu.flow.module.oss.repository.OssObjectRepository;
import org.yu.flow.module.oss.service.OssArchiveExtractService;
import org.yu.flow.module.oss.service.OssQuotaService;
import org.yu.flow.module.oss.service.OssThumbnailService;
import org.yu.flow.module.oss.service.OssUploadProfileService;
import org.yu.flow.module.oss.support.OssBizFieldValidator;
import org.yu.flow.module.oss.support.OssKeyPatternResolver;
import org.yu.flow.module.oss.support.OssProfileCallerAuth;
import org.yu.flow.module.oss.support.OssUploaderIdentity;
import org.yu.flow.module.oss.support.OssUploadRequestParser;
import org.yu.flow.module.rbac.service.RbacService;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Locale;
import java.util.Map;
import org.yu.flow.module.oss.config.ConditionalOnOssEnabled;

/**
 * 预签名直传：init 开票（预创建 PENDING 台账 + 签发 PUT URL）→ 客户端直传 OSS → confirm 复核转 ACTIVE。
 * <p>与网关代理上传（{@code /flow-api/oss/upload}）、网关侧分片上传并存，文件体不经过应用进程。</p>
 */
@Slf4j
@ConditionalOnOssEnabled
@Service
public class OssPresignUploadService {

    private static final ZoneId ZONE_SH = ZoneId.of("Asia/Shanghai");

    @Resource
    private OssUploadProfileService ossUploadProfileService;

    @Resource
    private OssConnectionRepository ossConnectionRepository;

    @Resource
    private OssObjectRepository ossObjectRepository;

    @Resource
    private OssStorageService ossStorageService;

    @Resource
    private DemoModeGuard demoModeGuard;

    @Resource
    private YuFlowProperties yuFlowProperties;

    @Resource
    private RbacService rbacService;

    @Resource
    private OssProfileCallerAuth ossProfileCallerAuth;

    @Resource
    private OssQuotaService ossQuotaService;

    @Resource
    private OssThumbnailService ossThumbnailService;

    @Resource
    private OssArchiveExtractService ossArchiveExtractService;

    @Transactional
    public OssPresignUploadInitDTO init(String profileCode, String originalName, String contentType,
                                        Long declaredSizeBytes, Map<String, String> bizFields,
                                        FlowHostPrincipal principal, HttpServletRequest request) {
        if (!yuFlowProperties.getOss().isPresignUploadEnabled()) {
            throw new FlowException("OSS_PRESIGN_UPLOAD_DISABLED", "预签名直传已全局关闭");
        }
        OssUploadProfileDO profile = ossUploadProfileService.requireByCode(profileCode);
        validateUploadAuth(profile, principal);
        if (!Boolean.TRUE.equals(profile.getPresignUploadEnabled())) {
            throw new FlowException("OSS_PRESIGN_UPLOAD_DISABLED",
                    "该上传场景未开放预签名直传: " + profile.getCode());
        }
        if (StrUtil.isBlank(originalName)) {
            throw new FlowException("OSS_FILE_REQUIRED", "originalName 不能为空");
        }

        OssConnectionDO connection = ossConnectionRepository.findByCode(profile.getConnectionCode())
                .orElseThrow(() -> new FlowException("OSS_CONNECTION_NOT_FOUND",
                        "OSS 连接不存在: " + profile.getConnectionCode()));
        if (connection.getEnabled() == null || !connection.getEnabled()) {
            throw new FlowException("OSS_CONNECTION_DISABLED", "OSS 连接已停用");
        }

        String bucket = resolveBucket(profile, connection);
        demoModeGuard.checkOssWrite(bucket);

        String ct = StrUtil.blankToDefault(contentType, "application/octet-stream");
        String extension = extractExtension(originalName);
        validateExtension(extension, profile.getAllowedExtensions());
        validateContentType(ct, profile.getAllowedContentTypes());

        long declared = declaredSizeBytes != null && declaredSizeBytes > 0 ? declaredSizeBytes : 0;
        if (declared > 0) {
            validateSize(declared, profile);
        }

        String objectKey;
        try {
            objectKey = OssKeyPatternResolver.resolve(
                    profile.getKeyPattern(), profile.getCode(), originalName, connection.getKeyPrefix());
        } catch (IllegalArgumentException e) {
            throw new FlowException("OSS_KEY_INVALID", e.getMessage());
        }

        String bizMeta = OssBizFieldValidator.validateAndSerialize(profile.getBizFieldsSchema(), bizFields);
        OssUploadOptions options = OssUploadRequestParser.parseOptions(request, bizFields);
        LocalDateTime expiresAt = OssUploadRequestParser.resolveExpiresAt(options);
        OssUploaderIdentity.Snapshot uploader = OssUploaderIdentity.from(principal, options);
        if (options != null && StrUtil.isNotBlank(options.getUploadedByOverride())) {
            applyAdminOverrideAuth(principal);
        }
        ossQuotaService.checkBeforeUpload(profile, uploader.getUploadedBy(), uploader.getUploadedByUserType(),
                declared, 1);

        LocalDateTime now = LocalDateTime.now(ZONE_SH);
        OssObjectDO entity = OssObjectDO.builder()
                .profileCode(profile.getCode())
                .connectionCode(connection.getCode())
                .bucket(bucket)
                .objectKey(objectKey)
                .visibility(profile.getVisibility())
                .originalName(originalName)
                .contentType(ct)
                .extension(extension)
                .sizeBytes(declared)
                .bizMeta(bizMeta)
                .uploadedBy(uploader.getUploadedBy())
                .uploadedByUserType(uploader.getUploadedByUserType())
                .uploadedByName(uploader.getUploadedByName())
                .deptId(uploader.getDeptId())
                .status(OssObjectDO.STATUS_PENDING)
                .expiresAt(expiresAt)
                .objectPurged(false)
                .thumbStatus(OssThumbnailService.THUMB_NONE)
                .extractStatus(OssArchiveExtractService.EXTRACT_NONE)
                .createTime(now)
                .updateTime(now)
                .build();
        entity = ossObjectRepository.save(entity);

        int expireSeconds = resolvePutExpireSeconds();
        String uploadUrl = ossStorageService.presignPutUrl(connection.getCode(), bucket, objectKey, expireSeconds);

        return new OssPresignUploadInitDTO()
                .setObjectId(entity.getId())
                .setUploadUrl(uploadUrl)
                .setMethod("PUT")
                .setBucket(bucket)
                .setObjectKey(objectKey)
                .setExpireSeconds(expireSeconds)
                .setUrlExpiresAt(now.plusSeconds(expireSeconds));
    }

    /**
     * 客户端直传完成后的复核：以 OSS 侧真实大小/类型为准，通过后台账转 ACTIVE。
     */
    @Transactional
    public OssUploadResultDTO confirm(String objectId, FlowHostPrincipal principal) {
        OssObjectDO entity = ossObjectRepository.findById(objectId)
                .orElseThrow(() -> new FlowException("OSS_OBJECT_NOT_FOUND", "文件台账不存在: " + objectId));
        OssUploadProfileDO profile = ossUploadProfileService.requireByCode(entity.getProfileCode());
        validateUploadAuth(profile, principal);
        validateOwnership(entity, principal);
        if (OssObjectDO.STATUS_ACTIVE.equals(entity.getStatus())) {
            // 幂等：重复 confirm 直接回放已生效的结果
            return buildResult(entity);
        }
        if (!OssObjectDO.STATUS_PENDING.equals(entity.getStatus())) {
            throw new FlowException("OSS_PRESIGN_STATE_INVALID", "台账状态不允许确认: " + entity.getStatus());
        }

        demoModeGuard.checkOssWrite(entity.getBucket());

        StatObjectResponse stat = ossStorageService.statObjectOrNull(
                entity.getConnectionCode(), entity.getBucket(), entity.getObjectKey());
        if (stat == null) {
            throw new FlowException("OSS_PRESIGN_OBJECT_MISSING", "OSS 上未找到该对象，请确认直传是否成功");
        }

        long realSize = stat.size();
        if (realSize <= 0) {
            purgeQuietly(entity);
            throw new FlowException("OSS_FILE_EMPTY", "直传对象为空");
        }
        try {
            validateSize(realSize, profile);
            String realCt = StrUtil.blankToDefault(stat.contentType(), entity.getContentType());
            validateContentType(realCt, profile.getAllowedContentTypes());
            ossQuotaService.checkBeforeUpload(profile, entity.getUploadedBy(), entity.getUploadedByUserType(),
                    realSize, 1);
            entity.setContentType(realCt);
        } catch (FlowException e) {
            // 复核不通过：先物理清掉已直传的对象，台账留在 PENDING 由清理任务回收
            purgeQuietly(entity);
            throw e;
        }

        OssConnectionDO connection = ossConnectionRepository.findByCode(entity.getConnectionCode())
                .orElseThrow(() -> new FlowException("OSS_CONNECTION_NOT_FOUND", "连接不存在"));

        String visibility = profile.getVisibility();
        entity.setVisibility(visibility);
        entity.setPublicPath(OssObjectDO.VISIBILITY_PUBLIC.equals(visibility)
                ? OssKeyPatternResolver.buildPublicPath(entity.getObjectKey()) : null);
        entity.setSizeBytes(realSize);
        entity.setStatus(OssObjectDO.STATUS_ACTIVE);
        entity.setUpdateTime(LocalDateTime.now(ZONE_SH));
        entity = ossObjectRepository.save(entity);
        ossThumbnailService.scheduleIfNeeded(entity, profile);
        ossArchiveExtractService.scheduleIfNeeded(entity, profile);

        OssUploadResultDTO dto = buildResult(entity);
        if (OssObjectDO.VISIBILITY_PUBLIC.equals(visibility)) {
            dto.setPublicUrl(OssKeyPatternResolver.buildPublicUrl(
                    connection.getPublicBaseUrl(), entity.getPublicPath()));
        }
        return dto;
    }

    /**
     * 客户端主动放弃：清掉可能已直传的对象并软删台账。
     */
    @Transactional
    public void abort(String objectId, FlowHostPrincipal principal) {
        OssObjectDO entity = ossObjectRepository.findById(objectId).orElse(null);
        if (entity == null || !OssObjectDO.STATUS_PENDING.equals(entity.getStatus())) {
            return;
        }
        validateOwnership(entity, principal);
        entity.setStatus(OssObjectDO.STATUS_DELETED);
        entity.setObjectPurged(false);
        entity.setUpdateTime(LocalDateTime.now(ZONE_SH));
        ossObjectRepository.save(entity);
    }

    private OssUploadResultDTO buildResult(OssObjectDO entity) {
        return new OssUploadResultDTO()
                .setId(entity.getId())
                .setVisibility(entity.getVisibility())
                .setOriginalName(entity.getOriginalName())
                .setSizeBytes(entity.getSizeBytes())
                .setContentType(entity.getContentType())
                .setPublicPath(entity.getPublicPath())
                .setExpiresAt(entity.getExpiresAt())
                .setThumbStatus(entity.getThumbStatus())
                .setThumbPublicPath(entity.getThumbPublicPath())
                .setHasThumbnail(OssThumbnailService.THUMB_READY.equals(entity.getThumbStatus()))
                .setExtractStatus(entity.getExtractStatus());
    }

    private void purgeQuietly(OssObjectDO entity) {
        try {
            ossStorageService.removeObject(entity.getConnectionCode(), entity.getBucket(), entity.getObjectKey());
        } catch (Exception e) {
            log.warn("[OSS] presign purge failed key={}: {}", entity.getObjectKey(), e.getMessage());
        }
    }

    private int resolvePutExpireSeconds() {
        int seconds = yuFlowProperties.getOss().getPresignPutExpireSeconds();
        return seconds > 0 ? seconds : 3600;
    }

    private void validateSize(long size, OssUploadProfileDO profile) {
        long presignMax = yuFlowProperties.getOss().getPresignMaxUploadBytes();
        if (presignMax > 0 && size > presignMax) {
            throw new FlowException("OSS_FILE_TOO_LARGE", "文件大小超出直传上限 " + presignMax + " 字节");
        }
        if (profile.getMaxSizeBytes() != null && profile.getMaxSizeBytes() > 0 && size > profile.getMaxSizeBytes()) {
            throw new FlowException("OSS_FILE_TOO_LARGE", "文件大小超出场景上限 " + profile.getMaxSizeBytes() + " 字节");
        }
    }

    private void validateUploadAuth(OssUploadProfileDO profile, FlowHostPrincipal principal) {
        ossProfileCallerAuth.assertUpload(profile, principal);
    }

    private void applyAdminOverrideAuth(FlowHostPrincipal principal) {
        if (principal == null || !rbacService.hasAnyPerm(principal.getUsername(), "flow:oss:admin", "*")) {
            throw new FlowException("RBAC_FORBIDDEN", "代传 uploadedBy 需要 flow:oss:admin 权限");
        }
    }

    /**
     * confirm / abort 只允许开票者本人或 OSS 管理员操作，避免他人凭 objectId 摘取台账。
     */
    private void validateOwnership(OssObjectDO entity, FlowHostPrincipal principal) {
        if (StrUtil.isBlank(entity.getUploadedBy())) {
            return;
        }
        if (OssUploaderIdentity.isSelf(entity, principal)) {
            return;
        }
        if (principal != null && rbacService.hasAnyPerm(principal.getUsername(), "flow:oss:admin", "*")) {
            return;
        }
        throw new FlowException("RBAC_FORBIDDEN", "无权操作该上传任务");
    }

    private static String resolveBucket(OssUploadProfileDO profile, OssConnectionDO connection) {
        if (StrUtil.isNotBlank(profile.getBucketOverride())) {
            return profile.getBucketOverride().trim();
        }
        if (OssUploadProfileDO.VISIBILITY_PUBLIC.equals(profile.getVisibility())) {
            if (StrUtil.isBlank(connection.getPublicBucket())) {
                throw new FlowException("OSS_PUBLIC_BUCKET_REQUIRED", "连接未配置公有桶");
            }
            return connection.getPublicBucket();
        }
        if (StrUtil.isBlank(connection.getPrivateBucket())) {
            throw new FlowException("OSS_PRIVATE_BUCKET_REQUIRED", "连接未配置私有桶");
        }
        return connection.getPrivateBucket();
    }

    private static void validateExtension(String extension, String allowedExtensions) {
        if (StrUtil.isBlank(allowedExtensions)) {
            return;
        }
        String ext = StrUtil.blankToDefault(extension, "").toLowerCase(Locale.ROOT);
        boolean ok = java.util.Arrays.stream(allowedExtensions.split(","))
                .map(String::trim)
                .filter(StrUtil::isNotBlank)
                .map(s -> s.toLowerCase(Locale.ROOT).replace(".", ""))
                .anyMatch(s -> s.equals(ext));
        if (!ok) {
            throw new FlowException("OSS_EXTENSION_DENIED", "不允许的文件扩展名: " + extension);
        }
    }

    private static void validateContentType(String contentType, String allowedContentTypes) {
        if (StrUtil.isBlank(allowedContentTypes)) {
            return;
        }
        String ct = StrUtil.blankToDefault(contentType, "").toLowerCase(Locale.ROOT);
        boolean ok = java.util.Arrays.stream(allowedContentTypes.split(","))
                .map(String::trim)
                .filter(StrUtil::isNotBlank)
                .map(s -> s.toLowerCase(Locale.ROOT))
                .anyMatch(ct::equals);
        if (!ok) {
            throw new FlowException("OSS_CONTENT_TYPE_DENIED", "不允许的 Content-Type: " + contentType);
        }
    }

    private static String extractExtension(String filename) {
        if (StrUtil.isBlank(filename)) {
            return "";
        }
        int dot = filename.lastIndexOf('.');
        if (dot < 0 || dot == filename.length() - 1) {
            return "";
        }
        return filename.substring(dot + 1).toLowerCase(Locale.ROOT);
    }
}
