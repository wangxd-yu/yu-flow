package org.yu.flow.module.oss.support;

import cn.hutool.core.util.StrUtil;
import jakarta.servlet.http.HttpServletRequest;
import org.yu.flow.exception.FlowException;
import org.yu.flow.module.oss.dto.OssUploadOptions;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Map;

/**
 * 上传请求参数解析（过期时间、覆盖、代传等）。
 */
public final class OssUploadRequestParser {

    private static final ZoneId ZONE_SH = ZoneId.of("Asia/Shanghai");
    private static final DateTimeFormatter EXPIRES_AT_FMT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private OssUploadRequestParser() {
    }

    public static OssUploadOptions parseOptions(HttpServletRequest request, Map<String, String> bizFields) {
        OssUploadOptions options = new OssUploadOptions();
        if (request == null) {
            return options;
        }
        String expiresAt = request.getParameter("expiresAt");
        if (StrUtil.isNotBlank(expiresAt)) {
            try {
                options.setExpiresAt(LocalDateTime.parse(expiresAt.trim(), EXPIRES_AT_FMT));
            } catch (DateTimeParseException e) {
                throw new FlowException("OSS_EXPIRES_AT_INVALID", "expiresAt 格式应为 yyyy-MM-dd HH:mm:ss");
            }
        }
        String expiresInSeconds = request.getParameter("expiresInSeconds");
        if (StrUtil.isNotBlank(expiresInSeconds)) {
            try {
                options.setExpiresInSeconds(Long.parseLong(expiresInSeconds.trim()));
            } catch (NumberFormatException e) {
                throw new FlowException("OSS_EXPIRES_IN_INVALID", "expiresInSeconds 必须为整数");
            }
        }
        options.setOverwrite("true".equalsIgnoreCase(request.getParameter("overwrite")));
        String uploadedBy = request.getParameter("uploadedBy");
        if (StrUtil.isNotBlank(uploadedBy)) {
            options.setUploadedByOverride(uploadedBy.trim());
        }
        String uploadedByUserType = request.getParameter("uploadedByUserType");
        if (StrUtil.isNotBlank(uploadedByUserType)) {
            options.setUploadedByUserTypeOverride(uploadedByUserType.trim());
        }
        String uploadedByName = request.getParameter("uploadedByName");
        if (StrUtil.isNotBlank(uploadedByName)) {
            options.setUploadedByNameOverride(uploadedByName.trim());
        }
        if (bizFields != null && bizFields.containsKey("expiresAt") && options.getExpiresAt() == null) {
            try {
                options.setExpiresAt(LocalDateTime.parse(bizFields.get("expiresAt").trim(), EXPIRES_AT_FMT));
            } catch (DateTimeParseException ignored) {
                /* ignore biz field parse */
            }
        }
        return options;
    }

    public static LocalDateTime resolveExpiresAt(OssUploadOptions options) {
        if (options == null) {
            return null;
        }
        if (options.getExpiresAt() != null) {
            return options.getExpiresAt();
        }
        if (options.getExpiresInSeconds() != null && options.getExpiresInSeconds() > 0) {
            return LocalDateTime.now(ZONE_SH).plusSeconds(options.getExpiresInSeconds());
        }
        return null;
    }
}
