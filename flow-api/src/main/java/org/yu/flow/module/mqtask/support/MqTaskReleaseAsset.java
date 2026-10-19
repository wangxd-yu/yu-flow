package org.yu.flow.module.mqtask.support;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;
import org.yu.flow.module.assetversion.UnpublishedChangeDetector;
import org.yu.flow.module.mqtask.domain.FlowMqTaskDO;
import org.yu.flow.module.mqtask.repository.FlowMqTaskRepository;
import org.yu.flow.module.mqtask.service.FlowMqTaskService;
import org.yu.flow.module.release.support.ReleaseAssetResolver;
import org.yu.flow.module.release.support.ReleaseAssetResolver.AssetInfo;
import org.yu.flow.module.release.support.ReleaseAssetType;
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
public class MqTaskReleaseAsset implements ReleaseAssetType<FlowMqTaskDO> {

    private final FlowMqTaskRepository repository;
    /** MQ 任务服务的下线要重建订阅，延迟取与其它可发布资产保持一致 */
    private final ObjectProvider<FlowMqTaskService> service;

    public MqTaskReleaseAsset(FlowMqTaskRepository repository, ObjectProvider<FlowMqTaskService> service) {
        this.repository = repository;
        this.service = service;
    }

    @Override
    public String type() {
        return ReleaseAssetResolver.MQ_TASK;
    }

    @Override
    public Optional<FlowMqTaskDO> findById(String id) {
        return repository.findById(id);
    }

    @Override
    public List<FlowMqTaskDO> findAllById(Collection<String> ids) {
        return repository.findAllById(ids);
    }

    @Override
    public AssetInfo describe(FlowMqTaskDO t) {
        boolean published = isPublished(t.getPublishStatus(), t.getPublishedSnapshot());
        return new AssetInfo(type(), t.getId(), t.getName(), t.getConnectionCode() + " / " + t.getTopic(), true,
                published, UnpublishedChangeDetector.mqTaskHasUnpublishedChanges(t),
                hashIf(published, t.getPublishedSnapshot()),
                dslDeps(published ? t.getPublishedSnapshot() : t.getDslContent()));
    }

    @Override
    public boolean online(FlowMqTaskDO t) {
        return isPublished(t.getPublishStatus(), t.getPublishedSnapshot());
    }

    @Override
    public LocalDateTime publishTime(FlowMqTaskDO t) {
        return t.getPublishTime();
    }

    @Override
    public List<FlowMqTaskDO> changedSince(LocalDateTime since, Pageable page) {
        return repository.findAll(publishedAfter(since), page).getContent();
    }

    @Override
    public List<FlowMqTaskDO> search(String keyword, Pageable page) {
        return repository.findAll(likeAny(keyword, "name", "topic"), page).getContent();
    }

    @Override
    public Object diffCurrent(String key) {
        return repository.findById(key)
                .map(t -> live(t, FlowMqTaskDO.class, t.getPublishStatus(), t.getPublishedSnapshot()))
                .orElse(null);
    }

    @Override
    public Object diffIncoming(AssetBundle bundle, String key) {
        return first(bundle.getMqTasks(), t -> key.equals(t.getId()));
    }

    @Override
    public boolean takeOffline(String id, String key) {
        service.getObject().unpublish(id);
        return true;
    }
}
