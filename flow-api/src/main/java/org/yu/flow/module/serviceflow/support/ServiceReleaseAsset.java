package org.yu.flow.module.serviceflow.support;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;
import org.yu.flow.module.assetversion.UnpublishedChangeDetector;
import org.yu.flow.module.release.support.ReleaseAssetResolver;
import org.yu.flow.module.release.support.ReleaseAssetResolver.AssetInfo;
import org.yu.flow.module.release.support.ReleaseAssetType;
import org.yu.flow.module.serviceflow.domain.FlowServiceFlowDO;
import org.yu.flow.module.serviceflow.repository.FlowServiceFlowRepository;
import org.yu.flow.module.serviceflow.service.FlowServiceFlowService;
import org.yu.flow.module.transfer.dto.AssetBundle;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import static org.yu.flow.module.release.support.ReleaseAssetSupport.dslDeps;
import static org.yu.flow.module.release.support.ReleaseAssetSupport.first;
import static org.yu.flow.module.release.support.ReleaseAssetSupport.hashIf;
import static org.yu.flow.module.release.support.ReleaseAssetSupport.isPublished;
import static org.yu.flow.module.release.support.ReleaseAssetSupport.likeAny;
import static org.yu.flow.module.release.support.ReleaseAssetSupport.live;
import static org.yu.flow.module.release.support.ReleaseAssetSupport.publishedAfter;

@Component
public class ServiceReleaseAsset implements ReleaseAssetType<FlowServiceFlowDO> {

    private final FlowServiceFlowRepository repository;
    /** 服务经发布门禁依赖版本单解析，延迟取避免循环依赖 */
    private final ObjectProvider<FlowServiceFlowService> service;

    public ServiceReleaseAsset(FlowServiceFlowRepository repository, ObjectProvider<FlowServiceFlowService> service) {
        this.repository = repository;
        this.service = service;
    }

    @Override
    public String type() {
        return ReleaseAssetResolver.SERVICE;
    }

    @Override
    public Optional<FlowServiceFlowDO> findById(String id) {
        return repository.findById(id);
    }

    @Override
    public List<FlowServiceFlowDO> findAllById(Collection<String> ids) {
        return repository.findAllById(ids);
    }

    @Override
    public AssetInfo describe(FlowServiceFlowDO s) {
        boolean published = isPublished(s.getPublishStatus(), s.getPublishedSnapshot());
        return new AssetInfo(type(), s.getId(), s.getName(), null, true, published,
                UnpublishedChangeDetector.serviceHasUnpublishedChanges(s), hashIf(published, s.getPublishedSnapshot()),
                dslDeps(published ? s.getPublishedSnapshot() : s.getDslContent()));
    }

    @Override
    public boolean online(FlowServiceFlowDO s) {
        return isPublished(s.getPublishStatus(), s.getPublishedSnapshot());
    }

    @Override
    public LocalDateTime publishTime(FlowServiceFlowDO s) {
        return s.getPublishTime();
    }

    @Override
    public List<FlowServiceFlowDO> changedSince(LocalDateTime since, Pageable page) {
        return repository.findAll(publishedAfter(since), page).getContent();
    }

    @Override
    public List<FlowServiceFlowDO> search(String keyword, Pageable page) {
        return repository.findAll(likeAny(keyword, "name"), page).getContent();
    }

    @Override
    public Object diffCurrent(String key) {
        return repository.findById(key)
                .map(s -> live(s, FlowServiceFlowDO.class, s.getPublishStatus(), s.getPublishedSnapshot()))
                .orElse(null);
    }

    @Override
    public Object diffIncoming(AssetBundle bundle, String key) {
        return first(bundle.getServices(), s -> key.equals(s.getId()));
    }

    @Override
    public boolean takeOffline(String id, String key) {
        service.getObject().unpublish(id);
        return true;
    }
}
