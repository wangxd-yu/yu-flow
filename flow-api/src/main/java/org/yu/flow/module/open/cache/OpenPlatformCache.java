package org.yu.flow.module.open.cache;

import cn.hutool.core.util.StrUtil;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import lombok.Builder;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.yu.flow.module.open.domain.FlowOpenApiGrantDO;
import org.yu.flow.module.open.domain.FlowOpenCredentialDO;
import org.yu.flow.module.open.domain.FlowOpenPlatformDO;
import org.yu.flow.module.open.repository.FlowOpenApiGrantRepository;
import org.yu.flow.module.open.repository.FlowOpenCredentialRepository;
import org.yu.flow.module.open.repository.FlowOpenPlatformRepository;
import org.yu.flow.util.AesEncryptUtil;

import jakarta.annotation.Resource;
import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * 开放平台凭证 L1 缓存 + Redis Pub/Sub 失效。
 */
@Slf4j
@Component
public class OpenPlatformCache {

    public static final String REFRESH_TOPIC = "flow:open:credential:refresh:topic";

    @Resource
    private FlowOpenCredentialRepository credentialRepository;
    @Resource
    private FlowOpenPlatformRepository platformRepository;
    @Resource
    private FlowOpenApiGrantRepository grantRepository;
    @Resource
    private AesEncryptUtil aesEncryptUtil;
    @Resource
    private StringRedisTemplate stringRedisTemplate;

    private final Cache<String, CachedCredential> byAppKey = Caffeine.newBuilder()
            .maximumSize(2000)
            .expireAfterWrite(10, TimeUnit.MINUTES)
            .build();

    /** platformId → (apiId → allowMethods，空串表示跟随接口 method) */
    private final Cache<String, Map<String, String>> grantsByPlatform = Caffeine.newBuilder()
            .maximumSize(500)
            .expireAfterWrite(10, TimeUnit.MINUTES)
            .build();

    public CachedCredential getByAppKey(String appKey) {
        if (StrUtil.isBlank(appKey)) {
            return null;
        }
        return byAppKey.get(appKey, this::loadCredential);
    }

    public Map<String, String> getGrantedApiMethods(String platformId) {
        if (StrUtil.isBlank(platformId)) {
            return Map.of();
        }
        Map<String, String> m = grantsByPlatform.get(platformId, this::loadGrants);
        return m != null ? m : Map.of();
    }

    public Set<String> getGrantedApiIds(String platformId) {
        return getGrantedApiMethods(platformId).keySet();
    }

    public boolean isApiGranted(String platformId, String apiId) {
        return getGrantedApiMethods(platformId).containsKey(apiId);
    }

    /**
     * @return 授权上的 allowMethods；未授权返回 null；授权但未限制返回 ""
     */
    public String getAllowMethods(String platformId, String apiId) {
        if (StrUtil.isBlank(platformId) || StrUtil.isBlank(apiId)) {
            return null;
        }
        Map<String, String> m = getGrantedApiMethods(platformId);
        if (!m.containsKey(apiId)) {
            return null;
        }
        String allow = m.get(apiId);
        return allow == null ? "" : allow;
    }

    public void invalidateAll() {
        byAppKey.invalidateAll();
        grantsByPlatform.invalidateAll();
    }

    public void publishRefresh() {
        try {
            stringRedisTemplate.convertAndSend(REFRESH_TOPIC, "REFRESH");
        } catch (Exception e) {
            log.warn("[OpenPlatformCache] 发布刷新失败: {}", e.getMessage());
        }
        invalidateAll();
    }

    public void onRemoteRefresh() {
        log.info("[OpenPlatformCache] 收到集群刷新");
        invalidateAll();
    }

    private CachedCredential loadCredential(String appKey) {
        FlowOpenCredentialDO cred = credentialRepository.findByAppKey(appKey).orElse(null);
        if (cred == null) {
            return null;
        }
        FlowOpenPlatformDO platform = platformRepository.findById(cred.getPlatformId()).orElse(null);
        if (platform == null) {
            return null;
        }
        String secret;
        try {
            secret = aesEncryptUtil.decrypt(cred.getAppSecretEnc());
        } catch (Exception e) {
            log.error("[OpenPlatformCache] 解密 secret 失败 appKey={}", appKey);
            return null;
        }
        List<String> ips = parseIpList(platform.getIpAllowlist());
        return CachedCredential.builder()
                .credentialId(cred.getId())
                .platformId(platform.getId())
                .platformName(platform.getName())
                .appKey(cred.getAppKey())
                .appSecret(secret)
                .credentialStatus(cred.getStatus())
                .platformStatus(platform.getStatus())
                .platformExpireAt(platform.getExpireAt())
                .credentialExpireAt(cred.getExpireAt())
                .ipAllowlist(ips)
                .openCallLogEnabled(platform.getOpenCallLogEnabled())
                .rateLimitQps(platform.getRateLimitQps())
                .build();
    }

    private Map<String, String> loadGrants(String platformId) {
        Map<String, String> out = new LinkedHashMap<>();
        for (FlowOpenApiGrantDO g : grantRepository.findByPlatformId(platformId)) {
            if (StrUtil.isBlank(g.getApiId())) {
                continue;
            }
            out.put(g.getApiId(), StrUtil.nullToEmpty(g.getAllowMethods()));
        }
        return out;
    }

    private static List<String> parseIpList(String json) {
        if (StrUtil.isBlank(json)) {
            return List.of();
        }
        String s = json.trim();
        if (s.startsWith("[")) {
            s = s.substring(1, s.endsWith("]") ? s.length() - 1 : s.length());
        }
        if (StrUtil.isBlank(s)) {
            return List.of();
        }
        List<String> out = new ArrayList<>();
        for (String part : s.split(",")) {
            String p = part.trim().replace("\"", "");
            if (StrUtil.isNotBlank(p)) {
                out.add(p);
            }
        }
        return out;
    }

    @Data
    @Builder
    public static class CachedCredential {
        private String credentialId;
        private String platformId;
        private String platformName;
        private String appKey;
        private String appSecret;
        private Integer credentialStatus;
        private Integer platformStatus;
        private Date platformExpireAt;
        private Date credentialExpireAt;
        private List<String> ipAllowlist;
        /** 平台级入站日志：null/1=开，0=关 */
        private Integer openCallLogEnabled;
        private Integer rateLimitQps;
    }
}
