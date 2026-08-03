package org.yu.flow.engine.evaluator.executor;

import cn.hutool.core.codec.Base64;
import cn.hutool.core.util.StrUtil;
import cn.hutool.extra.spring.SpringUtil;
import io.minio.GetObjectResponse;
import io.minio.Result;
import io.minio.messages.Item;
import lombok.extern.slf4j.Slf4j;
import org.yu.flow.config.DemoModeGuard;
import org.yu.flow.config.YuFlowProperties;
import org.yu.flow.engine.evaluator.ExecutionContext;
import org.yu.flow.engine.model.FlowDefinition;
import org.yu.flow.engine.model.PortNames;
import org.yu.flow.engine.model.step.OssStep;
import org.yu.flow.module.oss.client.OssStorageService;
import org.yu.flow.module.oss.domain.OssConnectionDO;
import org.yu.flow.module.oss.repository.OssConnectionRepository;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * OSS 节点执行器：成功走 success，失败走 fail（软失败）。
 */
@Slf4j
public class OssStepExecutor extends AbstractStepExecutor<OssStep> {

    private static final Pattern VAR_PATTERN = Pattern.compile("\\$\\{([^}]+)\\}");
    private static final long GET_MAX_BYTES = 5L * 1024 * 1024;

    @Override
    public String execute(OssStep step, ExecutionContext context, FlowDefinition flow) {
        Map<String, Object> inputs = prepareInputs(step, context, flow);

        String operation = firstNonBlank(str(inputs.get("operation")), step.getOperation());
        if (StrUtil.isBlank(operation)) {
            return softFail(step, context, "operation 不能为空");
        }
        operation = operation.trim().toLowerCase(Locale.ROOT);

        String connectionCode = firstNonBlank(str(inputs.get("connectionCode")),
                resolveString(step.getConnectionCode(), inputs));
        if (StrUtil.isBlank(connectionCode)) {
            return softFail(step, context, "connectionCode 不能为空");
        }

        OssStorageService storageService;
        OssConnectionRepository connectionRepository;
        DemoModeGuard demoModeGuard;
        try {
            storageService = SpringUtil.getBean(OssStorageService.class);
            connectionRepository = SpringUtil.getBean(OssConnectionRepository.class);
            demoModeGuard = SpringUtil.getBean(DemoModeGuard.class);
        } catch (Exception e) {
            return softFail(step, context, "OSS 服务不可用: " + e.getMessage());
        }
        if (storageService == null || connectionRepository == null) {
            return softFail(step, context, "OSS 服务未装配");
        }

        OssConnectionDO connection = connectionRepository.findByCode(connectionCode).orElse(null);
        if (connection == null || connection.getEnabled() == null || !connection.getEnabled()) {
            return softFail(step, context, "OSS 连接不存在或已停用: " + connectionCode);
        }

        String bucket = firstNonBlank(str(inputs.get("bucket")),
                resolveString(step.getBucket(), inputs));
        if (StrUtil.isBlank(bucket)) {
            bucket = StrUtil.blankToDefault(connection.getPrivateBucket(), connection.getPublicBucket());
        }
        if (StrUtil.isBlank(bucket)) {
            return softFail(step, context, "bucket 不能为空");
        }

        long start = System.currentTimeMillis();
        try {
            Map<String, Object> result = switch (operation) {
                case "put" -> doPut(step, inputs, storageService, demoModeGuard, connectionCode, bucket);
                case "get" -> doGet(step, inputs, context, storageService, connectionCode, bucket);
                case "delete" -> doDelete(step, inputs, storageService, demoModeGuard, connectionCode, bucket);
                case "list" -> doList(step, inputs, storageService, connectionCode, bucket);
                case "presignget" -> doPresignGet(step, inputs, storageService, connectionCode, bucket);
                default -> throw new IllegalArgumentException("不支持的 operation: " + operation);
            };
            result.put("timeMs", System.currentTimeMillis() - start);
            Map<String, Object> out = new HashMap<>();
            out.put(PortNames.OUT, result);
            context.setVar(step.getId(), out);
            return PortNames.SUCCESS;
        } catch (Exception e) {
            log.warn("OssStep [{}] {} 失败: {}", step.getId(), operation, e.getMessage());
            return softFail(step, context, e.getMessage());
        }
    }

    private Map<String, Object> doPut(OssStep step, Map<String, Object> inputs,
                                      OssStorageService storageService, DemoModeGuard demoModeGuard,
                                      String connectionCode, String bucket) throws Exception {
        demoModeGuard.checkOssWrite(bucket);
        String objectKey = firstNonBlank(str(inputs.get("objectKey")),
                resolveString(step.getObjectKey(), inputs));
        validateObjectKey(objectKey);

        byte[] bytes = resolvePutBytes(step, inputs);
        if (bytes == null || bytes.length == 0) {
            throw new IllegalArgumentException("put 操作缺少内容（inputs.content / contentBase64 / localBytesVar）");
        }
        String contentType = firstNonBlank(str(inputs.get("contentType")),
                resolveString(step.getContentType(), inputs));
        if (StrUtil.isBlank(contentType)) {
            contentType = "application/octet-stream";
        }
        try (InputStream in = new ByteArrayInputStream(bytes)) {
            storageService.putObject(connectionCode, in, bytes.length, contentType, bucket, objectKey);
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("bucket", bucket);
        result.put("objectKey", objectKey);
        result.put("size", bytes.length);
        return result;
    }

    private Map<String, Object> doGet(OssStep step, Map<String, Object> inputs, ExecutionContext context,
                                      OssStorageService storageService, String connectionCode,
                                      String bucket) throws Exception {
        String objectKey = firstNonBlank(str(inputs.get("objectKey")),
                resolveString(step.getObjectKey(), inputs));
        validateObjectKey(objectKey);
        try (GetObjectResponse obj = storageService.getObject(connectionCode, bucket, objectKey)) {
            byte[] bytes = obj.readAllBytes();
            if (bytes.length > GET_MAX_BYTES) {
                throw new IllegalArgumentException(
                        "对象大小超过 get 上限 " + GET_MAX_BYTES + " 字节");
            }
            String contentType = obj.headers() != null ? obj.headers().get("Content-Type") : null;
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("bucket", bucket);
            result.put("objectKey", objectKey);
            result.put("contentType", StrUtil.blankToDefault(contentType, "application/octet-stream"));
            result.put("bodyBase64", Base64.encode(bytes));
            result.put("size", bytes.length);
            return result;
        }
    }

    private Map<String, Object> doDelete(OssStep step, Map<String, Object> inputs,
                                         OssStorageService storageService, DemoModeGuard demoModeGuard,
                                         String connectionCode, String bucket) {
        demoModeGuard.checkOssWrite(bucket);
        String objectKey = firstNonBlank(str(inputs.get("objectKey")),
                resolveString(step.getObjectKey(), inputs));
        validateObjectKey(objectKey);
        storageService.removeObject(connectionCode, bucket, objectKey);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("bucket", bucket);
        result.put("objectKey", objectKey);
        result.put("deleted", true);
        return result;
    }

    private Map<String, Object> doList(OssStep step, Map<String, Object> inputs,
                                       OssStorageService storageService, String connectionCode,
                                       String bucket) throws Exception {
        String prefix = firstNonBlank(str(inputs.get("listPrefix")),
                resolveString(step.getListPrefix(), inputs));
        int maxKeys = step.getListMaxKeys() != null && step.getListMaxKeys() > 0
                ? step.getListMaxKeys() : 100;
        Object inputMax = inputs.get("listMaxKeys");
        if (inputMax != null) {
            maxKeys = Integer.parseInt(String.valueOf(inputMax));
        }
        if (maxKeys > 1000) {
            maxKeys = 1000;
        }
        Iterable<Result<Item>> items = storageService.listObjects(connectionCode, bucket, prefix, maxKeys);
        List<Map<String, Object>> objects = new ArrayList<>();
        for (Result<Item> result : items) {
            Item item = result.get();
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("key", item.objectName());
            row.put("size", item.size());
            row.put("lastModified", item.lastModified() != null ? item.lastModified().toString() : null);
            objects.add(row);
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("bucket", bucket);
        out.put("prefix", StrUtil.blankToDefault(prefix, ""));
        out.put("objects", objects);
        out.put("count", objects.size());
        return out;
    }

    private Map<String, Object> doPresignGet(OssStep step, Map<String, Object> inputs,
                                             OssStorageService storageService, String connectionCode,
                                             String bucket) {
        String objectKey = firstNonBlank(str(inputs.get("objectKey")),
                resolveString(step.getObjectKey(), inputs));
        validateObjectKey(objectKey);
        int expireSeconds = step.getPresignExpireSeconds() != null && step.getPresignExpireSeconds() > 0
                ? step.getPresignExpireSeconds() : 300;
        Object inputExpire = inputs.get("presignExpireSeconds");
        if (inputExpire != null) {
            expireSeconds = Integer.parseInt(String.valueOf(inputExpire));
        }
        YuFlowProperties props = SpringUtil.getBean(YuFlowProperties.class);
        if (props != null && props.getOss().getPresignExpireSeconds() > 0 && expireSeconds <= 0) {
            expireSeconds = props.getOss().getPresignExpireSeconds();
        }
        String url = storageService.presignGetUrl(connectionCode, bucket, objectKey, expireSeconds);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("bucket", bucket);
        result.put("objectKey", objectKey);
        result.put("url", url);
        result.put("expireSeconds", expireSeconds);
        return result;
    }

    private byte[] resolvePutBytes(OssStep step, Map<String, Object> inputs) {
        Object content = inputs.get("content");
        if (content instanceof byte[]) {
            return (byte[]) content;
        }
        if (content != null && !(content instanceof String)) {
            return String.valueOf(content).getBytes(StandardCharsets.UTF_8);
        }
        String contentStr = content != null ? String.valueOf(content) : null;
        if (StrUtil.isNotBlank(contentStr)) {
            return contentStr.getBytes(StandardCharsets.UTF_8);
        }
        Object base64 = inputs.get("contentBase64");
        if (base64 != null && StrUtil.isNotBlank(String.valueOf(base64))) {
            return Base64.decode(String.valueOf(base64));
        }
        String varName = firstNonBlank(str(inputs.get("localBytesVar")), step.getLocalBytesVar());
        if (StrUtil.isNotBlank(varName)) {
            Object val = inputs.get(varName);
            if (val == null) {
                val = getValueByPath(varName, inputs);
            }
            return toBytes(val);
        }
        return null;
    }

    private static byte[] toBytes(Object val) {
        if (val == null) {
            return null;
        }
        if (val instanceof byte[]) {
            return (byte[]) val;
        }
        String s = String.valueOf(val);
        if (StrUtil.isBlank(s)) {
            return new byte[0];
        }
        try {
            return Base64.decode(s);
        } catch (Exception ignored) {
            return s.getBytes(StandardCharsets.UTF_8);
        }
    }

    private static void validateObjectKey(String objectKey) {
        if (StrUtil.isBlank(objectKey)) {
            throw new IllegalArgumentException("objectKey 不能为空");
        }
        if (objectKey.contains("..")) {
            throw new IllegalArgumentException("objectKey 不允许包含 ..");
        }
    }

    private String softFail(OssStep step, ExecutionContext context, String message) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("success", false);
        result.put("error", message);
        Map<String, Object> out = new HashMap<>();
        out.put(PortNames.OUT, result);
        context.setVar(step.getId(), out);
        return PortNames.FAIL;
    }

    private static String resolveString(String template, Map<String, Object> inputs) {
        if (template == null) {
            return null;
        }
        Matcher matcher = VAR_PATTERN.matcher(template);
        StringBuffer sb = new StringBuffer();
        while (matcher.find()) {
            String varName = matcher.group(1).trim();
            Object val = getValueByPath(varName, inputs);
            String replacement = val != null ? String.valueOf(val) : "";
            matcher.appendReplacement(sb, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(sb);
        return sb.toString();
    }

    private static Object getValueByPath(String path, Map<String, Object> inputs) {
        if (path == null || inputs == null || inputs.isEmpty()) {
            return null;
        }
        if (inputs.containsKey(path)) {
            return inputs.get(path);
        }
        String[] parts = path.split("\\.");
        Object current = inputs;
        for (String part : parts) {
            if (current instanceof Map) {
                current = ((Map<?, ?>) current).get(part);
            } else {
                return null;
            }
            if (current == null) {
                return null;
            }
        }
        return current;
    }

    private static String str(Object o) {
        return o == null ? null : String.valueOf(o);
    }

    private static String firstNonBlank(String a, String b) {
        if (StrUtil.isNotBlank(a)) {
            return a;
        }
        return b;
    }
}
