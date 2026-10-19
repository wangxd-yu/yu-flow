package org.yu.flow.module.api.dataview;

import cn.hutool.core.util.StrUtil;
import jakarta.servlet.http.HttpServletResponse;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/** xlsx 下载的响应头与文件名 */
public final class ExcelDownloads {

    public static final String XLSX_MIME = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

    private ExcelDownloads() {
    }

    public static void applyDownloadHeaders(HttpServletResponse response, String fileName) {
        response.setContentType(XLSX_MIME);
        response.setCharacterEncoding("utf-8");
        response.setHeader("Cache-Control", "no-store");
        response.setHeader("Content-Disposition",
                "attachment;filename*=UTF-8''" + URLEncoder.encode(fileName, StandardCharsets.UTF_8));
        response.setHeader("Access-Control-Expose-Headers",
                "Content-Disposition, X-Export-Mode, X-Export-Fallback, X-Export-Fallback-Message, X-Export-Rows");
    }

    /** 模板导出回退为动态表头时告知前端原因 */
    public static void applyFallbackHeaders(HttpServletResponse response, String code, String message) {
        response.setHeader("X-Export-Fallback", code);
        if (StrUtil.isNotBlank(message)) {
            response.setHeader("X-Export-Fallback-Message",
                    URLEncoder.encode(message, StandardCharsets.UTF_8).replace("+", "%20"));
        }
    }

    /** 接口名去掉文件名非法字符，空时用 export */
    public static String baseName(String apiName) {
        return StrUtil.blankToDefault(apiName, "export").replaceAll("[\\\\/:*?\"<>|]", "_");
    }

    public static String timestampedFileName(String apiName) {
        return baseName(apiName) + "_" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss")) + ".xlsx";
    }
}
