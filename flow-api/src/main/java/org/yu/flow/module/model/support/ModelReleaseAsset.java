package org.yu.flow.module.model.support;

import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;
import org.yu.flow.module.model.domain.FlowModelInfoDO;
import org.yu.flow.module.model.repository.FlowModelInfoRepository;
import org.yu.flow.module.release.support.ReleaseAssetResolver;
import org.yu.flow.module.release.support.ReleaseAssetResolver.AssetInfo;
import org.yu.flow.module.release.support.ReleaseAssetType;
import org.yu.flow.module.transfer.dto.AssetBundle;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import static org.yu.flow.module.release.support.ReleaseAssetSupport.configInfo;
import static org.yu.flow.module.release.support.ReleaseAssetSupport.first;
import static org.yu.flow.module.release.support.ReleaseAssetSupport.likeAny;
import static org.yu.flow.module.release.support.ReleaseAssetSupport.updatedAfter;

@Component
public class ModelReleaseAsset implements ReleaseAssetType<FlowModelInfoDO> {

    private final FlowModelInfoRepository repository;

    public ModelReleaseAsset(FlowModelInfoRepository repository) {
        this.repository = repository;
    }

    @Override
    public String type() {
        return ReleaseAssetResolver.MODEL;
    }

    @Override
    public Optional<FlowModelInfoDO> findById(String id) {
        return repository.findById(id);
    }

    @Override
    public List<FlowModelInfoDO> findAllById(Collection<String> ids) {
        return repository.findAllById(ids);
    }

    @Override
    public AssetInfo describe(FlowModelInfoDO m) {
        return configInfo(type(), m.getId(), m.getName(), m.getDatasource() + " / " + m.getTableName(),
                m.getName(), m.getTableName(), m.getDatasource(), m.getFieldsSchema(), String.valueOf(m.getStatus()));
    }

    @Override
    public List<FlowModelInfoDO> changedSince(LocalDateTime since, Pageable page) {
        return repository.findAll(updatedAfter(since), page).getContent();
    }

    @Override
    public List<FlowModelInfoDO> search(String keyword, Pageable page) {
        return repository.findAll(likeAny(keyword, "name", "tableName"), page).getContent();
    }

    @Override
    public Object diffCurrent(String key) {
        return repository.findById(key).orElse(null);
    }

    @Override
    public Object diffIncoming(AssetBundle bundle, String key) {
        return first(bundle.getModels(), m -> key.equals(m.getId()));
    }
}
