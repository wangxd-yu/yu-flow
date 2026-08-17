package org.yu.flow.module.oss.controller;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;
import org.yu.flow.annotation.YuFlowApi;
import org.yu.flow.dto.R;
import org.yu.flow.module.host.FlowHostAuthSupport;
import org.yu.flow.module.host.FlowHostPrincipal;
import org.yu.flow.module.oss.dto.OssPresignUploadInitDTO;
import org.yu.flow.module.oss.dto.OssUploadResultDTO;
import org.yu.flow.module.oss.service.presign.OssPresignUploadService;

import java.util.HashMap;
import java.util.Map;
import org.yu.flow.module.oss.config.ConditionalOnOssEnabled;

/**
 * 预签名直传：文件体由客户端 PUT 直达 OSS，网关只负责开票与复核。
 */
@YuFlowApi
@ConditionalOnOssEnabled
@RestController
@RequestMapping("/flow-api/oss/presign")
public class OssPresignUploadController {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Resource
    private OssPresignUploadService ossPresignUploadService;

    @Resource
    private FlowHostAuthSupport flowHostAuthSupport;

    @PostMapping("/init")
    public R<OssPresignUploadInitDTO> init(@RequestParam("profile") String profileCode,
                                           @RequestParam(value = "originalName", required = false) String originalName,
                                           @RequestParam(value = "contentType", required = false) String contentType,
                                           @RequestParam(value = "sizeBytes", required = false) Long sizeBytes,
                                           @RequestBody(required = false) Map<String, String> body,
                                           HttpServletRequest request) throws Exception {
        FlowHostPrincipal principal = flowHostAuthSupport.getPrincipalProvider().resolve(request).orElse(null);
        Map<String, String> bizFields = mergeBizFields(request, body);
        if (originalName == null && bizFields.containsKey("originalName")) {
            originalName = bizFields.remove("originalName");
        }
        if (contentType == null && bizFields.containsKey("contentType")) {
            contentType = bizFields.remove("contentType");
        }
        if (sizeBytes == null && bizFields.containsKey("sizeBytes")) {
            sizeBytes = parseLongQuietly(bizFields.remove("sizeBytes"));
        }
        return R.ok(ossPresignUploadService.init(profileCode, originalName, contentType, sizeBytes,
                bizFields, principal, request));
    }

    @PostMapping("/{objectId}/confirm")
    public R<OssUploadResultDTO> confirm(@PathVariable String objectId, HttpServletRequest request) {
        FlowHostPrincipal principal = flowHostAuthSupport.getPrincipalProvider().resolve(request).orElse(null);
        return R.ok(ossPresignUploadService.confirm(objectId, principal));
    }

    @DeleteMapping("/{objectId}")
    public R<Void> abort(@PathVariable String objectId, HttpServletRequest request) {
        FlowHostPrincipal principal = flowHostAuthSupport.getPrincipalProvider().resolve(request).orElse(null);
        ossPresignUploadService.abort(objectId, principal);
        return R.ok();
    }

    private static Long parseLongQuietly(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Long.parseLong(value.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static Map<String, String> mergeBizFields(HttpServletRequest request, Map<String, String> body)
            throws Exception {
        Map<String, String> map = new HashMap<>();
        if (body != null) {
            map.putAll(body);
        }
        if (request == null) {
            return map;
        }
        String json = request.getParameter("bizFields");
        if (json != null && !json.isBlank()) {
            Map<String, String> parsed = MAPPER.readValue(json, new TypeReference<>() {});
            map.putAll(parsed);
        }
        request.getParameterMap().forEach((key, values) -> {
            if ("profile".equals(key) || "originalName".equals(key) || "contentType".equals(key)
                    || "sizeBytes".equals(key) || "bizFields".equals(key)
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
