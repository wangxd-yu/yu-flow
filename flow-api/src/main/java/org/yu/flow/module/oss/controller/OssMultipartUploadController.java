package org.yu.flow.module.oss.controller;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.yu.flow.annotation.YuFlowApi;
import org.yu.flow.dto.R;
import org.yu.flow.module.host.FlowHostAuthSupport;
import org.yu.flow.module.host.FlowHostPrincipal;
import org.yu.flow.module.oss.dto.OssMultipartInitResultDTO;
import org.yu.flow.module.oss.dto.OssUploadResultDTO;
import org.yu.flow.module.oss.service.multipart.OssMultipartUploadService;

import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

@YuFlowApi
@RestController
@RequestMapping("/flow-api/oss/multipart")
public class OssMultipartUploadController {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Resource
    private OssMultipartUploadService ossMultipartUploadService;

    @Resource
    private FlowHostAuthSupport flowHostAuthSupport;

    @PostMapping("/init")
    public R<OssMultipartInitResultDTO> init(@RequestParam("profile") String profileCode,
                                             @RequestParam(value = "originalName", required = false) String originalName,
                                             @RequestParam(value = "contentType", required = false) String contentType,
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
        return R.ok(ossMultipartUploadService.init(profileCode, originalName, contentType, bizFields, principal, request));
    }

    @PutMapping("/{uploadId}/parts/{partNumber}")
    public R<Void> uploadPart(@PathVariable String uploadId,
                              @PathVariable int partNumber,
                              @RequestParam(value = "file", required = false) MultipartFile file,
                              HttpServletRequest request) throws Exception {
        if (file != null && !file.isEmpty()) {
            try (InputStream stream = file.getInputStream()) {
                ossMultipartUploadService.uploadPart(uploadId, partNumber, stream, file.getSize());
            }
        } else {
            try (InputStream stream = request.getInputStream()) {
                long size = request.getContentLengthLong();
                if (size < 0) {
                    size = stream.available();
                }
                ossMultipartUploadService.uploadPart(uploadId, partNumber, stream, size);
            }
        }
        return R.ok();
    }

    @PostMapping("/{uploadId}/complete")
    public R<OssUploadResultDTO> complete(@PathVariable String uploadId, HttpServletRequest request) {
        FlowHostPrincipal principal = flowHostAuthSupport.getPrincipalProvider().resolve(request).orElse(null);
        return R.ok(ossMultipartUploadService.complete(uploadId, principal, request));
    }

    @DeleteMapping("/{uploadId}")
    public R<Void> abort(@PathVariable String uploadId) {
        ossMultipartUploadService.abort(uploadId);
        return R.ok();
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
                    || "bizFields".equals(key) || "expiresAt".equals(key) || "expiresInSeconds".equals(key)
                    || "overwrite".equals(key) || "uploadedBy".equals(key) || "uploadedByName".equals(key)) {
                return;
            }
            if (values != null && values.length > 0) {
                map.put(key, values[0]);
            }
        });
        return map;
    }
}
