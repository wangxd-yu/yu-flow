package org.yu.flow.module.release.support;

import org.springframework.data.domain.Pageable;
import org.yu.flow.module.release.support.ReleaseAssetResolver.AssetInfo;
import org.yu.flow.module.transfer.dto.AssetBundle;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * 版本单可纳入的一类资产。资产所在的业务模块各提供一份实现，{@link ReleaseAssetResolver} 按类型分派。
 *
 * @param <T> 资产实体
 */
public interface ReleaseAssetType<T> {

    /** 类型编码，取 {@link ReleaseAssetResolver} 里的常量 */
    String type();

    Optional<T> findById(String id);

    List<T> findAllById(Collection<String> ids);

    /** 在目标环境按编码匹配的类型（{@link ReleaseAssetResolver#KEYED_TYPES}）按编码查找 */
    default Optional<T> findByKey(String key) {
        return Optional.empty();
    }

    AssetInfo describe(T row);

    /** 不参与版本管理的行（例如系统保留接口）返回 false */
    default boolean visible(T row) {
        return true;
    }

    /** 在目标环境按编码匹配的类型返回编码 */
    default String keyOf(T row) {
        return null;
    }

    /** 是否在线：已发布、页面已发布、规则或平台已启用 */
    default boolean online(T row) {
        return false;
    }

    /** 可发布资产的最近发布时间 */
    default LocalDateTime publishTime(T row) {
        return null;
    }

    /** 不能随发布包迁移的原因（例如敏感系统配置）；可以迁移返回 null */
    default String transferRejectReason(T row) {
        return null;
    }

    /** 自某时间点以来有变化的行：可发布资产看发布时间，其余看更新时间 */
    List<T> changedSince(LocalDateTime since, Pageable page);

    List<T> search(String keyword, Pageable page);

    /**
     * 版本差异：目标环境里按包内键（ID 或编码）找到的当前内容，可发布资产取线上快照。
     * 返回实体，或已经展开成字段视图的 {@code Map<String, String>}；没有返回 null。
     */
    Object diffCurrent(String key);

    /** 版本差异：包内的同一资产，返回值约定同 {@link #diffCurrent} */
    Object diffIncoming(AssetBundle bundle, String key);

    /**
     * 下线：撤销发布、页面置草稿、规则或平台停用。在导入事务内调用。
     *
     * @return 不支持下线或资产不存在时返回 false
     */
    default boolean takeOffline(String id, String key) {
        return false;
    }
}
