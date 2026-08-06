package org.yu.flow.module.oss.client;

import cn.hutool.core.util.StrUtil;
import io.minio.MinioClient;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.yu.flow.exception.FlowException;
import org.yu.flow.module.oss.domain.OssConnectionDO;
import org.yu.flow.module.oss.repository.OssConnectionRepository;
import org.yu.flow.util.AesEncryptUtil;

import java.util.concurrent.ConcurrentHashMap;

/**
 * MinIO 客户端工厂（按连接 code 缓存）。
 */
@Slf4j
@Component
public class MinioClientFactory {

    private final ConcurrentHashMap<String, MinioClient> cache = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, MinioClient> externalCache = new ConcurrentHashMap<>();

    @Resource
    private OssConnectionRepository ossConnectionRepository;

    @Resource
    private AesEncryptUtil aesEncryptUtil;

    public MinioClient getClient(String connectionCode) {
        if (StrUtil.isBlank(connectionCode)) {
            throw new FlowException("OSS_CONNECTION_REQUIRED", "连接编码不能为空");
        }
        return cache.computeIfAbsent(connectionCode.trim(), this::buildClient);
    }

    public MinioClient getExternalClient(String connectionCode) {
        if (StrUtil.isBlank(connectionCode)) {
            throw new FlowException("OSS_CONNECTION_REQUIRED", "连接编码不能为空");
        }
        return externalCache.computeIfAbsent(connectionCode.trim(), this::buildExternalClient);
    }

    public void invalidate(String connectionCode) {
        if (StrUtil.isNotBlank(connectionCode)) {
            cache.remove(connectionCode.trim());
            externalCache.remove(connectionCode.trim());
        }
    }

    public MinioClient buildEphemeral(OssConnectionDO connection, String plainSecret) {
        if (connection == null || StrUtil.isBlank(connection.getEndpoint())) {
            throw new FlowException("OSS_ENDPOINT_REQUIRED", "endpoint 不能为空");
        }
        return buildFromParams(connection.getEndpoint(), connection.getAccessKey(), plainSecret,
                connection.getRegion(), connection.getPathStyle());
    }

    private MinioClient buildClient(String code) {
        OssConnectionDO connection = ossConnectionRepository.findByCode(code)
                .orElseThrow(() -> new FlowException("OSS_CONNECTION_NOT_FOUND", "OSS 连接不存在: " + code));
        if (connection.getEnabled() == null || !connection.getEnabled()) {
            throw new FlowException("OSS_CONNECTION_DISABLED", "OSS 连接已停用: " + code);
        }
        String secret = StrUtil.isNotBlank(connection.getSecretKey())
                ? aesEncryptUtil.decrypt(connection.getSecretKey()) : "";
        return buildFromParams(connection.getEndpoint(), connection.getAccessKey(), secret,
                connection.getRegion(), connection.getPathStyle());
    }

    private MinioClient buildExternalClient(String code) {
        OssConnectionDO connection = ossConnectionRepository.findByCode(code)
                .orElseThrow(() -> new FlowException("OSS_CONNECTION_NOT_FOUND", "OSS 连接不存在: " + code));
        if (connection.getEnabled() == null || !connection.getEnabled()) {
            throw new FlowException("OSS_CONNECTION_DISABLED", "OSS 连接已停用: " + code);
        }
        String secret = StrUtil.isNotBlank(connection.getSecretKey())
                ? aesEncryptUtil.decrypt(connection.getSecretKey()) : "";
        // 关键：如果配置了公有访问基址，外网签名 Client 强制使用公有基址作为 Endpoint！
        String endpoint = StrUtil.isNotBlank(connection.getPublicBaseUrl()) ? connection.getPublicBaseUrl() : connection.getEndpoint();
        return buildFromParams(endpoint, connection.getAccessKey(), secret,
                connection.getRegion(), connection.getPathStyle());
    }

    private static MinioClient buildFromParams(String endpoint, String accessKey, String secret,
                                               String region, Boolean pathStyle) {
        MinioClient.Builder builder = MinioClient.builder()
                .endpoint(endpoint)
                .credentials(StrUtil.blankToDefault(accessKey, ""), StrUtil.blankToDefault(secret, ""));
        if (Boolean.TRUE.equals(pathStyle)) {
            builder.region(StrUtil.blankToDefault(region, "us-east-1"));
        } else if (StrUtil.isNotBlank(region)) {
            builder.region(region);
        }
        return builder.build();
    }
}
