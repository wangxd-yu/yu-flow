package org.yu.flow.module.api.privacy;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.yu.flow.auto.dto.PageBean;
import org.yu.flow.dto.R;
import org.yu.flow.module.host.FlowHostPrincipal;
import org.yu.flow.util.FlowObjectMapperUtil;

import jakarta.annotation.Resource;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 递归拦截响应 JSON：识别隐私字段 → 解密 → 脱敏或明文（可选传输 SM4）。
 */
@Slf4j
@Component
public class PrivacyFieldInterceptor {

    @Resource
    private PrivacyConfigResolver privacyConfigResolver;

    @Resource
    private PrivacyRoleMatcher privacyRoleMatcher;

    @Resource
    private PrivacyCryptoService privacyCryptoService;

    public EffectivePrivacy resolveConfig(org.yu.flow.module.api.domain.FlowApiDO api, boolean useDraft) {
        return privacyConfigResolver.resolve(api, useDraft);
    }

    public PrivacyClass resolveClass(FlowHostPrincipal principal) {
        return privacyRoleMatcher.resolve(principal);
    }

    public byte[] unwrapTransportKey(String header) {
        return privacyCryptoService.unwrapTransportKey(header).orElse(null);
    }

    /**
     * @param wrapTransport JSON 明文档是否封装 SM4；查看页/Excel 传 false
     */
    public Object apply(Object result, EffectivePrivacy cfg, PrivacyClass privacyClass,
                        byte[] transportKey, boolean wrapTransport) {
        if (result == null || cfg == null || !cfg.isEnabled()) {
            return result;
        }
        PrivacyClass effective = privacyClass == null ? PrivacyClass.MASK : privacyClass;
        if (effective == PrivacyClass.REVEAL && wrapTransport && (transportKey == null || transportKey.length == 0)) {
            log.warn("[Privacy] 明文档缺少 X-Privacy-Key，降级为脱敏");
            effective = PrivacyClass.MASK;
        }
        int[] fieldCount = {0};
        Object out = walk(result, cfg, effective, transportKey, wrapTransport, fieldCount);
        if (fieldCount[0] > 0) {
            log.info("[Privacy] class={} fields={} wrapTransport={}", effective, fieldCount[0], wrapTransport);
        }
        return out;
    }

    private Object walk(Object node, EffectivePrivacy cfg, PrivacyClass cls,
                        byte[] transportKey, boolean wrapTransport, int[] fieldCount) {
        if (node == null) {
            return null;
        }
        if (node instanceof R<?> r) {
            @SuppressWarnings("unchecked")
            R<Object> typed = (R<Object>) r;
            typed.setData(walk(typed.getData(), cfg, cls, transportKey, wrapTransport, fieldCount));
            return typed;
        }
        if (node instanceof PageBean<?> page) {
            List<?> items = page.getItems();
            if (items != null) {
                List<Object> out = new ArrayList<>(items.size());
                for (Object item : items) {
                    out.add(walk(item, cfg, cls, transportKey, wrapTransport, fieldCount));
                }
                @SuppressWarnings("unchecked")
                PageBean<Object> typed = (PageBean<Object>) page;
                typed.setItems(out);
            }
            return page;
        }
        if (node instanceof Map<?, ?> map) {
            return walkMap(map, cfg, cls, transportKey, wrapTransport, fieldCount);
        }
        if (node instanceof List<?> list) {
            List<Object> out = new ArrayList<>(list.size());
            for (Object item : list) {
                out.add(walk(item, cfg, cls, transportKey, wrapTransport, fieldCount));
            }
            return out;
        }
        if (node instanceof Object[] arr) {
            Object[] out = new Object[arr.length];
            for (int i = 0; i < arr.length; i++) {
                out[i] = walk(arr[i], cfg, cls, transportKey, wrapTransport, fieldCount);
            }
            return out;
        }
        if (isSimple(node)) {
            return node;
        }
        try {
            Object converted = FlowObjectMapperUtil.flowObjectMapper().convertValue(node, Object.class);
            if (converted == node || isSimple(converted)) {
                return node;
            }
            return walk(converted, cfg, cls, transportKey, wrapTransport, fieldCount);
        } catch (Exception e) {
            return node;
        }
    }

    private Map<String, Object> walkMap(Map<?, ?> map, EffectivePrivacy cfg, PrivacyClass cls,
                                        byte[] transportKey, boolean wrapTransport, int[] fieldCount) {
        Map<String, Object> out = new LinkedHashMap<>();
        for (Map.Entry<?, ?> e : map.entrySet()) {
            if (e.getKey() == null) {
                continue;
            }
            String key = String.valueOf(e.getKey());
            Object value = e.getValue();
            if (cfg.isPrivacyField(key)) {
                String outKey = cfg.outputKey(key);
                out.put(outKey, transformField(outKey, value, cfg, cls, transportKey, wrapTransport, fieldCount));
            } else {
                out.put(key, walk(value, cfg, cls, transportKey, wrapTransport, fieldCount));
            }
        }
        return out;
    }

    private Object transformField(String outputKey, Object value, EffectivePrivacy cfg, PrivacyClass cls,
                                  byte[] transportKey, boolean wrapTransport, int[] fieldCount) {
        if (value == null) {
            return null;
        }
        if (value instanceof Map || value instanceof List) {
            return walk(value, cfg, cls, transportKey, wrapTransport, fieldCount);
        }
        fieldCount[0]++;
        String cipher = String.valueOf(value);
        if (cipher.isBlank()) {
            return value;
        }
        String plain = privacyCryptoService.decryptAtRest(
                cipher, cfg.getDecryptSpec(), cfg.getDecryptKey()).orElse(null);
        if (plain == null) {
            return PrivacyMasker.placeholder();
        }
        if (cls == PrivacyClass.MASK) {
            return PrivacyMasker.mask(plain, outputKey, cfg.getMaskRules());
        }
        if (wrapTransport && transportKey != null) {
            return privacyCryptoService.wrapTransport(plain, transportKey);
        }
        return plain;
    }

    private static boolean isSimple(Object node) {
        return node instanceof String || node instanceof Number || node instanceof Boolean
                || node instanceof Character;
    }
}
