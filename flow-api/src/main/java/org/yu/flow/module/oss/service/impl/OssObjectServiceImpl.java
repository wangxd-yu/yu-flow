package org.yu.flow.module.oss.service.impl;

import cn.hutool.core.util.StrUtil;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.minio.GetObjectResponse;
import jakarta.annotation.Resource;
import jakarta.persistence.criteria.Predicate;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.yu.flow.auto.dto.PageBean;
import org.yu.flow.config.DemoModeGuard;
import org.yu.flow.config.YuFlowProperties;
import org.yu.flow.exception.FlowException;
import org.yu.flow.module.host.FlowHostDataScope;
import org.yu.flow.module.host.FlowHostPrincipal;
import org.yu.flow.module.oss.client.OssStorageService;
import org.yu.flow.module.oss.domain.OssConnectionDO;
import org.yu.flow.module.oss.domain.OssDownloadLogDO;
import org.yu.flow.module.oss.domain.OssObjectDO;
import org.yu.flow.module.oss.domain.OssUploadProfileDO;
import org.yu.flow.module.oss.dto.FlowOssFileResolvedDTO;
import org.yu.flow.module.oss.dto.OssObjectDTO;
import org.yu.flow.module.oss.dto.OssPresignUrlDTO;
import org.yu.flow.module.oss.dto.OssUploadOptions;
import org.yu.flow.module.oss.dto.OssUploadResultDTO;
import org.yu.flow.module.oss.query.OssObjectQueryDTO;
import org.yu.flow.module.oss.repository.OssConnectionRepository;
import org.yu.flow.module.oss.repository.OssObjectRepository;
import org.yu.flow.module.oss.repository.OssUploadProfileRepository;
import org.yu.flow.module.oss.service.OssDownloadLogService;
import org.yu.flow.module.oss.service.OssObjectRefService;
import org.yu.flow.module.oss.service.OssObjectService;
import org.yu.flow.module.oss.service.OssQuotaService;
import org.yu.flow.module.oss.service.OssThumbnailService;
import org.yu.flow.module.oss.service.OssUploadProfileService;
import org.yu.flow.module.oss.spi.FlowOssObjectAccessVoter;
import org.yu.flow.module.oss.support.OssAccessEvaluator;
import org.yu.flow.module.oss.support.OssBizFieldValidator;
import org.yu.flow.module.oss.support.OssDataScopeSpecification;
import org.yu.flow.module.oss.support.OssKeyPatternResolver;
import org.yu.flow.module.oss.support.OssUploadRequestParser;
import org.yu.flow.module.oss.support.OssZipEntryNameSanitizer;
import org.yu.flow.module.rbac.service.RbacService;

import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@Slf4j
@Service
public class OssObjectServiceImpl implements OssObjectService {

    private static final ZoneId ZONE_SH = ZoneId.of("Asia/Shanghai");

    /** 纯 Caffeine 堆内本地缓存：fileId:absolute -> resolvedUrl (5分钟过期，最多5万条) */
    private final Cache<String, String> fileUrlCache = Caffeine.newBuilder()
            .maximumSize(50000)
            .expireAfterWrite(5, TimeUnit.MINUTES)
            .build();

    /** 纯 Caffeine 堆内本地缓存：fileId:absolute -> FlowOssFileResolvedDTO (5分钟过期，最多5万条) */
    private final Cache<String, FlowOssFileResolvedDTO> fileDetailCache = Caffeine.newBuilder()
            .maximumSize(50000)
            .expireAfterWrite(5, TimeUnit.MINUTES)
            .build();
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Resource
    private OssObjectRepository ossObjectRepository;

    @Resource
    private OssConnectionRepository ossConnectionRepository;

    @Resource
    private OssUploadProfileService ossUploadProfileService;

    @Resource
    private OssStorageService ossStorageService;

    @Resource
    private OssDownloadLogService ossDownloadLogService;

    @Resource
    private DemoModeGuard demoModeGuard;

    @Resource
    private YuFlowProperties yuFlowProperties;

    @Resource
    private RbacService rbacService;

    @Resource
    private OssAccessEvaluator ossAccessEvaluator;

    @Resource
    private OssQuotaService ossQuotaService;

    @Resource
    private OssObjectRefService ossObjectRefService;

    @Resource
    private OssThumbnailService ossThumbnailService;

    @Resource
    private OssUploadProfileRepository ossUploadProfileRepository;

    @Override
    public PageBean<OssObjectDTO> findPage(OssObjectQueryDTO queryDTO, FlowHostDataScope scope,
                                           FlowHostPrincipal principal) {
        Pageable pageable = PageRequest.of(
                queryDTO.getPage(), queryDTO.getSize(),
                Sort.by(Sort.Direction.DESC, "createTime")
        );
        Specification<OssObjectDO> scopeSpec = OssDataScopeSpecification.withScope(scope, principal);
        LocalDateTime now = LocalDateTime.now(ZONE_SH);
        Specification<OssObjectDO> querySpec = (root, cq, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (StrUtil.isNotBlank(queryDTO.getProfileCode())) {
                predicates.add(cb.equal(root.get("profileCode"), queryDTO.getProfileCode()));
            }
            if (StrUtil.isNotBlank(queryDTO.getOriginalName())) {
                predicates.add(cb.like(root.get("originalName"), "%" + queryDTO.getOriginalName() + "%"));
            }
            if (StrUtil.isNotBlank(queryDTO.getUploadedBy())) {
                predicates.add(cb.equal(root.get("uploadedBy"), queryDTO.getUploadedBy()));
            }
            if (StrUtil.isNotBlank(queryDTO.getVisibility())) {
                predicates.add(cb.equal(root.get("visibility"), queryDTO.getVisibility().trim().toUpperCase()));
            }
            if (StrUtil.isNotBlank(queryDTO.getStatus())) {
                predicates.add(cb.equal(root.get("status"), queryDTO.getStatus().trim().toUpperCase()));
            }
            if (Boolean.TRUE.equals(queryDTO.getExpiredOnly())) {
                predicates.add(cb.isNotNull(root.get("expiresAt")));
                predicates.add(cb.lessThan(root.get("expiresAt"), now));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
        Specification<OssObjectDO> spec = scopeSpec.and(querySpec);
        Page<OssObjectDO> page = ossObjectRepository.findAll(spec, pageable);
        List<OssObjectDTO> items = page.getContent().stream()
                .map(OssObjectDTO::fromDO)
                .collect(Collectors.toList());
        return new PageBean<>(items, page.getNumber(), page.getSize(),
                page.getTotalPages(), page.getTotalElements());
    }

    @Override
    public OssObjectDTO findById(String id, FlowHostDataScope scope, FlowHostPrincipal principal) {
        OssObjectDO object = ossObjectRepository.findById(id).orElse(null);
        if (object == null || !OssObjectDO.STATUS_ACTIVE.equals(object.getStatus())) {
            return null;
        }
        if (!ossAccessEvaluator.canAccess(object, scope, principal, FlowOssObjectAccessVoter.ACTION_VIEW)) {
            throw new FlowException("RBAC_FORBIDDEN", "无权查看该文件");
        }
        return OssObjectDTO.fromDO(object);
    }

    @Override
    @Transactional
    public void delete(String id, boolean force, FlowHostDataScope scope, FlowHostPrincipal principal) {
        OssObjectDO object = ossObjectRepository.findById(id).orElse(null);
        if (object == null || !OssObjectDO.STATUS_ACTIVE.equals(object.getStatus())) {
            throw new FlowException("OSS_OBJECT_NOT_FOUND", "文件不存在: " + id);
        }
        if (!canDelete(object, scope, principal)) {
            throw new FlowException("RBAC_FORBIDDEN", "无权删除该文件");
        }
        long refCount = ossObjectRefService.countByObjectId(id);
        if (refCount > 0 && !force) {
            throw new FlowException("OSS_OBJECT_IN_USE",
                    "文件仍被 " + refCount + " 个业务引用占用，请先解绑或使用 force=true 强制删除");
        }
        demoModeGuard.checkOssWrite(object.getBucket());
        object.setStatus(OssObjectDO.STATUS_DELETED);
        object.setObjectPurged(false);
        object.setUpdateTime(LocalDateTime.now(ZONE_SH));
        ossObjectRepository.save(object);
    }

    @Override
    @Transactional
    public List<OssUploadResultDTO> upload(String profileCode, MultipartFile[] files, Map<String, String> bizFields,
                                           FlowHostPrincipal principal, HttpServletRequest request,
                                           OssUploadOptions options) {
        OssUploadProfileDO profile = ossUploadProfileService.requireByCode(profileCode);
        if (profile.getEnabled() == null || !profile.getEnabled()) {
            throw new FlowException("OSS_PROFILE_DISABLED", "上传场景已停用: " + profileCode);
        }
        boolean requireAuth = profile.getRequireAuth() == null || profile.getRequireAuth();
        if (requireAuth && principal == null) {
            throw new FlowException("RBAC_UNAUTHORIZED", "未登录或凭证无效");
        }
        if (principal != null && StrUtil.isNotBlank(profile.getUploadPerm())
                && !isOpenPrincipal(principal)) {
            if (!rbacService.hasAnyPerm(principal.getUsername(), profile.getUploadPerm(), "*")) {
                throw new FlowException("RBAC_FORBIDDEN", "无上传权限: " + profile.getUploadPerm());
            }
        }

        if (files == null || files.length == 0) {
            throw new FlowException("OSS_FILE_REQUIRED", "请选择上传文件");
        }
        int maxFiles = profile.getMaxFilesPerRequest() != null ? profile.getMaxFilesPerRequest() : 1;
        if (files.length > maxFiles) {
            throw new FlowException("OSS_TOO_MANY_FILES", "单次最多上传 " + maxFiles + " 个文件");
        }

        OssConnectionDO connection = ossConnectionRepository.findByCode(profile.getConnectionCode())
                .orElseThrow(() -> new FlowException("OSS_CONNECTION_NOT_FOUND",
                        "OSS 连接不存在: " + profile.getConnectionCode()));
        if (connection.getEnabled() == null || !connection.getEnabled()) {
            throw new FlowException("OSS_CONNECTION_DISABLED", "OSS 连接已停用");
        }

        String bucket = resolveBucket(profile, connection);
        demoModeGuard.checkOssWrite(bucket);

        if (options == null) {
            options = OssUploadRequestParser.parseOptions(request, bizFields);
        }
        applyAdminUploadOverrides(principal, options, bizFields);

        String bizMeta = OssBizFieldValidator.validateAndSerialize(profile.getBizFieldsSchema(), bizFields);
        if (options.isOverwrite()) {
            softDeleteExistingByBizId(profile.getCode(), bizMeta);
        }

        LocalDateTime expiresAt = OssUploadRequestParser.resolveExpiresAt(options);
        String uploadedBy = principal != null ? principal.getUserId() : null;
        ossQuotaService.checkBeforeUpload(profile, uploadedBy, 0, files.length);
        List<OssUploadResultDTO> results = new ArrayList<>();
        for (MultipartFile file : files) {
            results.add(doUploadOne(file, profile, connection, bucket, bizMeta, principal, options, expiresAt));
        }
        return results;
    }

    @Override
    public void downloadOrPresign(String id, FlowHostPrincipal principal, FlowHostDataScope scope,
                                  HttpServletRequest request, HttpServletResponse response) {
        long start = System.currentTimeMillis();
        OssObjectDO object = loadActivePrivateObject(id, principal, scope, request, start);
        boolean forceStream = "true".equalsIgnoreCase(request.getParameter("stream"))
                || "1".equals(request.getParameter("stream"));
        String mode = resolvePrivateDownloadMode(object.getConnectionCode());
        if (!forceStream && OssConnectionDO.PRIVATE_DOWNLOAD_PRESIGN.equals(mode)) {
            int expireSeconds = resolvePresignExpireSeconds(object.getConnectionCode());
            String url = ossStorageService.presignGetUrl(
                    object.getConnectionCode(), object.getBucket(), object.getObjectKey(), expireSeconds);
            ossDownloadLogService.writeLog(id, principal, request, object.getVisibility(),
                    OssDownloadLogDO.RESULT_SUCCESS, null, System.currentTimeMillis() - start);
            if (wantsJsonResponse(request)) {
                try {
                    writePresignJson(response, url, expireSeconds);
                } catch (java.io.IOException e) {
                    throw new FlowException("OSS_PRESIGN_WRITE_FAILED", "写入预签名响应失败: " + e.getMessage(), e);
                }
            } else {
                response.setStatus(HttpServletResponse.SC_FOUND);
                response.setHeader("Location", url);
            }
            return;
        }
        streamObjectContent(object, id, principal, request, response, start);
    }

    @Override
    public OssPresignUrlDTO presignUrl(String id, FlowHostPrincipal principal, FlowHostDataScope scope,
                                       HttpServletRequest request) {
        long start = System.currentTimeMillis();
        OssObjectDO object = loadActivePrivateObject(id, principal, scope, request, start);
        int expireSeconds = resolvePresignExpireSeconds(object.getConnectionCode());
        String url = ossStorageService.presignGetUrl(
                object.getConnectionCode(), object.getBucket(), object.getObjectKey(), expireSeconds);
        ossDownloadLogService.writeLog(id, principal, request, object.getVisibility(),
                OssDownloadLogDO.RESULT_SUCCESS, null, System.currentTimeMillis() - start);
        return new OssPresignUrlDTO().setUrl(url).setExpireSeconds(expireSeconds);
    }

    @Override
    public void packDownload(List<String> ids, FlowHostPrincipal principal, FlowHostDataScope scope,
                           HttpServletRequest request, HttpServletResponse response) {
        if (ids == null || ids.isEmpty()) {
            throw new FlowException("OSS_PACK_EMPTY", "请选择要打包的文件");
        }
        int maxFiles = yuFlowProperties.getOss().getPackMaxFiles();
        if (maxFiles > 0 && ids.size() > maxFiles) {
            throw new FlowException("OSS_PACK_TOO_MANY", "打包文件数不能超过 " + maxFiles);
        }

        List<OssObjectDO> objects = new ArrayList<>();
        long totalBytes = 0;
        for (String id : ids) {
            OssObjectDO object = ossObjectRepository.findById(id).orElse(null);
            if (object == null || !OssObjectDO.STATUS_ACTIVE.equals(object.getStatus())) {
                throw new FlowException("OSS_OBJECT_NOT_FOUND", "文件不存在: " + id);
            }
            if (!OssObjectDO.VISIBILITY_PRIVATE.equals(object.getVisibility())) {
                throw new FlowException("OSS_PACK_PRIVATE_ONLY", "打包下载仅支持私有文件: " + id);
            }
            if (!ossAccessEvaluator.canAccess(object, scope, principal, FlowOssObjectAccessVoter.ACTION_DOWNLOAD)) {
                throw new FlowException("RBAC_FORBIDDEN", "无权下载文件: " + id);
            }
            long size = object.getSizeBytes() != null ? object.getSizeBytes() : 0;
            totalBytes += size;
            objects.add(object);
        }

        long maxBytes = yuFlowProperties.getOss().getPackMaxBytes();
        if (maxBytes > 0 && totalBytes > maxBytes) {
            throw new FlowException("OSS_PACK_TOO_LARGE", "打包总大小不能超过 " + maxBytes + " 字节");
        }

        response.setContentType("application/zip");
        response.setHeader("Content-Disposition", "attachment; filename=\"files.zip\"");
        Set<String> usedNames = new HashSet<>();
        try (ZipOutputStream zos = new ZipOutputStream(response.getOutputStream())) {
            for (OssObjectDO object : objects) {
                long start = System.currentTimeMillis();
                String entryName = OssZipEntryNameSanitizer.sanitize(object.getOriginalName(), usedNames);
                zos.putNextEntry(new ZipEntry(entryName));
                try (GetObjectResponse obj = ossStorageService.getObject(
                        object.getConnectionCode(), object.getBucket(), object.getObjectKey())) {
                    obj.transferTo(zos);
                }
                zos.closeEntry();
                ossDownloadLogService.writeLog(object.getId(), principal, request, object.getVisibility(),
                        OssDownloadLogDO.RESULT_SUCCESS, null, System.currentTimeMillis() - start);
            }
            zos.finish();
            response.flushBuffer();
        } catch (FlowException e) {
            throw e;
        } catch (Exception e) {
            throw new FlowException("OSS_PACK_FAILED", "打包下载失败: " + e.getMessage(), e);
        }
    }

    private OssObjectDO loadActivePrivateObject(String id, FlowHostPrincipal principal, FlowHostDataScope scope,
                                                HttpServletRequest request, long start) {
        OssObjectDO object = ossObjectRepository.findById(id).orElse(null);
        if (object == null || !OssObjectDO.STATUS_ACTIVE.equals(object.getStatus())) {
            ossDownloadLogService.writeLog(id, principal, request, "PRIVATE",
                    OssDownloadLogDO.RESULT_NOT_FOUND, "文件不存在", System.currentTimeMillis() - start);
            throw new FlowException("OSS_OBJECT_NOT_FOUND", "文件不存在: " + id);
        }
        // 公有文件无访问限制，直接通过；私有文件才需要检查 DataScope / downloadPerm
        if (!OssObjectDO.VISIBILITY_PUBLIC.equals(object.getVisibility())) {
            if (!ossAccessEvaluator.canAccess(object, scope, principal, FlowOssObjectAccessVoter.ACTION_DOWNLOAD)) {
                // DataScope 不通过时，尝试用 downloadPerm 权限码二次放行（适合管理员/客服角色跨范围访问）
                OssUploadProfileDO profile = ossUploadProfileRepository.findByCode(object.getProfileCode()).orElse(null);
                boolean grantedByPerm = profile != null
                        && StrUtil.isNotBlank(profile.getDownloadPerm())
                        && principal != null
                        && !isOpenPrincipal(principal)
                        && rbacService.hasAnyPerm(principal.getUsername(), profile.getDownloadPerm(), "*");
                if (!grantedByPerm) {
                    ossDownloadLogService.writeLog(id, principal, request, object.getVisibility(),
                            OssDownloadLogDO.RESULT_DENIED, "数据权限不足", System.currentTimeMillis() - start);
                    throw new FlowException("RBAC_FORBIDDEN", "无权下载该文件");
                }
            }
        }
        return object;
    }

    private void streamObjectContent(OssObjectDO object, String id, FlowHostPrincipal principal,
                                     HttpServletRequest request, HttpServletResponse response, long start) {
        try (GetObjectResponse obj = ossStorageService.getObject(
                object.getConnectionCode(), object.getBucket(), object.getObjectKey())) {
            response.setContentType(StrUtil.blankToDefault(object.getContentType(), "application/octet-stream"));
            String filename = StrUtil.blankToDefault(object.getOriginalName(), "download");
            response.setHeader("Content-Disposition", "attachment; filename=\"" + filename.replace("\"", "") + "\"");
            if (object.getSizeBytes() != null && object.getSizeBytes() > 0) {
                response.setContentLengthLong(object.getSizeBytes());
            }
            try (InputStream in = obj; OutputStream out = response.getOutputStream()) {
                in.transferTo(out);
                out.flush();
            }
            ossDownloadLogService.writeLog(id, principal, request, object.getVisibility(),
                    OssDownloadLogDO.RESULT_SUCCESS, null, System.currentTimeMillis() - start);
        } catch (FlowException e) {
            ossDownloadLogService.writeLog(id, principal, request, object.getVisibility(),
                    OssDownloadLogDO.RESULT_ERROR, e.getMessage(), System.currentTimeMillis() - start);
            throw e;
        } catch (Exception e) {
            ossDownloadLogService.writeLog(id, principal, request, object.getVisibility(),
                    OssDownloadLogDO.RESULT_ERROR, e.getMessage(), System.currentTimeMillis() - start);
            throw new FlowException("OSS_DOWNLOAD_FAILED", "下载失败: " + e.getMessage(), e);
        }
    }

    private static void writePresignJson(HttpServletResponse response, String url, int expireSeconds)
            throws java.io.IOException {
        response.setStatus(HttpServletResponse.SC_OK);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        String body = "{\"url\":\"" + url.replace("\\", "\\\\").replace("\"", "\\\"")
                + "\",\"expireSeconds\":" + expireSeconds + "}";
        response.getOutputStream().write(body.getBytes(StandardCharsets.UTF_8));
        response.flushBuffer();
    }

    private static boolean wantsJsonResponse(HttpServletRequest request) {
        if (request == null) {
            return false;
        }
        if ("json".equalsIgnoreCase(request.getParameter("format"))) {
            return true;
        }
        String accept = request.getHeader("Accept");
        return accept != null && accept.contains(MediaType.APPLICATION_JSON_VALUE);
    }

    private String resolvePrivateDownloadMode(String connectionCode) {
        OssConnectionDO connection = ossConnectionRepository.findByCode(connectionCode).orElse(null);
        if (connection != null && StrUtil.isNotBlank(connection.getPrivateDownloadMode())) {
            return connection.getPrivateDownloadMode().trim().toUpperCase(Locale.ROOT);
        }
        return StrUtil.blankToDefault(yuFlowProperties.getOss().getPrivateDownloadMode(),
                OssConnectionDO.PRIVATE_DOWNLOAD_STREAM).toUpperCase(Locale.ROOT);
    }

    private int resolvePresignExpireSeconds(String connectionCode) {
        OssConnectionDO connection = ossConnectionRepository.findByCode(connectionCode).orElse(null);
        if (connection != null && connection.getPresignExpireSeconds() != null
                && connection.getPresignExpireSeconds() > 0) {
            return connection.getPresignExpireSeconds();
        }
        int global = yuFlowProperties.getOss().getPresignExpireSeconds();
        return global > 0 ? global : 300;
    }

    private OssUploadResultDTO doUploadOne(MultipartFile file, OssUploadProfileDO profile,
                                           OssConnectionDO connection, String bucket, String bizMeta,
                                           FlowHostPrincipal principal, OssUploadOptions options,
                                           LocalDateTime expiresAt) {
        if (file == null || file.isEmpty()) {
            throw new FlowException("OSS_FILE_EMPTY", "上传文件为空");
        }
        String originalName = StrUtil.blankToDefault(file.getOriginalFilename(), "file");
        String extension = extractExtension(originalName);
        validateExtension(extension, profile.getAllowedExtensions());
        String contentType = StrUtil.blankToDefault(file.getContentType(), "application/octet-stream");
        validateContentType(contentType, profile.getAllowedContentTypes());

        long size = file.getSize();
        long globalMax = yuFlowProperties.getOss().getMaxUploadBytes();
        if (globalMax > 0 && size > globalMax) {
            throw new FlowException("OSS_FILE_TOO_LARGE", "文件大小超出全局上限 " + globalMax + " 字节");
        }
        if (profile.getMaxSizeBytes() != null && profile.getMaxSizeBytes() > 0 && size > profile.getMaxSizeBytes()) {
            throw new FlowException("OSS_FILE_TOO_LARGE", "文件大小超出场景上限 " + profile.getMaxSizeBytes() + " 字节");
        }

        String uploadedBy = principal != null ? principal.getUserId() : null;
        String uploadedByName = principal != null ? principal.getUsername() : null;
        String deptId = principal != null ? principal.getDeptId() : null;
        if (options != null && StrUtil.isNotBlank(options.getUploadedByOverride())) {
            uploadedBy = options.getUploadedByOverride();
            uploadedByName = StrUtil.blankToDefault(options.getUploadedByNameOverride(), uploadedBy);
        }
        ossQuotaService.checkBeforeUpload(profile, uploadedBy, size, 1);

        String objectKey;
        try {
            objectKey = OssKeyPatternResolver.resolve(
                    profile.getKeyPattern(), profile.getCode(), originalName, connection.getKeyPrefix());
        } catch (IllegalArgumentException e) {
            throw new FlowException("OSS_KEY_INVALID", e.getMessage());
        }

        String checksum;
        try (InputStream raw = file.getInputStream()) {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            DigestInputStream din = new DigestInputStream(raw, digest);
            ossStorageService.putObject(connection.getCode(), din, size, contentType, bucket, objectKey);
            checksum = HexFormat.of().formatHex(digest.digest());
        } catch (FlowException e) {
            throw e;
        } catch (Exception e) {
            throw new FlowException("OSS_UPLOAD_FAILED", "上传失败: " + e.getMessage(), e);
        }

        String visibility = profile.getVisibility();
        String publicPath = OssObjectDO.VISIBILITY_PUBLIC.equals(visibility)
                ? OssKeyPatternResolver.buildPublicPath(objectKey) : null;

        LocalDateTime now = LocalDateTime.now(ZONE_SH);

        OssObjectDO entity = OssObjectDO.builder()
                .profileCode(profile.getCode())
                .connectionCode(connection.getCode())
                .bucket(bucket)
                .objectKey(objectKey)
                .visibility(visibility)
                .publicPath(publicPath)
                .originalName(originalName)
                .contentType(contentType)
                .extension(extension)
                .sizeBytes(size)
                .checksumSha256(checksum)
                .bizMeta(bizMeta)
                .uploadedBy(uploadedBy)
                .uploadedByName(uploadedByName)
                .deptId(deptId)
                .status(OssObjectDO.STATUS_ACTIVE)
                .expiresAt(expiresAt)
                .objectPurged(false)
                .thumbStatus(OssThumbnailService.THUMB_NONE)
                .createTime(now)
                .updateTime(now)
                .build();
        entity = ossObjectRepository.save(entity);
        ossThumbnailService.scheduleIfNeeded(entity, profile);

        OssUploadResultDTO dto = new OssUploadResultDTO()
                .setId(entity.getId())
                .setVisibility(visibility)
                .setOriginalName(originalName)
                .setSizeBytes(size)
                .setContentType(contentType)
                .setPublicPath(publicPath)
                .setExpiresAt(expiresAt)
                .setThumbStatus(entity.getThumbStatus())
                .setThumbPublicPath(entity.getThumbPublicPath())
                .setHasThumbnail(OssThumbnailService.THUMB_READY.equals(entity.getThumbStatus()));
        if (OssObjectDO.VISIBILITY_PUBLIC.equals(visibility)) {
            dto.setPublicUrl(OssKeyPatternResolver.buildPublicUrl(connection.getPublicBaseUrl(), publicPath));
        }
        return dto;
    }

    private void applyAdminUploadOverrides(FlowHostPrincipal principal, OssUploadOptions options,
                                           Map<String, String> bizFields) {
        if (options == null || StrUtil.isBlank(options.getUploadedByOverride())) {
            return;
        }
        if (principal == null || !rbacService.hasAnyPerm(principal.getUsername(), "flow:oss:admin", "*")) {
            throw new FlowException("RBAC_FORBIDDEN", "代传 uploadedBy 需要 flow:oss:admin 权限");
        }
        if (bizFields != null && principal.getUsername() != null) {
            bizFields.put("_operator", principal.getUsername());
        }
    }

    private void softDeleteExistingByBizId(String profileCode, String bizMeta) {
        String bizId = extractBizId(bizMeta);
        if (StrUtil.isBlank(bizId)) {
            return;
        }
        List<OssObjectDO> existing = ossObjectRepository.findByProfileCodeAndStatus(
                profileCode, OssObjectDO.STATUS_ACTIVE);
        LocalDateTime now = LocalDateTime.now(ZONE_SH);
        for (OssObjectDO object : existing) {
            if (bizId.equals(extractBizId(object.getBizMeta()))) {
                object.setStatus(OssObjectDO.STATUS_DELETED);
                object.setObjectPurged(false);
                object.setUpdateTime(now);
                ossObjectRepository.save(object);
            }
        }
    }

    private static String extractBizId(String bizMeta) {
        if (StrUtil.isBlank(bizMeta)) {
            return null;
        }
        try {
            Map<String, Object> map = MAPPER.readValue(bizMeta, new TypeReference<>() {});
            Object bizId = map.get("bizId");
            return bizId != null ? bizId.toString() : null;
        } catch (Exception e) {
            return null;
        }
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
        boolean ok = Arrays.stream(allowedExtensions.split(","))
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
        String ct = contentType.toLowerCase(Locale.ROOT);
        boolean ok = Arrays.stream(allowedContentTypes.split(","))
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

    private boolean canDelete(OssObjectDO object, FlowHostDataScope scope, FlowHostPrincipal principal) {
        return ossAccessEvaluator.canAccess(object, scope, principal, FlowOssObjectAccessVoter.ACTION_DELETE);
    }

    private static boolean isOpenPrincipal(FlowHostPrincipal principal) {
        return principal != null && StrUtil.isNotBlank(principal.getUserId())
                && principal.getUserId().startsWith("open:");
    }

    // ── 注解解析与 Caffeine 本地缓存（支持带/不带 IP:Port 全路径或相对路径） ──

    @Override
    public String resolveAccessUrl(String fileId, String profileCode, boolean absolute) {
        if (StrUtil.isBlank(fileId)) {
            return null;
        }
        String cacheKey = fileId.trim() + ":" + absolute;
        String cached = fileUrlCache.getIfPresent(cacheKey);
        if (cached != null) {
            return cached;
        }

        OssObjectDO object = ossObjectRepository.findById(fileId.trim()).orElse(null);
        if (object == null || !OssObjectDO.STATUS_ACTIVE.equals(object.getStatus())) {
            return fileId;
        }

        String url = buildResolvedUrl(object, absolute);
        if (url != null) {
            fileUrlCache.put(cacheKey, url);
        }
        return url != null ? url : fileId;
    }

    @Override
    public FlowOssFileResolvedDTO resolveFileDetail(String fileId, String profileCode, boolean absolute) {
        if (StrUtil.isBlank(fileId)) {
            return null;
        }
        String cacheKey = fileId.trim() + ":" + absolute;
        FlowOssFileResolvedDTO cached = fileDetailCache.getIfPresent(cacheKey);
        if (cached != null) {
            return cached;
        }

        OssObjectDO object = ossObjectRepository.findById(fileId.trim()).orElse(null);
        if (object == null || !OssObjectDO.STATUS_ACTIVE.equals(object.getStatus())) {
            return null;
        }

        String url = buildResolvedUrl(object, absolute);
        FlowOssFileResolvedDTO dto = FlowOssFileResolvedDTO.builder()
                .id(object.getId())
                .originalName(object.getOriginalName())
                .sizeBytes(object.getSizeBytes())
                .contentType(object.getContentType())
                .extension(object.getExtension())
                .bucket(object.getBucket())
                .visibility(object.getVisibility())
                .url(url)
                .build();

        fileDetailCache.put(cacheKey, dto);
        return dto;
    }

    @Override
    public void batchPreloadUrls(Collection<String> fileIds, boolean absolute) {
        if (fileIds == null || fileIds.isEmpty()) {
            return;
        }
        List<String> uncached = fileIds.stream()
                .filter(StrUtil::isNotBlank)
                .map(String::trim)
                .distinct()
                .filter(id -> fileUrlCache.getIfPresent(id + ":" + absolute) == null)
                .collect(Collectors.toList());

        if (uncached.isEmpty()) {
            return;
        }

        List<OssObjectDO> objects = ossObjectRepository.findAllById(uncached);
        for (OssObjectDO obj : objects) {
            if (OssObjectDO.STATUS_ACTIVE.equals(obj.getStatus())) {
                String url = buildResolvedUrl(obj, absolute);
                if (url != null) {
                    fileUrlCache.put(obj.getId() + ":" + absolute, url);
                }
            }
        }
    }

    @Override
    public void batchPreloadDetails(Collection<String> fileIds, boolean absolute) {
        if (fileIds == null || fileIds.isEmpty()) {
            return;
        }
        List<String> uncached = fileIds.stream()
                .filter(StrUtil::isNotBlank)
                .map(String::trim)
                .distinct()
                .filter(id -> fileDetailCache.getIfPresent(id + ":" + absolute) == null)
                .collect(Collectors.toList());

        if (uncached.isEmpty()) {
            return;
        }

        List<OssObjectDO> objects = ossObjectRepository.findAllById(uncached);
        for (OssObjectDO obj : objects) {
            if (OssObjectDO.STATUS_ACTIVE.equals(obj.getStatus())) {
                String url = buildResolvedUrl(obj, absolute);
                FlowOssFileResolvedDTO dto = FlowOssFileResolvedDTO.builder()
                        .id(obj.getId())
                        .originalName(obj.getOriginalName())
                        .sizeBytes(obj.getSizeBytes())
                        .contentType(obj.getContentType())
                        .extension(obj.getExtension())
                        .bucket(obj.getBucket())
                        .visibility(obj.getVisibility())
                        .url(url)
                        .build();
                fileDetailCache.put(obj.getId() + ":" + absolute, dto);
            }
        }
    }

    private String buildResolvedUrl(OssObjectDO object, boolean absolute) {
        if (object == null) {
            return null;
        }
        String downloadPath = "/flow-api/oss/objects/" + object.getId() + "/content";

        if (OssObjectDO.VISIBILITY_PUBLIC.equals(object.getVisibility())) {
            OssConnectionDO conn = ossConnectionRepository.findByCode(object.getConnectionCode()).orElse(null);
            String publicBaseUrl = conn != null ? conn.getPublicBaseUrl() : null;

            if (absolute) {
                if (StrUtil.isNotBlank(publicBaseUrl)) {
                    return OssKeyPatternResolver.buildPublicUrl(publicBaseUrl, object.getPublicPath());
                }
                return buildAbsoluteUrl(downloadPath);
            } else {
                return StrUtil.isNotBlank(object.getPublicPath()) ? object.getPublicPath() : downloadPath;
            }
        } else {
            return absolute ? buildAbsoluteUrl(downloadPath) : downloadPath;
        }
    }

    private String buildAbsoluteUrl(String path) {
        if (StrUtil.isBlank(path)) {
            return path;
        }
        String p = path.startsWith("/") ? path : "/" + path;
        String base = null;
        try {
            org.springframework.web.context.request.ServletRequestAttributes attrs =
                    (org.springframework.web.context.request.ServletRequestAttributes)
                            org.springframework.web.context.request.RequestContextHolder.getRequestAttributes();
            if (attrs != null) {
                HttpServletRequest req = attrs.getRequest();
                String scheme = req.getScheme();
                String serverName = req.getServerName();
                int port = req.getServerPort();
                if (("http".equalsIgnoreCase(scheme) && port == 80) || ("https".equalsIgnoreCase(scheme) && port == 443)) {
                    base = scheme + "://" + serverName;
                } else {
                    base = scheme + "://" + serverName + ":" + port;
                }
            }
        } catch (Exception ignored) {
        }
        if (StrUtil.isBlank(base)) {
            return p;
        }
        base = base.trim();
        while (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        return base + p;
    }
}
