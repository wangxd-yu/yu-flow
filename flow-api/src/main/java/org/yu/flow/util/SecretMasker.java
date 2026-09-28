package org.yu.flow.util;

import com.fasterxml.jackson.core.io.JsonStringEncoder;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;

/**
 * 按值脱敏：把本次执行中用到的敏感值（如敏感环境变量）从落库文本里替换掉。
 *
 * <p>同时替换原文与 JSON 转义形式，因为轨迹是序列化后的 JSON，值里的引号、反斜杠会被转义。</p>
 */
public final class SecretMasker {

    public static final String MASK = "******";

    /** 过短的值替换会误伤正常文本（如 "1"、"on"），不参与按值脱敏 */
    static final int MIN_SECRET_LENGTH = 4;

    private static final ObjectMapper COPY_MAPPER = FlowObjectMapperUtil.flowObjectMapper().copy()
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

    private SecretMasker() {
    }

    public static String mask(String text, Collection<String> secrets) {
        if (text == null || text.isEmpty() || secrets == null || secrets.isEmpty()) {
            return text;
        }
        List<String> ordered = maskable(secrets);
        String result = text;
        for (String secret : ordered) {
            result = result.replace(secret, MASK);
            String escaped = new String(JsonStringEncoder.getInstance().quoteAsString(secret));
            if (!escaped.equals(secret)) {
                result = result.replace(escaped, MASK);
            }
        }
        return result;
    }

    /**
     * 返回脱敏后的副本（经 JSON 往返），供调试接口直接返回轨迹对象；没有需要脱敏的内容时原样返回。
     *
     * @throws IllegalStateException 往返失败时不返回原对象，避免把敏感值带出去
     */
    @SuppressWarnings("unchecked")
    public static <T> T maskCopy(T value, Collection<String> secrets) {
        if (value == null || secrets == null || maskable(secrets).isEmpty()) {
            return value;
        }
        try {
            String json = COPY_MAPPER.writeValueAsString(value);
            String masked = mask(json, secrets);
            return masked.equals(json) ? value : (T) COPY_MAPPER.readValue(masked, value.getClass());
        } catch (Exception e) {
            throw new IllegalStateException("结果包含敏感值且脱敏失败，已隐藏结果", e);
        }
    }

    /** 长的先替换，避免一个密钥是另一个的子串时只替换掉一半 */
    private static List<String> maskable(Collection<String> secrets) {
        List<String> ordered = new ArrayList<>();
        for (String s : secrets) {
            if (s != null && s.length() >= MIN_SECRET_LENGTH) {
                ordered.add(s);
            }
        }
        ordered.sort(Comparator.comparingInt(String::length).reversed());
        return ordered;
    }
}
