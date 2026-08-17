package org.yu.flow.module.transfer.support;

import cn.hutool.core.util.StrUtil;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

/**
 * 资产实体的脱管拷贝与快照回填。
 *
 * <p>导出必须在拷贝上改字段：JPA 托管实体一旦被修改，事务提交时会把改动写回源库。</p>
 */
public final class BundleEntityCopier {

    /**
     * 宽松映射：包体可能来自不同版本，多出来的字段直接忽略，不让整包导入失败。
     * <p>实体上的 {@code @JsonFormat} 同时约束序列化与反序列化，时间字段可原样往返。</p>
     */
    private static final ObjectMapper MAPPER = create();

    private BundleEntityCopier() {
    }

    private static ObjectMapper create() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        mapper.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
        return mapper;
    }

    /** 深拷贝出一个与持久化上下文无关的对象 */
    public static <T> T detachedCopy(T entity, Class<T> type) {
        if (entity == null) {
            return null;
        }
        return MAPPER.convertValue(entity, type);
    }

    /**
     * 把发布快照中的字段回填到实体。
     *
     * <p>快照 JSON 的字段名与实体属性一一对应（见各模块 {@code buildSnapshot}），
     * 因此按「仅覆盖快照中出现的字段」的语义直接增量反序列化即可，无需逐字段手写映射。</p>
     */
    public static <T> void applySnapshot(T target, String snapshotJson) {
        if (target == null || StrUtil.isBlank(snapshotJson)) {
            return;
        }
        try {
            MAPPER.readerForUpdating(target).readValue(snapshotJson);
        } catch (Exception e) {
            throw new RuntimeException("解析发布快照失败: " + e.getMessage(), e);
        }
    }
}
