package org.yu.flow.module.api.privacy;

import cn.hutool.core.util.StrUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.yu.flow.auto.dto.PageBean;
import org.yu.flow.dto.R;
import org.yu.flow.module.host.FlowHostPrincipal;
import org.yu.flow.module.host.PrivacyAccessRule;
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
        return resolveDecision(principal, null).getPrivacyClass();
    }

    public PrivacyDecision resolveDecision(FlowHostPrincipal principal, EffectivePrivacy privacy) {
        List<PrivacyAccessRule> rules = privacy == null ? null : privacy.getAccessRules();
        return privacyRoleMatcher.resolveDecision(principal, rules);
    }

    public byte[] unwrapTransportKey(String header) {
        return privacyCryptoService.unwrapTransportKey(header).orElse(null);
    }

    /**
     * @param wrapTransport JSON 明文档是否封装 SM4；查看页/Excel 传 false
     */
    public Object apply(Object result, EffectivePrivacy cfg, PrivacyClass privacyClass,
                        byte[] transportKey, boolean wrapTransport) {
        return apply(result, cfg, PrivacyDecision.of(privacyClass), transportKey, wrapTransport);
    }

    public Object apply(Object result, EffectivePrivacy cfg, PrivacyDecision decision,
                        byte[] transportKey, boolean wrapTransport) {
        if (result == null || cfg == null || !cfg.isEnabled()) {
            return result;
        }
        PrivacyDecision effective = decision == null ? PrivacyDecision.mask() : decision;
        PrivacyClass resolved = effective.getPrivacyClass();
        String ruleName = StrUtil.blankToDefault(effective.getMatchedRuleName(), "-");
        boolean degraded = false;
        if (resolved == PrivacyClass.REVEAL && wrapTransport && (transportKey == null || transportKey.length == 0)) {
            log.warn("[Privacy] 明文档缺少 X-Privacy-Key，降级为脱敏");
            effective = PrivacyDecision.of(PrivacyClass.MASK, effective.getFieldActions(),
                    effective.getMatchedRuleName());
            degraded = true;
        }
        int[] fieldCount = {0};
        Object out = walk(result, cfg, effective, transportKey, wrapTransport, fieldCount);
        if (fieldCount[0] > 0) {
            log.info("[Privacy] resolved={} effective={} rule={} fields={} wrapTransport={}{}",
                    resolved, effective.getPrivacyClass(), ruleName, fieldCount[0], wrapTransport,
                    degraded ? " degraded=missing-transport-key" : "");
        }
        return out;
    }

    private Object walk(Object node, EffectivePrivacy cfg, PrivacyDecision decision,
                        byte[] transportKey, boolean wrapTransport, int[] fieldCount) {
        if (node == null) {
            return null;
        }
        if (node instanceof R<?> r) {
            @SuppressWarnings("unchecked")
            R<Object> typed = (R<Object>) r;
            typed.setData(walk(typed.getData(), cfg, decision, transportKey, wrapTransport, fieldCount));
            return typed;
        }
        if (node instanceof PageBean<?> page) {
            List<?> items = page.getItems();
            if (items != null) {
                List<Object> out = new ArrayList<>(items.size());
                for (Object item : items) {
                    out.add(walk(item, cfg, decision, transportKey, wrapTransport, fieldCount));
                }
                @SuppressWarnings("unchecked")
                PageBean<Object> typed = (PageBean<Object>) page;
                typed.setItems(out);
            }
            return page;
        }
        if (node instanceof Map<?, ?> map) {
            return walkMap(map, cfg, decision, transportKey, wrapTransport, fieldCount);
        }
        if (node instanceof List<?> list) {
            List<Object> out = new ArrayList<>(list.size());
            for (Object item : list) {
                out.add(walk(item, cfg, decision, transportKey, wrapTransport, fieldCount));
            }
            return out;
        }
        if (node instanceof Object[] arr) {
            Object[] out = new Object[arr.length];
            for (int i = 0; i < arr.length; i++) {
                out[i] = walk(arr[i], cfg, decision, transportKey, wrapTransport, fieldCount);
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
            return walk(converted, cfg, decision, transportKey, wrapTransport, fieldCount);
        } catch (Exception e) {
            return node;
        }
    }

    private Map<String, Object> walkMap(Map<?, ?> map, EffectivePrivacy cfg, PrivacyDecision decision,
                                        byte[] transportKey, boolean wrapTransport, int[] fieldCount) {
        Map<String, Object> out = new LinkedHashMap<>();
        for (Map.Entry<?, ?> e : map.entrySet()) {
            if (e.getKey() == null) {
                continue;
            }
            String key = String.valueOf(e.getKey());
            Object value = e.getValue();
            boolean privacyField = cfg.isPrivacyField(key);
            String outKey = privacyField ? cfg.outputKey(key) : key;
            if (decision.drops(outKey, key)) {
                fieldCount[0]++;
                continue;
            }
            if (privacyField) {
                PrivacyClass fieldClass = decision.classForField(outKey, key);
                out.put(outKey, transformField(outKey, value, cfg, fieldClass, transportKey, wrapTransport, fieldCount,
                        decision));
            } else {
                out.put(key, walk(value, cfg, decision, transportKey, wrapTransport, fieldCount));
            }
        }
        return out;
    }

    private Object transformField(String outputKey, Object value, EffectivePrivacy cfg, PrivacyClass cls,
                                  byte[] transportKey, boolean wrapTransport, int[] fieldCount,
                                  PrivacyDecision decision) {
        if (value == null) {
            return null;
        }
        if (value instanceof Map || value instanceof List) {
            return walk(value, cfg, decision, transportKey, wrapTransport, fieldCount);
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
        boolean canReveal = cls == PrivacyClass.REVEAL
                && (!wrapTransport || (transportKey != null && transportKey.length > 0));
        if (!canReveal) {
            return PrivacyMasker.mask(plain, outputKey, cfg.getMaskRules());
        }
        if (wrapTransport) {
            return privacyCryptoService.wrapTransport(plain, transportKey);
        }
        return plain;
    }

    private static boolean isSimple(Object node) {
        return node instanceof String || node instanceof Number || node instanceof Boolean
                || node instanceof Character;
    }
}
