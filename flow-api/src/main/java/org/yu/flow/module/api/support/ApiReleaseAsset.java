package org.yu.flow.module.api.support;

import cn.hutool.core.util.StrUtil;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;
import org.yu.flow.module.api.domain.FlowApiDO;
import org.yu.flow.module.api.repository.FlowApiRepository;
import org.yu.flow.module.api.service.FlowApiCrudService;
import org.yu.flow.module.assetversion.UnpublishedChangeDetector;
import org.yu.flow.module.host.HostCatalogReserved;
import org.yu.flow.module.release.support.ReleaseAssetResolver;
import org.yu.flow.module.release.support.ReleaseAssetResolver.AssetInfo;
import org.yu.flow.module.release.support.ReleaseAssetResolver.AssetRef;
import org.yu.flow.module.release.support.ReleaseAssetType;
import org.yu.flow.module.transfer.dto.AssetBundle;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import static org.yu.flow.module.release.support.ReleaseAssetSupport.dslDeps;
import static org.yu.flow.module.release.support.ReleaseAssetSupport.first;
import static org.yu.flow.module.release.support.ReleaseAssetSupport.hashIf;
import static org.yu.flow.module.release.support.ReleaseAssetSupport.isPublished;
import static org.yu.flow.module.release.support.ReleaseAssetSupport.likeAny;
import static org.yu.flow.module.release.support.ReleaseAssetSupport.live;
import static org.yu.flow.module.release.support.ReleaseAssetSupport.publishedAfter;

@Component
public class ApiReleaseAsset implements ReleaseAssetType<FlowApiDO> {

    private final FlowApiRepository repository;
    /** 接口服务经发布门禁依赖版本单解析，延迟取避免循环依赖 */
    private final ObjectProvider<FlowApiCrudService> crudService;

    public ApiReleaseAsset(FlowApiRepository repository, ObjectProvider<FlowApiCrudService> crudService) {
        this.repository = repository;
        this.crudService = crudService;
    }

    @Override
    public String type() {
        return ReleaseAssetResolver.API;
    }

    @Override
    public Optional<FlowApiDO> findById(String id) {
        return repository.findById(id);
    }

    @Override
    public List<FlowApiDO> findAllById(Collection<String> ids) {
        return repository.findAllById(ids);
    }

    @Override
    public boolean visible(FlowApiDO a) {
        return !HostCatalogReserved.isReservedId(a.getId());
    }

    @Override
    public AssetInfo describe(FlowApiDO a) {
        boolean published = isPublished(a.getPublishStatus(), a.getPublishedSnapshot());
        List<AssetRef> deps = dslDeps(published ? a.getPublishedSnapshot() : a.getDslContent());
        if (StrUtil.isNotBlank(a.getTemplateId())) {
            deps.add(new AssetRef(ReleaseAssetResolver.RESPONSE_TEMPLATE, a.getTemplateId()));
        }
        String detail = StrUtil.nullToEmpty(a.getMethod()).toUpperCase(Locale.ROOT) + " " + StrUtil.nullToEmpty(a.getUrl());
        return new AssetInfo(type(), a.getId(), a.getName(), detail.trim(), true, published,
                UnpublishedChangeDetector.apiHasUnpublishedChanges(a), hashIf(published, a.getPublishedSnapshot()), deps);
    }

    @Override
    public boolean online(FlowApiDO a) {
        return isPublished(a.getPublishStatus(), a.getPublishedSnapshot());
    }

    @Override
    public LocalDateTime publishTime(FlowApiDO a) {
        return a.getPublishTime();
    }

    @Override
    public List<FlowApiDO> changedSince(LocalDateTime since, Pageable page) {
        return repository.findAll(publishedAfter(since), page).getContent();
    }

    @Override
    public List<FlowApiDO> search(String keyword, Pageable page) {
        return repository.findAll(likeAny(keyword, "name", "url"), page).getContent();
    }

    @Override
    public Object diffCurrent(String key) {
        return repository.findById(key)
                .map(a -> live(a, FlowApiDO.class, a.getPublishStatus(), a.getPublishedSnapshot()))
                .orElse(null);
    }

    @Override
    public Object diffIncoming(AssetBundle bundle, String key) {
        return first(bundle.getApis(), a -> key.equals(a.getId()));
    }

    @Override
    public boolean takeOffline(String id, String key) {
        crudService.getObject().unpublish(id);
        return true;
    }
}
