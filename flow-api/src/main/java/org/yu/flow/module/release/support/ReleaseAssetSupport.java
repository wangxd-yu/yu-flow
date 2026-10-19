package org.yu.flow.module.release.support;

import cn.hutool.core.util.StrUtil;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;
import org.yu.flow.module.assetref.FlowDslReferenceScanner;
import org.yu.flow.module.release.support.ReleaseAssetResolver.AssetInfo;
import org.yu.flow.module.release.support.ReleaseAssetResolver.AssetRef;
import org.yu.flow.module.transfer.support.BundleEntityCopier;
import org.yu.flow.util.ContentHashUtil;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * {@link ReleaseAssetType} 各实现共用的查询条件与展示信息构造。
 */
public final class ReleaseAssetSupport {

    private ReleaseAssetSupport() {
    }

    public static boolean isPublished(Integer status, String snapshot) {
        return status != null && status == 1 && StrUtil.isNotBlank(snapshot);
    }

    public static String hashIf(boolean published, String snapshot) {
        return published ? ContentHashUtil.sha256Hex(snapshot) : null;
    }

    /** 编排里引用的接口与内部服务 */
    public static List<AssetRef> dslDeps(String content) {
        List<AssetRef> deps = new ArrayList<>();
        for (FlowDslReferenceScanner.OutboundRef ref : FlowDslReferenceScanner.scan(content)) {
            deps.add(new AssetRef("service".equals(ref.targetType())
                    ? ReleaseAssetResolver.SERVICE : ReleaseAssetResolver.API, ref.targetId()));
        }
        return deps;
    }

    /** 无发布概念的资产：指纹取业务字段拼接 */
    public static AssetInfo configInfo(String type, String id, String name, String detail, String... hashParts) {
        StringBuilder sb = new StringBuilder();
        for (String part : hashParts) {
            sb.append(StrUtil.nullToEmpty(part)).append('\u0000');
        }
        return new AssetInfo(type, id, name, detail, false, false, false, ContentHashUtil.sha256Hex(sb.toString()),
                new ArrayList<>());
    }

    /** 已发布的资产取线上快照内容 */
    public static <T> T live(T entity, Class<T> cls, Integer publishStatus, String snapshot) {
        T copy = BundleEntityCopier.detachedCopy(entity, cls);
        if (isPublished(publishStatus, snapshot)) {
            BundleEntityCopier.applySnapshot(copy, snapshot);
        }
        return copy;
    }

    public static <T> Specification<T> publishedAfter(LocalDateTime since) {
        return (root, query, cb) -> cb.and(cb.equal(root.get("publishStatus"), 1),
                cb.greaterThan(root.get("publishTime"), since));
    }

    public static <T> Specification<T> updatedAfter(LocalDateTime since) {
        return (root, query, cb) -> cb.greaterThan(root.get("updateTime"), since);
    }

    public static <T> Specification<T> likeAny(String keyword, String... fields) {
        return (root, query, cb) -> {
            if (keyword == null) {
                return cb.conjunction();
            }
            String pattern = "%" + keyword.toLowerCase(Locale.ROOT) + "%";
            List<Predicate> ors = new ArrayList<>();
            for (String f : fields) {
                ors.add(cb.like(cb.lower(root.get(f)), pattern));
            }
            return cb.or(ors.toArray(new Predicate[0]));
        };
    }

    public static <T> T first(List<T> list, java.util.function.Predicate<T> match) {
        return list == null ? null : list.stream().filter(match).findFirst().orElse(null);
    }
}
