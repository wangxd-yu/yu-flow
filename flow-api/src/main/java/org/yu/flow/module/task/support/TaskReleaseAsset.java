package org.yu.flow.module.task.support;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;
import org.yu.flow.module.assetversion.UnpublishedChangeDetector;
import org.yu.flow.module.release.support.ReleaseAssetResolver;
import org.yu.flow.module.release.support.ReleaseAssetResolver.AssetInfo;
import org.yu.flow.module.release.support.ReleaseAssetType;
import org.yu.flow.module.task.domain.FlowTaskDO;
import org.yu.flow.module.task.repository.FlowTaskRepository;
import org.yu.flow.module.task.service.FlowTaskService;
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
public class TaskReleaseAsset implements ReleaseAssetType<FlowTaskDO> {

    private final FlowTaskRepository repository;
    /** 任务服务经发布门禁依赖版本单解析，延迟取避免循环依赖 */
    private final ObjectProvider<FlowTaskService> service;

    public TaskReleaseAsset(FlowTaskRepository repository, ObjectProvider<FlowTaskService> service) {
        this.repository = repository;
        this.service = service;
    }

    @Override
    public String type() {
        return ReleaseAssetResolver.TASK;
    }

    @Override
    public Optional<FlowTaskDO> findById(String id) {
        return repository.findById(id);
    }

    @Override
    public List<FlowTaskDO> findAllById(Collection<String> ids) {
        return repository.findAllById(ids);
    }

    @Override
    public AssetInfo describe(FlowTaskDO t) {
        boolean published = isPublished(t.getPublishStatus(), t.getPublishedSnapshot());
        return new AssetInfo(type(), t.getId(), t.getName(), t.getCron(), true, published,
                UnpublishedChangeDetector.taskHasUnpublishedChanges(t), hashIf(published, t.getPublishedSnapshot()),
                dslDeps(published ? t.getPublishedSnapshot() : t.getDslContent()));
    }

    @Override
    public boolean online(FlowTaskDO t) {
        return isPublished(t.getPublishStatus(), t.getPublishedSnapshot());
    }

    @Override
    public LocalDateTime publishTime(FlowTaskDO t) {
        return t.getPublishTime();
    }

    @Override
    public List<FlowTaskDO> changedSince(LocalDateTime since, Pageable page) {
        return repository.findAll(publishedAfter(since), page).getContent();
    }

    @Override
    public List<FlowTaskDO> search(String keyword, Pageable page) {
        return repository.findAll(likeAny(keyword, "name"), page).getContent();
    }

    @Override
    public Object diffCurrent(String key) {
        return repository.findById(key)
                .map(t -> live(t, FlowTaskDO.class, t.getPublishStatus(), t.getPublishedSnapshot()))
                .orElse(null);
    }

    @Override
    public Object diffIncoming(AssetBundle bundle, String key) {
        return first(bundle.getTasks(), t -> key.equals(t.getId()));
    }

    @Override
    public boolean takeOffline(String id, String key) {
        service.getObject().unpublish(id);
        return true;
    }
}
