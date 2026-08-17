package org.yu.flow.module.oss.service.multipart;

import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
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
import org.yu.flow.module.oss.dto.OssMultipartInitResultDTO;
import org.yu.flow.module.oss.dto.OssUploadOptions;
import org.yu.flow.module.oss.dto.OssUploadResultDTO;
import org.yu.flow.module.oss.repository.OssConnectionRepository;
import org.yu.flow.module.oss.repository.OssObjectRepository;
import org.yu.flow.module.oss.service.OssQuotaService;
import org.yu.flow.module.oss.service.OssThumbnailService;
import org.yu.flow.module.oss.service.OssUploadProfileService;
import org.yu.flow.module.oss.support.OssBizFieldValidator;
import org.yu.flow.module.oss.support.OssKeyPatternResolver;
import org.yu.flow.module.oss.support.OssProfileCallerAuth;
import org.yu.flow.module.oss.support.OssUploaderIdentity;
import org.yu.flow.module.oss.support.OssUploadRequestParser;

import java.io.InputStream;
import java.io.OutputStream;
import java.io.SequenceInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import org.yu.flow.module.oss.config.ConditionalOnOssEnabled;

/**
 * 网关侧分片上传：分片落盘 → complete 合并 putObject（MinIO SDK 对大文件自动 multipart）。
 */
@Slf4j
@ConditionalOnOssEnabled
@Service
public class OssMultipartUploadService {

    private static final ZoneId ZONE_SH = ZoneId.of("Asia/Shanghai");

    private final ConcurrentHashMap<String, OssMultipartUploadSession> sessions = new ConcurrentHashMap<>();

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
    private OssProfileCallerAuth ossProfileCallerAuth;

    @Resource
    private OssQuotaService ossQuotaService;

    @Resource
    private OssThumbnailService ossThumbnailService;

    private ScheduledExecutorService cleanupScheduler;
    private Path rootTempDir;

    @PostConstruct
    public void init() throws Exception {
        rootTempDir = Path.of(System.getProperty("java.io.tmpdir"), "yu-flow-oss-multipart");
        Files.createDirectories(rootTempDir);
        cleanupScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "oss-multipart-cleanup");
            t.setDaemon(true);
            return t;
        });
        cleanupScheduler.scheduleAtFixedRate(this::evictExpiredSessions, 5, 5, TimeUnit.MINUTES);
    }

    @PreDestroy
    public void destroy() {
        if (cleanupScheduler != null && !cleanupScheduler.isShutdown()) {
            cleanupScheduler.shutdown();
        }
        sessions.keySet().forEach(this::abortQuietly);
    }

    public OssMultipartInitResultDTO init(String profileCode, String originalName, String contentType,
                                          Map<String, String> bizFields, FlowHostPrincipal principal,
                                          HttpServletRequest request) {
        OssUploadProfileDO profile = ossUploadProfileService.requireByCode(profileCode);
        validateUploadAuth(profile, principal);
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
        validateExtension(extractExtension(originalName), profile.getAllowedExtensions());
        validateContentType(ct, profile.getAllowedContentTypes());

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
        ossQuotaService.checkBeforeUpload(profile, uploader.getUploadedBy(), uploader.getUploadedByUserType(), 0, 1);

        String sessionId = IdUtil.fastSimpleUUID();
        Path workDir;
        try {
            workDir = rootTempDir.resolve(sessionId);
            Files.createDirectories(workDir);
        } catch (Exception e) {
            throw new FlowException("OSS_MULTIPART_INIT_FAILED", "创建分片临时目录失败: " + e.getMessage(), e);
        }

        OssMultipartUploadSession session = new OssMultipartUploadSession();
        session.setSessionId(sessionId);
        session.setConnectionCode(connection.getCode());
        session.setBucket(bucket);
        session.setObjectKey(objectKey);
        session.setProfileCode(profile.getCode());
        session.setOwnerId(principal != null ? principal.getUserId() : null);
        session.setOwnerAuthChannel(principal != null ? principal.getAuthChannel() : null);
        session.setContentType(ct);
        session.setOriginalName(originalName);
        session.setBizMeta(bizMeta);
        session.setExpiresAt(expiresAt);
        session.setCreatedAt(LocalDateTime.now(ZONE_SH));
        session.setWorkDir(workDir);
        applyPrincipal(session, principal, options);

        sessions.put(sessionId, session);
        return new OssMultipartInitResultDTO()
                .setUploadId(sessionId)
                .setBucket(bucket)
                .setObjectKey(objectKey);
    }

    public void uploadPart(String sessionId, int partNumber, InputStream body, long size,
                           FlowHostPrincipal principal) {
        if (partNumber < 1 || partNumber > 10000) {
            throw new FlowException("OSS_MULTIPART_PART_INVALID", "partNumber 非法: " + partNumber);
        }
        OssMultipartUploadSession session = requireSession(sessionId);
        assertSessionOwner(session, principal);
        demoModeGuard.checkOssWrite(session.getBucket());

        long globalMax = yuFlowProperties.getOss().getMaxUploadBytes();
        if (globalMax > 0 && size > globalMax) {
            throw new FlowException("OSS_FILE_TOO_LARGE", "分片大小超出全局上限");
        }

        Path partFile = session.getWorkDir().resolve("part-" + partNumber);
        try {
            session.getParts().stream()
                    .filter(p -> p.getPartNumber() == partNumber)
                    .findFirst()
                    .ifPresent(old -> {
                        try {
                            Files.deleteIfExists(old.getFilePath());
                        } catch (Exception ignored) {
                            // ignore
                        }
                    });
            session.getParts().removeIf(p -> p.getPartNumber() == partNumber);

            try (OutputStream out = Files.newOutputStream(partFile,
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE)) {
                long copied = body.transferTo(out);
                if (size >= 0 && copied != size && size > 0) {
                    log.debug("[OSS] multipart part size mismatch declared={} actual={}", size, copied);
                    size = copied;
                } else if (size < 0) {
                    size = copied;
                }
            }

            OssMultipartUploadSession.PartRecord record = new OssMultipartUploadSession.PartRecord();
            record.setPartNumber(partNumber);
            record.setFilePath(partFile);
            record.setSizeBytes(size);
            session.getParts().add(record);
            session.setTotalSizeBytes(session.getParts().stream()
                    .mapToLong(OssMultipartUploadSession.PartRecord::getSizeBytes).sum());
        } catch (FlowException e) {
            throw e;
        } catch (Exception e) {
            throw new FlowException("OSS_MULTIPART_PART_FAILED", "上传分片失败: " + e.getMessage(), e);
        }
    }

    @Transactional
    public OssUploadResultDTO complete(String sessionId, FlowHostPrincipal principal, HttpServletRequest request) {
        OssMultipartUploadSession session = requireSession(sessionId);
        assertSessionOwner(session, principal);
        demoModeGuard.checkOssWrite(session.getBucket());

        if (session.getParts().isEmpty()) {
            throw new FlowException("OSS_MULTIPART_NO_PARTS", "尚未上传任何分片");
        }

        OssUploadProfileDO profile = ossUploadProfileService.requireByCode(session.getProfileCode());
        validateUploadAuth(profile, principal);
        OssConnectionDO connection = ossConnectionRepository.findByCode(session.getConnectionCode())
                .orElseThrow(() -> new FlowException("OSS_CONNECTION_NOT_FOUND", "连接不存在"));
        if (!Boolean.TRUE.equals(connection.getEnabled())) {
            throw new FlowException("OSS_CONNECTION_DISABLED", "OSS 连接已停用");
        }
        long totalSize = session.getTotalSizeBytes();
        if (profile.getMaxSizeBytes() != null && profile.getMaxSizeBytes() > 0 && totalSize > profile.getMaxSizeBytes()) {
            throw new FlowException("OSS_FILE_TOO_LARGE", "文件大小超出场景上限");
        }
        long globalMax = yuFlowProperties.getOss().getMaxUploadBytes();
        if (globalMax > 0 && totalSize > globalMax) {
            throw new FlowException("OSS_FILE_TOO_LARGE", "文件大小超出全局上限");
        }

        String uploadedBy = session.getUploadedBy();
        ossQuotaService.checkBeforeUpload(profile, uploadedBy, session.getUploadedByUserType(), totalSize, 1);

        List<OssMultipartUploadSession.PartRecord> ordered = session.getParts().stream()
                .sorted(Comparator.comparingInt(OssMultipartUploadSession.PartRecord::getPartNumber))
                .collect(Collectors.toList());

        try {
            List<InputStream> streams = new ArrayList<>();
            for (OssMultipartUploadSession.PartRecord part : ordered) {
                streams.add(Files.newInputStream(part.getFilePath()));
            }
            Enumeration<InputStream> enumeration = Collections.enumeration(streams);
            try (SequenceInputStream sequence = new SequenceInputStream(enumeration)) {
                ossStorageService.putObject(
                        session.getConnectionCode(),
                        sequence,
                        totalSize,
                        session.getContentType(),
                        session.getBucket(),
                        session.getObjectKey());
            } finally {
                for (InputStream in : streams) {
                    try {
                        in.close();
                    } catch (Exception ignored) {
                        // ignore
                    }
                }
            }
        } catch (FlowException e) {
            throw e;
        } catch (Exception e) {
            throw new FlowException("OSS_MULTIPART_COMPLETE_FAILED", "完成分片上传失败: " + e.getMessage(), e);
        }

        String visibility = profile.getVisibility();
        String publicPath = OssObjectDO.VISIBILITY_PUBLIC.equals(visibility)
                ? OssKeyPatternResolver.buildPublicPath(session.getObjectKey()) : null;
        LocalDateTime now = LocalDateTime.now(ZONE_SH);

        OssObjectDO entity = OssObjectDO.builder()
                .profileCode(session.getProfileCode())
                .connectionCode(session.getConnectionCode())
                .bucket(session.getBucket())
                .objectKey(session.getObjectKey())
                .visibility(visibility)
                .publicPath(publicPath)
                .originalName(session.getOriginalName())
                .contentType(session.getContentType())
                .extension(extractExtension(session.getOriginalName()))
                .sizeBytes(totalSize)
                .bizMeta(session.getBizMeta())
                .uploadedBy(session.getUploadedBy())
                .uploadedByUserType(session.getUploadedByUserType())
                .uploadedByName(session.getUploadedByName())
                .deptId(session.getDeptId())
                .status(OssObjectDO.STATUS_ACTIVE)
                .expiresAt(session.getExpiresAt())
                .objectPurged(false)
                .thumbStatus(OssThumbnailService.THUMB_NONE)
                .createTime(now)
                .updateTime(now)
                .build();
        entity = ossObjectRepository.save(entity);
        ossThumbnailService.scheduleIfNeeded(entity, profile);

        cleanupSessionFiles(session);
        sessions.remove(sessionId);

        OssUploadResultDTO dto = new OssUploadResultDTO()
                .setId(entity.getId())
                .setVisibility(visibility)
                .setOriginalName(session.getOriginalName())
                .setSizeBytes(totalSize)
                .setContentType(session.getContentType())
                .setPublicPath(publicPath)
                .setExpiresAt(session.getExpiresAt())
                .setThumbStatus(entity.getThumbStatus())
                .setThumbPublicPath(entity.getThumbPublicPath())
                .setHasThumbnail(OssThumbnailService.THUMB_READY.equals(entity.getThumbStatus()));
        if (OssObjectDO.VISIBILITY_PUBLIC.equals(visibility)) {
            dto.setPublicUrl(OssKeyPatternResolver.buildPublicUrl(connection.getPublicBaseUrl(), publicPath));
        }
        return dto;
    }

    public void abort(String sessionId, FlowHostPrincipal principal) {
        OssMultipartUploadSession session = requireSession(sessionId);
        assertSessionOwner(session, principal);
        removeSession(sessionId);
    }

    private void removeSession(String sessionId) {
        OssMultipartUploadSession session = sessions.remove(sessionId);
        if (session == null) {
            return;
        }
        cleanupSessionFiles(session);
    }

    private void abortQuietly(String sessionId) {
        try {
            removeSession(sessionId);
        } catch (Exception e) {
            log.debug("[OSS] abortQuietly {}: {}", sessionId, e.getMessage());
        }
    }

    private void cleanupSessionFiles(OssMultipartUploadSession session) {
        if (session == null || session.getWorkDir() == null) {
            return;
        }
        try {
            if (Files.isDirectory(session.getWorkDir())) {
                try (var walk = Files.walk(session.getWorkDir())) {
                    walk.sorted(Comparator.reverseOrder()).forEach(path -> {
                        try {
                            Files.deleteIfExists(path);
                        } catch (Exception ignored) {
                            // ignore
                        }
                    });
                }
            }
        } catch (Exception e) {
            log.warn("[OSS] cleanup multipart dir {}: {}", session.getWorkDir(), e.getMessage());
        }
    }

    private OssMultipartUploadSession requireSession(String sessionId) {
        OssMultipartUploadSession session = sessions.get(sessionId);
        if (session == null) {
            throw new FlowException("OSS_MULTIPART_SESSION_NOT_FOUND", "分片上传会话不存在或已过期: " + sessionId);
        }
        int ttlMinutes = yuFlowProperties.getOss().getMultipartSessionTtlMinutes();
        if (ttlMinutes > 0 && session.getCreatedAt().plusMinutes(ttlMinutes).isBefore(LocalDateTime.now(ZONE_SH))) {
            sessions.remove(sessionId);
            cleanupSessionFiles(session);
            throw new FlowException("OSS_MULTIPART_SESSION_EXPIRED", "分片上传会话已过期");
        }
        return session;
    }

    private void evictExpiredSessions() {
        int ttlMinutes = yuFlowProperties.getOss().getMultipartSessionTtlMinutes();
        if (ttlMinutes <= 0) {
            return;
        }
        LocalDateTime threshold = LocalDateTime.now(ZONE_SH).minusMinutes(ttlMinutes);
        List<String> expired = new ArrayList<>();
        sessions.forEach((id, session) -> {
            if (session.getCreatedAt() != null && session.getCreatedAt().isBefore(threshold)) {
                expired.add(id);
            }
        });
        expired.forEach(this::abortQuietly);
    }

    private void validateUploadAuth(OssUploadProfileDO profile, FlowHostPrincipal principal) {
        ossProfileCallerAuth.assertUpload(profile, principal);
    }

    private void applyPrincipal(OssMultipartUploadSession session, FlowHostPrincipal principal,
                                OssUploadOptions options) {
        if (options != null && StrUtil.isNotBlank(options.getUploadedByOverride())) {
            if (!ossProfileCallerAuth.hasFlowPermission(principal, "flow:oss:admin")) {
                throw new FlowException("RBAC_FORBIDDEN", "代传 uploadedBy 需要 flow:oss:admin 权限");
            }
        }
        OssUploaderIdentity.Snapshot uploader = OssUploaderIdentity.from(principal, options);
        session.setUploadedBy(uploader.getUploadedBy());
        session.setUploadedByUserType(uploader.getUploadedByUserType());
        session.setUploadedByName(uploader.getUploadedByName());
        if (principal != null && (options == null || StrUtil.isBlank(options.getUploadedByOverride()))) {
            session.setDeptId(uploader.getDeptId());
        }
    }

    private void assertSessionOwner(OssMultipartUploadSession session, FlowHostPrincipal principal) {
        if (session == null || StrUtil.isBlank(session.getOwnerId())) {
            // 匿名场景当前以高熵 uploadId 作为 capability；后续可升级为独立签名 token。
            return;
        }
        if (principal != null && session.getOwnerId().equals(principal.getUserId())) {
            return;
        }
        if (ossProfileCallerAuth.hasFlowPermission(principal, "flow:oss:admin")) {
            return;
        }
        throw new FlowException("OSS_MULTIPART_FORBIDDEN", "无权操作该分片上传会话");
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
        String ct = contentType.toLowerCase(Locale.ROOT);
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
