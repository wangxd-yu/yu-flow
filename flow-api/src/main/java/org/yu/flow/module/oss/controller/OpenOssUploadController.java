package org.yu.flow.module.oss.controller;

import cn.hutool.core.util.StrUtil;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.yu.flow.annotation.YuFlowApi;
import org.yu.flow.dto.R;
import org.yu.flow.exception.FlowException;
import org.yu.flow.module.host.FlowHostPrincipal;
import org.yu.flow.module.oss.dto.OssUploadOptions;
import org.yu.flow.module.oss.dto.OssUploadResultDTO;
import org.yu.flow.module.oss.service.OssObjectService;
import org.yu.flow.module.oss.support.OssUploadRequestParser;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 开放平台 OSS 上传（AppKey/HMAC 鉴权，不经管理端 JWT）。
 */
@YuFlowApi
@RestController
@RequestMapping("/flow-api/open/oss")
public class OpenOssUploadController {

    @Resource
    private OssObjectService ossObjectService;

    @PostMapping("/upload")
    public R<List<OssUploadResultDTO>> upload(@RequestParam("profile") String profileCode,
                                              @RequestParam(value = "file", required = false) MultipartFile file,
                                              @RequestParam(value = "files", required = false) MultipartFile[] files,
                                              HttpServletRequest request) {
        String appKey = str(request.getAttribute("yuOpenAppKey"));
        if (StrUtil.isBlank(appKey)) {
            throw new FlowException("OPEN_UNAUTHORIZED", "开放上传缺少 AppKey 鉴权");
        }
        FlowHostPrincipal principal = FlowHostPrincipal.builder()
                .userId("open:" + appKey)
                .username(appKey)
                .build();
        MultipartFile[] uploadFiles = resolveFiles(file, files);
        Map<String, String> bizFields = extractBizFields(request);
        OssUploadOptions options = OssUploadRequestParser.parseOptions(request, bizFields);
        return R.ok(ossObjectService.upload(profileCode, uploadFiles, bizFields, principal, request, options));
    }

    private static MultipartFile[] resolveFiles(MultipartFile file, MultipartFile[] files) {
        if (files != null && files.length > 0) {
            return files;
        }
        if (file != null && !file.isEmpty()) {
            return new MultipartFile[]{file};
        }
        return new MultipartFile[0];
    }

    private static Map<String, String> extractBizFields(HttpServletRequest request) {
        Map<String, String> map = new HashMap<>();
        if (request == null) {
            return map;
        }
        request.getParameterMap().forEach((key, values) -> {
            if ("profile".equals(key) || "file".equals(key) || "files".equals(key)
                    || "expiresAt".equals(key) || "expiresInSeconds".equals(key)
                    || "overwrite".equals(key) || "uploadedBy".equals(key) || "uploadedByName".equals(key)) {
                return;
            }
            if (values != null && values.length > 0) {
                map.put(key, values[0]);
            }
        });
        return map;
    }

    private static String str(Object o) {
        return o == null ? null : String.valueOf(o);
    }
}
