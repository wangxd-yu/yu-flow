package org.yu.flow.module.oss.service.impl;

import cn.hutool.core.util.StrUtil;
import io.minio.GetObjectResponse;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.yu.flow.config.DemoModeGuard;
import org.yu.flow.config.YuFlowProperties;
import org.yu.flow.exception.FlowException;
import org.yu.flow.module.oss.client.OssStorageService;
import org.yu.flow.module.oss.config.ConditionalOnOssEnabled;
import org.yu.flow.module.oss.domain.OssObjectDO;
import org.yu.flow.module.oss.domain.OssUploadProfileDO;
import org.yu.flow.module.oss.repository.OssObjectRepository;
import org.yu.flow.module.oss.repository.OssUploadProfileRepository;
import org.yu.flow.module.oss.service.OssArchiveExtractService;
import org.yu.flow.module.oss.service.OssQuotaService;
import org.yu.flow.module.oss.service.OssThumbnailService;
import org.yu.flow.module.oss.support.OssArchiveExtractSupport;
import org.yu.flow.module.oss.support.OssContentSniffer;
import org.yu.flow.module.oss.support.OssKeyPatternResolver;
import org.yu.flow.module.oss.support.OssLimitedInputStream;
import org.yu.flow.module.oss.support.OssLimitedInputStream.OssArchiveLimitException;
import org.yu.flow.module.oss.support.OssNonClosingInputStream;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.SequenceInputStream;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

@Slf4j
@ConditionalOnOssEnabled
@Service
public class OssArchiveExtractServiceImpl implements OssArchiveExtractService {

    private static final ZoneId ZONE_SH = ZoneId.of("Asia/Shanghai");
    private static final int PEEK_BYTES = 64;
    private static final long DEFAULT_MAX_UNCOMPRESSED = 512L * 1024 * 1024;
    private static final int DEFAULT_MAX_ENTRIES = 200;
    private static final double DEFAULT_MAX_RATIO = 100d;

    @Resource
    private OssObjectRepository ossObjectRepository;

    @Resource
    private OssUploadProfileRepository ossUploadProfileRepository;

    @Resource
    private OssStorageService ossStorageService;

    @Resource
    private OssQuotaService ossQuotaService;

    @Resource
    private OssThumbnailService ossThumbnailService;

    @Resource
    private DemoModeGuard demoModeGuard;

    @Resource
    private YuFlowProperties yuFlowProperties;

    @Resource
    private TransactionTemplate transactionTemplate;

    private ExecutorService executor;
    private final ConcurrentHashMap<String, Object> locks = new ConcurrentHashMap<>();

    @PostConstruct
    public void init() {
        int pool = 2;
        YuFlowProperties.Oss.Extract cfg = extractCfg();
        if (cfg != null && cfg.getPoolSize() > 0) {
            pool = Math.min(8, Math.max(1, cfg.getPoolSize()));
        }
        ThreadFactory factory = r -> {
            Thread t = new Thread(r, "oss-extract");
            t.setDaemon(true);
            return t;
        };
        executor = Executors.newFixedThreadPool(pool, factory);
    }

    @PreDestroy
    public void destroy() {
        if (executor != null && !executor.isShutdown()) {
            executor.shutdown();
        }
    }

    @Override
    @Transactional
    public void scheduleIfNeeded(OssObjectDO object, OssUploadProfileDO profile) {
        if (object == null || StrUtil.isBlank(object.getId())) {
            return;
        }
        if (StrUtil.isNotBlank(object.getParentObjectId())) {
            return;
        }
        if (!OssArchiveExtractSupport.isZipObject(object.getExtension(), object.getContentType())) {
            return;
        }
        String current = StrUtil.blankToDefault(object.getExtractStatus(), EXTRACT_NONE);
        if (EXTRACT_DONE.equals(current) || EXTRACT_PENDING.equals(current)
                || EXTRACT_EXTRACTING.equals(current) || EXTRACT_FAILED.equals(current)) {
            return;
        }
        String skip = evaluateSkipReason(object, profile);
        if (skip != null) {
            return;
        }
        object.setExtractStatus(EXTRACT_PENDING);
        object.setExtractError(null);
        object.setUpdateTime(LocalDateTime.now(ZONE_SH));
        ossObjectRepository.save(object);
        enqueueAfterCommit(object.getId());
    }

    private void enqueueAfterCommit(String objectId) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    executor.submit(() -> safeProcess(objectId));
                }
            });
        } else {
            executor.submit(() -> safeProcess(objectId));
        }
    }

    @Override
    public void process(String objectId) {
        Object lock = locks.computeIfAbsent(objectId, k -> new Object());
        synchronized (lock) {
            try {
                doProcess(objectId);
            } finally {
                locks.remove(objectId, lock);
            }
        }
    }

    private void safeProcess(String objectId) {
        try {
            process(objectId);
        } catch (Exception e) {
            log.warn("[OSS] archive extract async error id={}: {}", objectId, e.getMessage());
            markFailed(objectId, e.getMessage());
        }
    }

    private void doProcess(String objectId) {
        OssObjectDO object = ossObjectRepository.findById(objectId).orElse(null);
        if (object == null || !OssObjectDO.STATUS_ACTIVE.equals(object.getStatus())) {
            return;
        }
        String status = StrUtil.blankToDefault(object.getExtractStatus(), EXTRACT_NONE);
        if (EXTRACT_DONE.equals(status) || EXTRACT_FAILED.equals(status) || EXTRACT_SKIPPED.equals(status)
                || EXTRACT_NONE.equals(status)) {
            return;
        }
        OssUploadProfileDO profile = StrUtil.isNotBlank(object.getProfileCode())
                ? ossUploadProfileRepository.findByCode(object.getProfileCode()).orElse(null)
                : null;
        String skip = evaluateSkipReason(object, profile);
        if (skip != null) {
            markSkipped(objectId, skip);
            return;
        }

        transactionTemplate.executeWithoutResult(tx -> {
            OssObjectDO row = ossObjectRepository.findById(objectId).orElse(null);
            if (row == null) {
                return;
            }
            row.setExtractStatus(EXTRACT_EXTRACTING);
            row.setExtractError(null);
            row.setUpdateTime(LocalDateTime.now(ZONE_SH));
            ossObjectRepository.save(row);
        });

        if (EXTRACT_EXTRACTING.equals(status)) {
            rollbackChildren(object);
        }

        Limits limits = resolveLimits(profile);
        List<OssObjectDO> written = new ArrayList<>();
        int skipped = 0;
        try {
            ExtractOutcome outcome = extractEntries(object, profile, limits, written);
            skipped = outcome.skipped;
            if (written.isEmpty()) {
                rollbackQuietly(written);
                markFailed(objectId, "合格文件数为 0，整包失败");
                return;
            }
            String summary = OssArchiveExtractSupport.summary(written.size(), skipped, 0);
            boolean keep = profile == null || profile.getExtractKeepArchive() == null
                    || Boolean.TRUE.equals(profile.getExtractKeepArchive());
            markDone(objectId, summary, !keep);
        } catch (PackRejectedException e) {
            rollbackQuietly(written);
            markFailed(objectId, e.getMessage());
        } catch (OssArchiveLimitException e) {
            rollbackQuietly(written);
            markFailed(objectId, e.getMessage());
        } catch (FlowException e) {
            rollbackQuietly(written);
            markFailed(objectId, e.getMessage());
        } catch (Exception e) {
            rollbackQuietly(written);
            markFailed(objectId, e.getMessage());
        }
    }

    private ExtractOutcome extractEntries(OssObjectDO parent, OssUploadProfileDO profile, Limits limits,
                                          List<OssObjectDO> written) throws Exception {
        boolean failPack = OssArchiveExtractSupport.failPack(
                profile != null ? profile.getExtractRejectPolicy() : null);
        String innerExts = profile != null ? profile.getExtractAllowedExtensions() : null;
        String innerMimes = profile != null ? profile.getExtractAllowedContentTypes() : null;
        long archiveSize = parent.getSizeBytes() != null ? parent.getSizeBytes() : 0;
        long maxRatioUncompressed = 0;
        if (limits.maxRatio > 0 && archiveSize > 0) {
            maxRatioUncompressed = (long) Math.min((double) Long.MAX_VALUE, archiveSize * limits.maxRatio);
        }

        OssLimitedInputStream.Counter counter = new OssLimitedInputStream.Counter();
        int fileEntries = 0;
        int skipped = 0;

        try (GetObjectResponse raw = ossStorageService.getObject(
                parent.getConnectionCode(), parent.getBucket(), parent.getObjectKey());
             ZipInputStream zipIn = new ZipInputStream(raw)) {
            ZipEntry entry;
            while ((entry = zipIn.getNextEntry()) != null) {
                String rawName = entry.getName();
                boolean dir = OssArchiveExtractSupport.isDirectoryName(rawName, entry.isDirectory());
                if (dir) {
                    zipIn.closeEntry();
                    continue;
                }
                fileEntries++;
                if (limits.maxEntries > 0 && fileEntries > limits.maxEntries) {
                    throw new PackRejectedException("压缩包条目数超过上限 " + limits.maxEntries);
                }

                String relative = OssArchiveExtractSupport.sanitizeEntryPath(rawName);
                if (relative == null) {
                    throw new PackRejectedException("非法包内路径（Zip Slip）: " + rawName);
                }
                if (OssArchiveExtractSupport.isJunkPath(relative)) {
                    skipped++;
                    zipIn.closeEntry();
                    continue;
                }

                long declared = entry.getSize();
                if (declared > 0) {
                    if (limits.maxUncompressed > 0 && counter.get() + declared > limits.maxUncompressed) {
                        throw new OssArchiveLimitException("解压后总大小超过上限");
                    }
                    if (maxRatioUncompressed > 0 && counter.get() + declared > maxRatioUncompressed) {
                        throw new OssArchiveLimitException("压缩比超过安全上限（疑似 zip bomb）");
                    }
                }

                byte[] head = readPrefix(zipIn, PEEK_BYTES);
                String ext = OssArchiveExtractSupport.extensionOf(relative);
                if (OssContentSniffer.looksLikeNestedArchive(ext, head)) {
                    skipped++;
                    zipIn.closeEntry();
                    continue;
                }
                if (!OssContentSniffer.magicMatchesExtension(ext, head)) {
                    if (failPack) {
                        throw new PackRejectedException("扩展名与文件头不符: " + relative);
                    }
                    skipped++;
                    zipIn.closeEntry();
                    continue;
                }
                String contentType = OssContentSniffer.resolveContentType(ext, head);
                if (!OssArchiveExtractSupport.allowedByInnerList(ext, contentType, innerExts, innerMimes)) {
                    if (failPack) {
                        throw new PackRejectedException("不在展开白名单内: " + relative);
                    }
                    skipped++;
                    zipIn.closeEntry();
                    continue;
                }

                String childKey = OssArchiveExtractSupport.childObjectKey(parent.getObjectKey(), relative);
                if (childKey == null) {
                    if (failPack) {
                        throw new PackRejectedException("对象键过长或非法: " + relative);
                    }
                    skipped++;
                    zipIn.closeEntry();
                    continue;
                }

                InputStream rest = new SequenceInputStream(
                        new ByteArrayInputStream(head), new OssNonClosingInputStream(zipIn));
                OssLimitedInputStream limited = new OssLimitedInputStream(
                        rest, counter, limits.maxUncompressed, limits.maxUncompressed, maxRatioUncompressed);
                MessageDigest digest = MessageDigest.getInstance("SHA-256");
                DigestInputStream din = new DigestInputStream(limited, digest);

                demoModeGuard.checkOssWrite(parent.getBucket());
                ossStorageService.putObject(
                        parent.getConnectionCode(), din, -1, contentType, parent.getBucket(), childKey);

                long size = limited.getEntryBytes();
                if (size <= 0) {
                    ossStorageService.removeObject(parent.getConnectionCode(), parent.getBucket(), childKey);
                    skipped++;
                    zipIn.closeEntry();
                    continue;
                }

                try {
                    long creditBytes = 0;
                    int creditFiles = 0;
                    if (profile != null && Boolean.FALSE.equals(profile.getExtractKeepArchive())) {
                        creditBytes = parent.getSizeBytes() != null ? parent.getSizeBytes() : 0;
                        creditFiles = 1;
                    }
                    ossQuotaService.checkBeforeUpload(profile, parent.getUploadedBy(),
                            parent.getUploadedByUserType(), size, 1, creditBytes, creditFiles);
                } catch (FlowException e) {
                    ossStorageService.removeObject(parent.getConnectionCode(), parent.getBucket(), childKey);
                    throw e;
                }

                String checksum = HexFormat.of().formatHex(digest.digest());
                OssObjectDO child;
                try {
                    child = persistChild(parent, relative, childKey, contentType, ext, size, checksum);
                } catch (RuntimeException e) {
                    ossStorageService.removeObject(parent.getConnectionCode(), parent.getBucket(), childKey);
                    throw e;
                }
                written.add(child);
                ossThumbnailService.scheduleIfNeeded(child, profile);
                zipIn.closeEntry();
            }
        }
        return new ExtractOutcome(skipped);
    }

    private OssObjectDO persistChild(OssObjectDO parent, String relativePath, String objectKey,
                                     String contentType, String extension, long size, String checksum) {
        LocalDateTime now = LocalDateTime.now(ZONE_SH);
        String visibility = parent.getVisibility();
        String publicPath = OssObjectDO.VISIBILITY_PUBLIC.equals(visibility)
                ? OssKeyPatternResolver.buildPublicPath(objectKey) : null;
        OssObjectDO child = OssObjectDO.builder()
                .profileCode(parent.getProfileCode())
                .connectionCode(parent.getConnectionCode())
                .bucket(parent.getBucket())
                .objectKey(objectKey)
                .visibility(visibility)
                .publicPath(publicPath)
                .originalName(relativePath)
                .contentType(contentType)
                .extension(extension)
                .sizeBytes(size)
                .checksumSha256(checksum)
                .bizMeta(parent.getBizMeta())
                .uploadedBy(parent.getUploadedBy())
                .uploadedByUserType(parent.getUploadedByUserType())
                .uploadedByName(parent.getUploadedByName())
                .deptId(parent.getDeptId())
                .status(OssObjectDO.STATUS_ACTIVE)
                .expiresAt(parent.getExpiresAt())
                .objectPurged(false)
                .thumbStatus(OssThumbnailService.THUMB_NONE)
                .parentObjectId(parent.getId())
                .archiveEntryPath(relativePath)
                .extractStatus(EXTRACT_NONE)
                .createTime(now)
                .updateTime(now)
                .build();
        return transactionTemplate.execute(tx -> ossObjectRepository.save(child));
    }

    private void rollbackChildren(OssObjectDO parent) {
        List<OssObjectDO> existing = ossObjectRepository.findByParentObjectIdAndStatus(
                parent.getId(), OssObjectDO.STATUS_ACTIVE);
        rollbackQuietly(existing);
    }

    private void rollbackQuietly(List<OssObjectDO> children) {
        if (children == null || children.isEmpty()) {
            return;
        }
        LocalDateTime now = LocalDateTime.now(ZONE_SH);
        for (OssObjectDO child : children) {
            try {
                ossStorageService.removeObject(child.getConnectionCode(), child.getBucket(), child.getObjectKey());
            } catch (Exception e) {
                log.warn("[OSS] extract rollback remove failed key={}: {}", child.getObjectKey(), e.getMessage());
            }
            try {
                transactionTemplate.executeWithoutResult(tx -> {
                    OssObjectDO row = ossObjectRepository.findById(child.getId()).orElse(null);
                    if (row == null) {
                        return;
                    }
                    row.setStatus(OssObjectDO.STATUS_DELETED);
                    row.setObjectPurged(true);
                    row.setUpdateTime(now);
                    ossObjectRepository.save(row);
                });
            } catch (Exception e) {
                log.warn("[OSS] extract rollback meta failed id={}: {}", child.getId(), e.getMessage());
            }
        }
        children.clear();
    }

    private String evaluateSkipReason(OssObjectDO object, OssUploadProfileDO profile) {
        YuFlowProperties.Oss.Extract cfg = extractCfg();
        if (cfg != null && !cfg.isEnabled()) {
            return "全局压缩包展开已关闭";
        }
        if (profile == null || !Boolean.TRUE.equals(profile.getExtractArchiveEnabled())) {
            return "场景未启用压缩包展开";
        }
        if (!OssArchiveExtractSupport.isZipObject(object.getExtension(), object.getContentType())) {
            return "非 zip 文件";
        }
        return null;
    }

    private Limits resolveLimits(OssUploadProfileDO profile) {
        YuFlowProperties.Oss.Extract cfg = extractCfg();
        int maxEntries = DEFAULT_MAX_ENTRIES;
        long maxUncompressed = DEFAULT_MAX_UNCOMPRESSED;
        double maxRatio = DEFAULT_MAX_RATIO;
        if (cfg != null) {
            if (cfg.getMaxEntries() > 0) {
                maxEntries = cfg.getMaxEntries();
            }
            if (cfg.getMaxUncompressedBytes() > 0) {
                maxUncompressed = cfg.getMaxUncompressedBytes();
            }
            if (cfg.getMaxRatio() > 0) {
                maxRatio = cfg.getMaxRatio();
            }
        }
        if (profile != null && profile.getExtractMaxEntries() != null && profile.getExtractMaxEntries() > 0) {
            maxEntries = profile.getExtractMaxEntries();
        }
        if (profile != null && profile.getExtractMaxUncompressedBytes() != null
                && profile.getExtractMaxUncompressedBytes() > 0) {
            maxUncompressed = profile.getExtractMaxUncompressedBytes();
        }
        return new Limits(maxEntries, maxUncompressed, maxRatio);
    }

    private YuFlowProperties.Oss.Extract extractCfg() {
        return yuFlowProperties.getOss() != null ? yuFlowProperties.getOss().getExtract() : null;
    }

    private void markDone(String objectId, String summary, boolean deleteArchive) {
        transactionTemplate.executeWithoutResult(tx -> {
            OssObjectDO object = ossObjectRepository.findById(objectId).orElse(null);
            if (object == null) {
                return;
            }
            object.setExtractStatus(EXTRACT_DONE);
            object.setExtractError(OssArchiveExtractSupport.truncateError(summary));
            if (deleteArchive) {
                object.setStatus(OssObjectDO.STATUS_DELETED);
                object.setObjectPurged(false);
            }
            object.setUpdateTime(LocalDateTime.now(ZONE_SH));
            ossObjectRepository.save(object);
        });
    }

    private void markFailed(String objectId, String error) {
        transactionTemplate.executeWithoutResult(tx -> {
            OssObjectDO object = ossObjectRepository.findById(objectId).orElse(null);
            if (object == null) {
                return;
            }
            object.setExtractStatus(EXTRACT_FAILED);
            object.setExtractError(OssArchiveExtractSupport.truncateError(error));
            object.setUpdateTime(LocalDateTime.now(ZONE_SH));
            ossObjectRepository.save(object);
        });
    }

    private void markSkipped(String objectId, String reason) {
        transactionTemplate.executeWithoutResult(tx -> {
            OssObjectDO object = ossObjectRepository.findById(objectId).orElse(null);
            if (object == null) {
                return;
            }
            object.setExtractStatus(EXTRACT_SKIPPED);
            object.setExtractError(OssArchiveExtractSupport.truncateError(reason));
            object.setUpdateTime(LocalDateTime.now(ZONE_SH));
            ossObjectRepository.save(object);
        });
    }

    static byte[] readPrefix(InputStream in, int n) throws Exception {
        byte[] buf = new byte[n];
        int off = 0;
        while (off < n) {
            int r = in.read(buf, off, n - off);
            if (r < 0) {
                break;
            }
            off += r;
        }
        if (off == n) {
            return buf;
        }
        byte[] actual = new byte[off];
        System.arraycopy(buf, 0, actual, 0, off);
        return actual;
    }

    private record Limits(int maxEntries, long maxUncompressed, double maxRatio) {
    }

    private record ExtractOutcome(int skipped) {
    }

    static final class PackRejectedException extends RuntimeException {
        PackRejectedException(String message) {
            super(message);
        }
    }
}
