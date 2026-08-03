package org.yu.flow.module.oss.client;

import cn.hutool.core.util.StrUtil;
import io.minio.BucketExistsArgs;
import io.minio.GetObjectArgs;
import io.minio.GetObjectResponse;
import io.minio.GetPresignedObjectUrlArgs;
import io.minio.ListObjectsArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import io.minio.Result;
import io.minio.errors.ErrorResponseException;
import io.minio.http.Method;
import io.minio.messages.Item;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.yu.flow.exception.FlowException;

import java.io.InputStream;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
public class OssStorageService {

    /** MinIO SDK 对大文件自动 multipart 的分片大小（10MB） */
    private static final long PUT_PART_SIZE = 10L * 1024 * 1024;

    @Resource
    private MinioClientFactory minioClientFactory;

    public Iterable<Result<Item>> listObjects(String connectionCode, String bucket, String prefix, int maxKeys) {
        if (maxKeys <= 0) {
            maxKeys = 100;
        }
        if (maxKeys > 1000) {
            maxKeys = 1000;
        }
        try {
            MinioClient client = minioClientFactory.getClient(connectionCode);
            ListObjectsArgs.Builder builder = ListObjectsArgs.builder()
                    .bucket(bucket)
                    .maxKeys(maxKeys);
            if (StrUtil.isNotBlank(prefix)) {
                builder.prefix(prefix);
            }
            return client.listObjects(builder.build());
        } catch (FlowException e) {
            throw e;
        } catch (Exception e) {
            log.warn("[OSS] listObjects failed bucket={} prefix={}: {}", bucket, prefix, e.getMessage());
            throw new FlowException("OSS_LIST_FAILED", "列举对象失败: " + e.getMessage(), e);
        }
    }

    public void putObject(String connectionCode, InputStream stream, long size, String contentType,
                          String bucket, String key) {
        try {
            MinioClient client = minioClientFactory.getClient(connectionCode);
            long partSize = size < 0 || size > PUT_PART_SIZE ? PUT_PART_SIZE : -1;
            PutObjectArgs.Builder builder = PutObjectArgs.builder()
                    .bucket(bucket)
                    .object(key)
                    .stream(stream, size, partSize);
            if (StrUtil.isNotBlank(contentType)) {
                builder.contentType(contentType);
            }
            client.putObject(builder.build());
        } catch (FlowException e) {
            throw e;
        } catch (Exception e) {
            log.warn("[OSS] putObject failed bucket={} key={}: {}", bucket, key, e.getMessage());
            throw new FlowException("OSS_PUT_FAILED", "上传对象失败: " + e.getMessage(), e);
        }
    }

    public GetObjectResponse getObject(String connectionCode, String bucket, String key) {
        try {
            MinioClient client = minioClientFactory.getClient(connectionCode);
            return client.getObject(GetObjectArgs.builder()
                    .bucket(bucket)
                    .object(key)
                    .build());
        } catch (FlowException e) {
            throw e;
        } catch (Exception e) {
            log.warn("[OSS] getObject failed bucket={} key={}: {}", bucket, key, e.getMessage());
            throw new FlowException("OSS_GET_FAILED", "读取对象失败: " + e.getMessage(), e);
        }
    }

    public String presignGetUrl(String connectionCode, String bucket, String key, int expireSeconds) {
        if (expireSeconds <= 0) {
            expireSeconds = 300;
        }
        try {
            MinioClient client = minioClientFactory.getClient(connectionCode);
            return client.getPresignedObjectUrl(GetPresignedObjectUrlArgs.builder()
                    .method(Method.GET)
                    .bucket(bucket)
                    .object(key)
                    .expiry(expireSeconds, TimeUnit.SECONDS)
                    .build());
        } catch (FlowException e) {
            throw e;
        } catch (Exception e) {
            log.warn("[OSS] presignGetUrl failed bucket={} key={}: {}", bucket, key, e.getMessage());
            throw new FlowException("OSS_PRESIGN_FAILED", "生成预签名 URL 失败: " + e.getMessage(), e);
        }
    }

    public void removeObject(String connectionCode, String bucket, String key) {
        try {
            MinioClient client = minioClientFactory.getClient(connectionCode);
            client.removeObject(RemoveObjectArgs.builder()
                    .bucket(bucket)
                    .object(key)
                    .build());
        } catch (FlowException e) {
            throw e;
        } catch (Exception e) {
            log.warn("[OSS] removeObject failed bucket={} key={}: {}", bucket, key, e.getMessage());
            throw new FlowException("OSS_DELETE_FAILED", "删除对象失败: " + e.getMessage(), e);
        }
    }

    public boolean bucketExists(MinioClient client, String bucket) {
        if (StrUtil.isBlank(bucket) || client == null) {
            return false;
        }
        try {
            return client.bucketExists(BucketExistsArgs.builder().bucket(bucket).build());
        } catch (Exception e) {
            log.warn("[OSS] bucketExists failed bucket={}: {}", bucket, e.getMessage());
            return false;
        }
    }

    public String getBucketPolicySafe(MinioClient client, String bucket) {
        if (StrUtil.isBlank(bucket) || client == null) {
            return null;
        }
        try {
            return client.getBucketPolicy(
                    io.minio.GetBucketPolicyArgs.builder().bucket(bucket).build());
        } catch (ErrorResponseException e) {
            if (e.errorResponse() != null && "NoSuchBucketPolicy".equals(e.errorResponse().code())) {
                return null;
            }
            log.debug("[OSS] getBucketPolicy failed bucket={}: {}", bucket, e.getMessage());
            return null;
        } catch (Exception e) {
            log.debug("[OSS] getBucketPolicy failed bucket={}: {}", bucket, e.getMessage());
            return null;
        }
    }

    public boolean bucketExists(String connectionCode, String bucket) {
        if (StrUtil.isBlank(bucket)) {
            return false;
        }
        try {
            MinioClient client = minioClientFactory.getClient(connectionCode);
            return client.bucketExists(BucketExistsArgs.builder().bucket(bucket).build());
        } catch (Exception e) {
            log.warn("[OSS] bucketExists failed bucket={}: {}", bucket, e.getMessage());
            return false;
        }
    }

    public String getBucketPolicySafe(String connectionCode, String bucket) {
        if (StrUtil.isBlank(bucket)) {
            return null;
        }
        try {
            MinioClient client = minioClientFactory.getClient(connectionCode);
            return client.getBucketPolicy(
                    io.minio.GetBucketPolicyArgs.builder().bucket(bucket).build());
        } catch (ErrorResponseException e) {
            if (e.errorResponse() != null && "NoSuchBucketPolicy".equals(e.errorResponse().code())) {
                return null;
            }
            log.debug("[OSS] getBucketPolicy failed bucket={}: {}", bucket, e.getMessage());
            return null;
        } catch (Exception e) {
            log.debug("[OSS] getBucketPolicy failed bucket={}: {}", bucket, e.getMessage());
            return null;
        }
    }
}
