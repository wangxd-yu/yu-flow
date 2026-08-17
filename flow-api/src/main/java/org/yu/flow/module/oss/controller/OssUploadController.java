package org.yu.flow.module.oss.controller;

import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.yu.flow.annotation.YuFlowApi;
import org.yu.flow.dto.R;
import org.yu.flow.module.host.FlowHostAuthSupport;
import org.yu.flow.module.host.FlowHostPrincipal;
import org.yu.flow.module.oss.dto.OssUploadOptions;
import org.yu.flow.module.oss.dto.OssUploadResultDTO;
import org.yu.flow.module.oss.service.OssObjectService;
import org.yu.flow.module.oss.support.OssUploadRequestParser;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.yu.flow.module.oss.config.ConditionalOnOssEnabled;

@YuFlowApi
@ConditionalOnOssEnabled
@RestController
@RequestMapping("/flow-api/oss")
public class OssUploadController {

    @Resource
    private OssObjectService ossObjectService;

    @Resource
    private FlowHostAuthSupport flowHostAuthSupport;

    @PostMapping("/upload")
    public R<List<OssUploadResultDTO>> upload(@RequestParam("profile") String profileCode,
                                              @RequestParam(value = "file", required = false) MultipartFile[] file,
                                              @RequestParam(value = "files", required = false) MultipartFile[] files,
                                              HttpServletRequest request) {
        FlowHostPrincipal principal = flowHostAuthSupport.getPrincipalProvider().resolve(request).orElse(null);
        MultipartFile[] uploadFiles = resolveFiles(file, files, request);
        Map<String, String> bizFields = extractBizFields(request);
        OssUploadOptions options = OssUploadRequestParser.parseOptions(request, bizFields);
        return R.ok(ossObjectService.upload(profileCode, uploadFiles, bizFields, principal, request, options));
    }

    private static MultipartFile[] resolveFiles(MultipartFile[] file, MultipartFile[] files, HttpServletRequest request) {
        List<MultipartFile> list = extractNonEmpty(files);
        if (!list.isEmpty()) {
            return list.toArray(new MultipartFile[0]);
        }
        list = extractNonEmpty(file);
        if (!list.isEmpty()) {
            return list.toArray(new MultipartFile[0]);
        }
        if (request instanceof org.springframework.web.multipart.MultipartHttpServletRequest multiRequest) {
            for (List<MultipartFile> fileList : multiRequest.getMultiFileMap().values()) {
                List<MultipartFile> extracted = extractNonEmpty(fileList != null ? fileList.toArray(new MultipartFile[0]) : null);
                if (!extracted.isEmpty()) {
                    return extracted.toArray(new MultipartFile[0]);
                }
            }
        }
        return new MultipartFile[0];
    }

    private static List<MultipartFile> extractNonEmpty(MultipartFile[] files) {
        List<MultipartFile> list = new ArrayList<>();
        if (files != null) {
            for (MultipartFile f : files) {
                if (f != null && !f.isEmpty()) {
                    list.add(f);
                }
            }
        }
        return list;
    }

    private static Map<String, String> extractBizFields(HttpServletRequest request) {
        Map<String, String> map = new HashMap<>();
        if (request == null) {
            return map;
        }
        request.getParameterMap().forEach((key, values) -> {
            if ("profile".equals(key) || "file".equals(key) || "files".equals(key)
                    || "expiresAt".equals(key) || "expiresInSeconds".equals(key)
                    || "overwrite".equals(key) || "uploadedBy".equals(key)
                    || "uploadedByName".equals(key) || "uploadedByUserType".equals(key)) {
                return;
            }
            if (values != null && values.length > 0) {
                map.put(key, values[0]);
            }
        });
        return map;
    }
}
