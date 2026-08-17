package org.yu.flow.module.oss.service.impl;

import cn.hutool.core.util.StrUtil;
import jakarta.annotation.Resource;
import jakarta.persistence.criteria.Predicate;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.yu.flow.auto.dto.PageBean;
import org.yu.flow.config.DemoModeGuard;
import org.yu.flow.exception.FlowException;
import org.yu.flow.module.oss.client.MinioClientFactory;
import org.yu.flow.module.oss.client.OssStorageService;
import org.yu.flow.module.oss.domain.OssConnectionDO;
import org.yu.flow.module.oss.dto.OssConnectionDTO;
import org.yu.flow.module.oss.dto.OssConnectionTestResultDTO;
import org.yu.flow.module.oss.query.OssConnectionQueryDTO;
import org.yu.flow.module.oss.repository.OssConnectionRepository;
import org.yu.flow.module.oss.service.OssConnectionService;
import org.yu.flow.util.AesEncryptUtil;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.stream.Collectors;
import org.yu.flow.module.oss.config.ConditionalOnOssEnabled;

@Slf4j
@ConditionalOnOssEnabled
@Service
public class OssConnectionServiceImpl implements OssConnectionService {

    private static final ZoneId ZONE_SH = ZoneId.of("Asia/Shanghai");

    @Resource
    private OssConnectionRepository ossConnectionRepository;

    @Resource
    private AesEncryptUtil aesEncryptUtil;

    @Resource
    private DemoModeGuard demoModeGuard;

    @Resource
    private MinioClientFactory minioClientFactory;

    @Resource
    private OssStorageService ossStorageService;

    @Override
    @Transactional
    public OssConnectionDO save(OssConnectionDO connectionDO) {
        validateBasic(connectionDO);
        if (ossConnectionRepository.existsByCode(connectionDO.getCode())) {
            throw new FlowException("OSS_CODE_DUPLICATED", "连接编码已存在: " + connectionDO.getCode());
        }
        if (connectionDO.getEnabled() == null) {
            connectionDO.setEnabled(true);
        }
        if (connectionDO.getPathStyle() == null) {
            connectionDO.setPathStyle(true);
        }
        if (connectionDO.getDeleted() == null) {
            connectionDO.setDeleted(0);
        }
        if (StrUtil.isBlank(connectionDO.getPublicAccessMode())) {
            connectionDO.setPublicAccessMode(OssConnectionDO.ACCESS_MODE_NGINX_PROXY);
        }
        if (StrUtil.isBlank(connectionDO.getPrivateDownloadMode())) {
            connectionDO.setPrivateDownloadMode(OssConnectionDO.PRIVATE_DOWNLOAD_STREAM);
        }
        if (connectionDO.getPresignExpireSeconds() == null) {
            connectionDO.setPresignExpireSeconds(300);
        }
        connectionDO.setHealthStatus(OssConnectionDO.HEALTH_UNKNOWN);
        connectionDO.setSecretKey(encryptIfPresent(connectionDO.getSecretKey()));
        LocalDateTime now = LocalDateTime.now(ZONE_SH);
        connectionDO.setCreateTime(now);
        connectionDO.setUpdateTime(now);
        return ossConnectionRepository.save(connectionDO);
    }

    @Override
    @Transactional
    public OssConnectionDO update(OssConnectionDO connectionDO) {
        demoModeGuard.checkModifyOrDelete(connectionDO.getId(), "OSS 连接");
        OssConnectionDO existing = requireConnection(connectionDO.getId());

        if (connectionDO.getName() != null) {
            existing.setName(connectionDO.getName());
        }
        if (connectionDO.getCode() != null && !connectionDO.getCode().equals(existing.getCode())) {
            if (ossConnectionRepository.existsByCode(connectionDO.getCode())) {
                throw new FlowException("OSS_CODE_DUPLICATED", "连接编码已存在: " + connectionDO.getCode());
            }
            minioClientFactory.invalidate(existing.getCode());
            existing.setCode(connectionDO.getCode());
        }
        if (connectionDO.getEndpoint() != null) {
            existing.setEndpoint(connectionDO.getEndpoint());
        }
        if (connectionDO.getAccessKey() != null) {
            existing.setAccessKey(connectionDO.getAccessKey());
        }
        if (StrUtil.isNotBlank(connectionDO.getSecretKey())) {
            existing.setSecretKey(aesEncryptUtil.encrypt(connectionDO.getSecretKey()));
        }
        if (connectionDO.getRegion() != null) {
            existing.setRegion(connectionDO.getRegion());
        }
        if (connectionDO.getPathStyle() != null) {
            existing.setPathStyle(connectionDO.getPathStyle());
        }
        if (connectionDO.getPublicBucket() != null) {
            existing.setPublicBucket(connectionDO.getPublicBucket());
        }
        if (connectionDO.getPrivateBucket() != null) {
            existing.setPrivateBucket(connectionDO.getPrivateBucket());
        }
        if (connectionDO.getPublicBaseUrl() != null) {
            existing.setPublicBaseUrl(connectionDO.getPublicBaseUrl());
        }
        if (connectionDO.getKeyPrefix() != null) {
            existing.setKeyPrefix(connectionDO.getKeyPrefix());
        }
        if (connectionDO.getPublicAccessMode() != null) {
            existing.setPublicAccessMode(connectionDO.getPublicAccessMode());
        }
        if (connectionDO.getPrivateDownloadMode() != null) {
            existing.setPrivateDownloadMode(connectionDO.getPrivateDownloadMode());
        }
        if (connectionDO.getPresignExpireSeconds() != null) {
            existing.setPresignExpireSeconds(connectionDO.getPresignExpireSeconds());
        }
        if (connectionDO.getEnabled() != null) {
            existing.setEnabled(connectionDO.getEnabled());
        }
        if (connectionDO.getInfo() != null) {
            existing.setInfo(connectionDO.getInfo());
        }
        existing.setHealthStatus(OssConnectionDO.HEALTH_UNKNOWN);
        existing.setUpdateTime(LocalDateTime.now(ZONE_SH));

        OssConnectionDO updated = ossConnectionRepository.save(existing);
        minioClientFactory.invalidate(updated.getCode());
        return updated;
    }

    @Override
    @Transactional
    public void delete(String id) {
        demoModeGuard.checkModifyOrDelete(id, "OSS 连接");
        OssConnectionDO existing = ossConnectionRepository.findById(id).orElse(null);
        ossConnectionRepository.deleteById(id);
        if (existing != null) {
            minioClientFactory.invalidate(existing.getCode());
        }
    }

    @Override
    @Transactional
    public void batchDelete(List<String> ids) {
        if (ids == null || ids.isEmpty()) {
            return;
        }
        ids.forEach(id -> demoModeGuard.checkModifyOrDelete(id, "OSS 连接"));
        for (String id : ids) {
            delete(id);
        }
    }

    @Override
    public OssConnectionDO findById(String id) {
        return ossConnectionRepository.findById(id).orElse(null);
    }

    @Override
    public OssConnectionDO findByCode(String code) {
        if (code == null || code.isBlank()) {
            return null;
        }
        return ossConnectionRepository.findByCode(code.trim()).orElse(null);
    }

    @Override
    public PageBean<OssConnectionDTO> findPage(OssConnectionQueryDTO queryDTO) {
        Pageable pageable = PageRequest.of(
                queryDTO.getPage(), queryDTO.getSize(),
                Sort.by(Sort.Direction.DESC, "createTime")
        );
        Specification<OssConnectionDO> spec = (root, cq, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (StrUtil.isNotBlank(queryDTO.getName())) {
                predicates.add(cb.like(root.get("name"), "%" + queryDTO.getName() + "%"));
            }
            if (StrUtil.isNotBlank(queryDTO.getCode())) {
                predicates.add(cb.like(root.get("code"), "%" + queryDTO.getCode() + "%"));
            }
            if (queryDTO.getEnabled() != null) {
                predicates.add(cb.equal(root.get("enabled"), queryDTO.getEnabled()));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
        Page<OssConnectionDO> page = ossConnectionRepository.findAll(spec, pageable);
        List<OssConnectionDTO> items = page.getContent().stream()
                .map(OssConnectionDTO::fromDO)
                .collect(Collectors.toList());
        return new PageBean<>(items, page.getNumber(), page.getSize(),
                page.getTotalPages(), page.getTotalElements());
    }

    @Override
    public List<OssConnectionDTO> listEnabled() {
        return ossConnectionRepository.findByEnabled(true).stream()
                .map(OssConnectionDTO::fromDO)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public OssConnectionDO enable(String id) {
        demoModeGuard.checkModifyOrDelete(id, "OSS 连接");
        OssConnectionDO connection = requireConnection(id);
        connection.setEnabled(true);
        connection.setUpdateTime(LocalDateTime.now(ZONE_SH));
        return ossConnectionRepository.save(connection);
    }

    @Override
    @Transactional
    public OssConnectionDO disable(String id) {
        demoModeGuard.checkModifyOrDelete(id, "OSS 连接");
        OssConnectionDO connection = requireConnection(id);
        connection.setEnabled(false);
        connection.setUpdateTime(LocalDateTime.now(ZONE_SH));
        OssConnectionDO saved = ossConnectionRepository.save(connection);
        minioClientFactory.invalidate(saved.getCode());
        return saved;
    }

    @Override
    public OssConnectionTestResultDTO testConnection(OssConnectionDO probe) {
        validateBasic(probe);
        String secret = probe.getSecretKey();
        if (StrUtil.isBlank(secret) && StrUtil.isNotBlank(probe.getId())) {
            OssConnectionDO stored = ossConnectionRepository.findById(probe.getId()).orElse(null);
            if (stored != null && StrUtil.isNotBlank(stored.getSecretKey())) {
                secret = aesEncryptUtil.decrypt(stored.getSecretKey());
            }
        }
        try {
            io.minio.MinioClient client = minioClientFactory.buildEphemeral(probe, secret);
            OssConnectionTestResultDTO result = new OssConnectionTestResultDTO();
            result.setSuccess(true);
            result.setMessage("连接测试成功");
            LinkedHashMap<String, OssConnectionTestResultDTO.OssBucketStatusDTO> buckets = new LinkedHashMap<>();
            addBucketStatus(buckets, "public", probe.getPublicBucket(), client);
            addBucketStatus(buckets, "private", probe.getPrivateBucket(), client);
            result.setBuckets(buckets);
            recordTestResult(probe.getId(), true, null);
            return result;
        } catch (FlowException e) {
            recordTestResult(probe.getId(), false, e.getMessage());
            throw e;
        } catch (Exception e) {
            String msg = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
            recordTestResult(probe.getId(), false, msg);
            throw new FlowException("OSS_CONNECT_FAILED", "OSS 连接失败: " + msg);
        }
    }

    private void addBucketStatus(LinkedHashMap<String, OssConnectionTestResultDTO.OssBucketStatusDTO> map,
                                 String key, String bucket, io.minio.MinioClient client) {
        if (StrUtil.isBlank(bucket)) {
            return;
        }
        boolean exists = ossStorageService.bucketExists(client, bucket);
        String policy = ossStorageService.getBucketPolicySafe(client, bucket);
        map.put(key, new OssConnectionTestResultDTO.OssBucketStatusDTO()
                .setBucket(bucket)
                .setExists(exists)
                .setPolicySummary(summarizePolicy(policy)));
    }

    private static String summarizePolicy(String policy) {
        if (StrUtil.isBlank(policy)) {
            return "无策略或不可读";
        }
        if (policy.contains("s3:GetObject") && policy.contains("\"*\"")) {
            return "可能允许匿名读";
        }
        return StrUtil.maxLength(policy.replaceAll("\\s+", " "), 200);
    }

    private void recordTestResult(String id, boolean success, String errorMsg) {
        if (StrUtil.isBlank(id)) {
            return;
        }
        try {
            OssConnectionDO stored = ossConnectionRepository.findById(id).orElse(null);
            if (stored == null) {
                return;
            }
            stored.setHealthStatus(success ? OssConnectionDO.HEALTH_HEALTHY : OssConnectionDO.HEALTH_UNHEALTHY);
            stored.setLastErrorMsg(success ? null : StrUtil.maxLength(errorMsg, 1000));
            stored.setLastTestTime(LocalDateTime.now(ZONE_SH));
            ossConnectionRepository.save(stored);
        } catch (Exception e) {
            log.warn("[OSS] 回写连接测试结果失败 id={}: {}", id, e.getMessage());
        }
    }

    private void validateBasic(OssConnectionDO connectionDO) {
        if (connectionDO == null) {
            throw new FlowException("OSS_PARAM_REQUIRED", "连接配置不能为空");
        }
        if (StrUtil.isBlank(connectionDO.getCode())) {
            throw new FlowException("OSS_CODE_REQUIRED", "连接编码 code 不能为空");
        }
        if (StrUtil.isBlank(connectionDO.getEndpoint())) {
            throw new FlowException("OSS_ENDPOINT_REQUIRED", "endpoint 不能为空");
        }
    }

    private String encryptIfPresent(String plain) {
        return StrUtil.isNotBlank(plain) ? aesEncryptUtil.encrypt(plain) : null;
    }

    private OssConnectionDO requireConnection(String id) {
        return ossConnectionRepository.findById(id)
                .orElseThrow(() -> new FlowException("OSS_CONNECTION_NOT_FOUND", "OSS 连接不存在: " + id));
    }

    @Override
    public List<String> listBucketsByCode(String code) {
        OssConnectionDO conn = findByCode(code);
        if (conn == null) {
            throw new FlowException("OSS_CONN_NOT_FOUND", "OSS 连接不存在: " + code);
        }
        List<String> list = new ArrayList<>();
        try {
            io.minio.MinioClient client = minioClientFactory.getClient(conn.getCode());
            List<io.minio.messages.Bucket> buckets = client.listBuckets();
            if (buckets != null) {
                for (io.minio.messages.Bucket b : buckets) {
                    if (StrUtil.isNotBlank(b.name())) {
                        list.add(b.name());
                    }
                }
            }
            log.info("[OSS] listBucketsByCode success code={} count={} buckets={}", code, list.size(), list);
        } catch (Exception e) {
            log.error("[OSS] listBucketsByCode failed code=" + code + ": " + e.getMessage(), e);
        }
        if (StrUtil.isNotBlank(conn.getPublicBucket()) && !list.contains(conn.getPublicBucket())) {
            list.add(0, conn.getPublicBucket());
        }
        if (StrUtil.isNotBlank(conn.getPrivateBucket()) && !list.contains(conn.getPrivateBucket())) {
            if (StrUtil.isNotBlank(conn.getPublicBucket()) && list.size() > 1) {
                list.add(1, conn.getPrivateBucket());
            } else {
                list.add(0, conn.getPrivateBucket());
            }
        }
        return list;
    }
}
