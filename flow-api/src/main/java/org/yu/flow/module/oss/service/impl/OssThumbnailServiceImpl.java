package org.yu.flow.module.oss.service.impl;

import cn.hutool.core.util.StrUtil;
import io.minio.GetObjectResponse;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.yu.flow.config.DemoModeGuard;
import org.yu.flow.config.YuFlowProperties;
import org.yu.flow.exception.FlowException;
import org.yu.flow.module.host.FlowHostDataScope;
import org.yu.flow.module.host.FlowHostPrincipal;
import org.yu.flow.module.oss.client.OssStorageService;
import org.yu.flow.module.oss.domain.OssObjectDO;
import org.yu.flow.module.oss.domain.OssUploadProfileDO;
import org.yu.flow.module.oss.repository.OssObjectRepository;
import org.yu.flow.module.oss.repository.OssUploadProfileRepository;
import org.yu.flow.module.oss.service.OssThumbnailService;
import org.yu.flow.module.oss.spi.FlowOssObjectAccessVoter;
import org.yu.flow.module.oss.support.OssAccessEvaluator;
import org.yu.flow.module.oss.support.OssKeyPatternResolver;
import org.yu.flow.module.oss.support.OssProfileCallerAuth;
import org.yu.flow.module.oss.support.OssUploaderIdentity;
import org.yu.flow.module.rbac.service.RbacService;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Iterator;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import org.yu.flow.module.oss.config.ConditionalOnOssEnabled;

@Slf4j
@ConditionalOnOssEnabled
@Service
public class OssThumbnailServiceImpl implements OssThumbnailService {

    private static final ZoneId ZONE_SH = ZoneId.of("Asia/Shanghai");
    private static final String THUMB_SUFFIX = ".thumb.jpg";
    private static final String THUMB_CONTENT_TYPE = "image/jpeg";
    private static final Set<String> IMAGE_EXTENSIONS = Set.of(
            "jpg", "jpeg", "png", "gif", "bmp", "webp");

    @Resource
    private OssObjectRepository ossObjectRepository;

    @Resource
    private OssUploadProfileRepository ossUploadProfileRepository;

    @Resource
    private OssStorageService ossStorageService;

    @Resource
    private DemoModeGuard demoModeGuard;

    @Resource
    private YuFlowProperties yuFlowProperties;

    @Resource
    private OssAccessEvaluator ossAccessEvaluator;

    @Resource
    private OssProfileCallerAuth ossProfileCallerAuth;

    @Resource
    private RbacService rbacService;

    @Resource
    private TransactionTemplate transactionTemplate;

    private ExecutorService executor;

    @PostConstruct
    public void init() {
        ThreadFactory factory = r -> {
            Thread t = new Thread(r, "oss-thumbnail");
            t.setDaemon(true);
            return t;
        };
        executor = Executors.newFixedThreadPool(2, factory);
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
        YuFlowProperties.Oss.Thumbnail cfg = yuFlowProperties.getOss().getThumbnail();
        String skipReason = evaluateSkipReason(object, profile, cfg);
        if (skipReason != null) {
            object.setThumbStatus(THUMB_SKIPPED);
            object.setThumbError(truncateError(skipReason));
            object.setUpdateTime(LocalDateTime.now(ZONE_SH));
            ossObjectRepository.save(object);
            return;
        }
        object.setThumbStatus(THUMB_PENDING);
        object.setThumbError(null);
        object.setUpdateTime(LocalDateTime.now(ZONE_SH));
        ossObjectRepository.save(object);
        String objectId = object.getId();
        enqueueAfterCommit(objectId);
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
        OssObjectDO object = ossObjectRepository.findById(objectId).orElse(null);
        if (object == null || !OssObjectDO.STATUS_ACTIVE.equals(object.getStatus())) {
            return;
        }
        if (!THUMB_PENDING.equals(object.getThumbStatus())) {
            return;
        }

        YuFlowProperties.Oss.Thumbnail cfg = yuFlowProperties.getOss().getThumbnail();
        OssUploadProfileDO profile = StrUtil.isNotBlank(object.getProfileCode())
                ? ossUploadProfileRepository.findByCode(object.getProfileCode()).orElse(null)
                : null;
        int maxEdge = resolveMaxEdge(profile, cfg);
        double jpegQuality = resolveJpegQuality(profile, cfg);

        try (GetObjectResponse raw = ossStorageService.getObject(
                object.getConnectionCode(), object.getBucket(), object.getObjectKey());
             InputStream in = raw) {
            BufferedImage source = ImageIO.read(in);
            if (source == null) {
                markFailed(objectId, "无法解码为图片");
                return;
            }
            BufferedImage scaled = scaleToMaxEdge(source, maxEdge);
            byte[] jpegBytes = writeJpeg(scaled, (float) jpegQuality);
            if (jpegBytes.length == 0) {
                markFailed(objectId, "缩略图编码失败");
                return;
            }

            String thumbKey = object.getObjectKey() + THUMB_SUFFIX;
            demoModeGuard.checkOssWrite(object.getBucket());
            try (ByteArrayInputStream thumbStream = new ByteArrayInputStream(jpegBytes)) {
                ossStorageService.putObject(
                        object.getConnectionCode(),
                        thumbStream,
                        jpegBytes.length,
                        THUMB_CONTENT_TYPE,
                        object.getBucket(),
                        thumbKey);
            }

            String thumbPublicPath = null;
            if (OssObjectDO.VISIBILITY_PUBLIC.equals(object.getVisibility())) {
                thumbPublicPath = OssKeyPatternResolver.buildPublicPath(thumbKey);
            }

            markReady(objectId, thumbKey, thumbPublicPath, jpegBytes.length);
        } catch (FlowException e) {
            markFailed(objectId, e.getMessage());
        } catch (Exception e) {
            log.warn("[OSS] thumbnail process failed id={}: {}", objectId, e.getMessage());
            markFailed(objectId, e.getMessage());
        }
    }

    @Override
    public void streamThumbnail(String id, FlowHostPrincipal principal, FlowHostDataScope scope,
                                HttpServletResponse response) {
        OssObjectDO object = ossObjectRepository.findById(id).orElse(null);
        if (object == null || !OssObjectDO.STATUS_ACTIVE.equals(object.getStatus())) {
            throw new FlowException("OSS_OBJECT_NOT_FOUND", "文件不存在: " + id);
        }
        ossProfileCallerAuth.assertDownload(object, principal);
        if (!ossAccessEvaluator.canAccess(object, scope, principal, FlowOssObjectAccessVoter.ACTION_DOWNLOAD)) {
            throw new FlowException("RBAC_FORBIDDEN", "无权查看该文件缩略图");
        }

        String status = StrUtil.blankToDefault(object.getThumbStatus(), THUMB_NONE);
        if (THUMB_PENDING.equals(status)) {
            throw new FlowException("OSS_THUMB_PENDING", "缩略图生成中，请稍后重试");
        }
        if (THUMB_FAILED.equals(status)) {
            String msg = StrUtil.blankToDefault(object.getThumbError(), "缩略图生成失败");
            throw new FlowException("OSS_THUMB_FAILED", msg);
        }
        if (THUMB_SKIPPED.equals(status)) {
            throw new FlowException("OSS_THUMB_SKIPPED", "该文件未生成缩略图");
        }
        if (!THUMB_READY.equals(status) || StrUtil.isBlank(object.getThumbObjectKey())) {
            throw new FlowException("OSS_THUMB_NONE", "缩略图不可用");
        }

        try (GetObjectResponse obj = ossStorageService.getObject(
                object.getConnectionCode(), object.getBucket(), object.getThumbObjectKey())) {
            response.setContentType(StrUtil.blankToDefault(object.getThumbContentType(), THUMB_CONTENT_TYPE));
            response.setHeader("Cache-Control", "private, max-age=3600");
            if (object.getThumbSizeBytes() != null && object.getThumbSizeBytes() > 0) {
                response.setContentLengthLong(object.getThumbSizeBytes());
            }
            try (InputStream in = obj; OutputStream out = response.getOutputStream()) {
                in.transferTo(out);
                out.flush();
            }
        } catch (FlowException e) {
            throw e;
        } catch (Exception e) {
            throw new FlowException("OSS_THUMB_STREAM_FAILED", "读取缩略图失败: " + e.getMessage(), e);
        }
    }

    @Override
    @Transactional
    public void rebuild(String id, FlowHostPrincipal principal, FlowHostDataScope scope) {
        OssObjectDO object = ossObjectRepository.findById(id).orElse(null);
        if (object == null || !OssObjectDO.STATUS_ACTIVE.equals(object.getStatus())) {
            throw new FlowException("OSS_OBJECT_NOT_FOUND", "文件不存在: " + id);
        }
        if (!canRebuild(object, principal, scope)) {
            throw new FlowException("RBAC_FORBIDDEN", "无权重建缩略图");
        }
        object.setThumbStatus(THUMB_PENDING);
        object.setThumbError(null);
        object.setUpdateTime(LocalDateTime.now(ZONE_SH));
        ossObjectRepository.save(object);
        enqueueAfterCommit(id);
    }

    private void safeProcess(String objectId) {
        try {
            transactionTemplate.executeWithoutResult(status -> process(objectId));
        } catch (Exception e) {
            log.warn("[OSS] thumbnail async process error id={}: {}", objectId, e.getMessage());
        }
    }

    private boolean canRebuild(OssObjectDO object, FlowHostPrincipal principal, FlowHostDataScope scope) {
        if (principal == null) {
            return false;
        }
        if (rbacService.hasAnyPerm(principal.getUsername(), "flow:oss:admin", "*")) {
            return true;
        }
        if (OssUploaderIdentity.isSelf(object, principal)) {
            return true;
        }
        return ossAccessEvaluator.canAccess(object, scope, principal, FlowOssObjectAccessVoter.ACTION_DELETE);
    }

    private String evaluateSkipReason(OssObjectDO object, OssUploadProfileDO profile,
                                      YuFlowProperties.Oss.Thumbnail cfg) {
        if (cfg == null || !cfg.isEnabled()) {
            return "全局缩略图功能已关闭";
        }
        if (profile == null || profile.getThumbnailEnabled() == null || !profile.getThumbnailEnabled()) {
            return "场景未启用缩略图";
        }
        if (!isImage(object)) {
            return "非图片文件";
        }
        long size = object.getSizeBytes() != null ? object.getSizeBytes() : 0;
        long maxSource = resolveMaxSourceBytes(profile, cfg);
        if (maxSource > 0 && size > maxSource) {
            return "源文件超过缩略图大小上限";
        }
        return null;
    }

    private static int resolveMaxEdge(OssUploadProfileDO profile, YuFlowProperties.Oss.Thumbnail cfg) {
        if (profile != null && profile.getThumbnailMaxEdge() != null && profile.getThumbnailMaxEdge() > 0) {
            return profile.getThumbnailMaxEdge();
        }
        int global = cfg != null ? cfg.getMaxEdge() : 256;
        return global > 0 ? global : 256;
    }

    private static long resolveMaxSourceBytes(OssUploadProfileDO profile, YuFlowProperties.Oss.Thumbnail cfg) {
        if (profile != null && profile.getThumbnailMaxSourceBytes() != null
                && profile.getThumbnailMaxSourceBytes() > 0) {
            return profile.getThumbnailMaxSourceBytes();
        }
        return cfg != null ? cfg.getMaxSourceBytes() : 0;
    }

    private static double resolveJpegQuality(OssUploadProfileDO profile, YuFlowProperties.Oss.Thumbnail cfg) {
        if (profile != null && profile.getThumbnailJpegQuality() != null) {
            double q = profile.getThumbnailJpegQuality();
            if (q > 0 && q <= 1) {
                return q;
            }
        }
        double global = cfg != null ? cfg.getJpegQuality() : 0.85;
        return global > 0 && global <= 1 ? global : 0.85;
    }

    static boolean isImage(OssObjectDO object) {
        if (object == null) {
            return false;
        }
        String ct = StrUtil.blankToDefault(object.getContentType(), "").toLowerCase(Locale.ROOT);
        if (ct.startsWith("image/")) {
            return true;
        }
        String ext = StrUtil.blankToDefault(object.getExtension(), "").toLowerCase(Locale.ROOT);
        return IMAGE_EXTENSIONS.contains(ext);
    }

    static BufferedImage scaleToMaxEdge(BufferedImage source, int maxEdge) {
        int w = source.getWidth();
        int h = source.getHeight();
        if (maxEdge <= 0 || w <= 0 || h <= 0) {
            return source;
        }
        int longest = Math.max(w, h);
        if (longest <= maxEdge) {
            return source;
        }
        double scale = (double) maxEdge / longest;
        int newW = Math.max(1, (int) Math.round(w * scale));
        int newH = Math.max(1, (int) Math.round(h * scale));
        int type = source.getType() == 0 ? BufferedImage.TYPE_INT_RGB : source.getType();
        BufferedImage target = new BufferedImage(newW, newH, type);
        Graphics2D g = target.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.drawImage(source.getScaledInstance(newW, newH, Image.SCALE_SMOOTH), 0, 0, null);
        g.dispose();
        return target;
    }

    static byte[] writeJpeg(BufferedImage image, float quality) throws Exception {
        BufferedImage rgb = image;
        if (image.getType() == BufferedImage.TYPE_BYTE_INDEXED
                || image.getColorModel().hasAlpha()) {
            rgb = new BufferedImage(image.getWidth(), image.getHeight(), BufferedImage.TYPE_INT_RGB);
            Graphics2D g = rgb.createGraphics();
            g.drawImage(image, 0, 0, java.awt.Color.WHITE, null);
            g.dispose();
        }
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName("jpeg");
        if (!writers.hasNext()) {
            ImageIO.write(rgb, "jpeg", baos);
            return baos.toByteArray();
        }
        ImageWriter writer = writers.next();
        try (ImageOutputStream ios = ImageIO.createImageOutputStream(baos)) {
            writer.setOutput(ios);
            ImageWriteParam param = writer.getDefaultWriteParam();
            if (param.canWriteCompressed()) {
                param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
                float q = quality;
                if (q <= 0 || q > 1) {
                    q = 0.85f;
                }
                param.setCompressionQuality(q);
            }
            writer.write(null, new IIOImage(rgb, null, null), param);
        } finally {
            writer.dispose();
        }
        return baos.toByteArray();
    }

    private void markReady(String objectId, String thumbKey, String thumbPublicPath, long sizeBytes) {
        transactionTemplate.executeWithoutResult(status -> {
            OssObjectDO object = ossObjectRepository.findById(objectId).orElse(null);
            if (object == null) {
                return;
            }
            object.setThumbStatus(THUMB_READY);
            object.setThumbObjectKey(thumbKey);
            object.setThumbPublicPath(thumbPublicPath);
            object.setThumbContentType(THUMB_CONTENT_TYPE);
            object.setThumbSizeBytes(sizeBytes);
            object.setThumbError(null);
            object.setUpdateTime(LocalDateTime.now(ZONE_SH));
            ossObjectRepository.save(object);
        });
    }

    private void markFailed(String objectId, String error) {
        transactionTemplate.executeWithoutResult(status -> {
            OssObjectDO object = ossObjectRepository.findById(objectId).orElse(null);
            if (object == null) {
                return;
            }
            object.setThumbStatus(THUMB_FAILED);
            object.setThumbError(truncateError(error));
            object.setUpdateTime(LocalDateTime.now(ZONE_SH));
            ossObjectRepository.save(object);
        });
    }

    private static String truncateError(String error) {
        if (error == null) {
            return null;
        }
        return error.length() > 500 ? error.substring(0, 500) : error;
    }
}
