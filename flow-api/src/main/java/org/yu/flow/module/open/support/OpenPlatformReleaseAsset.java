package org.yu.flow.module.open.support;

import cn.hutool.core.util.StrUtil;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;
import org.yu.flow.module.open.cache.OpenPlatformCache;
import org.yu.flow.module.open.domain.FlowOpenPlatformDO;
import org.yu.flow.module.open.repository.FlowOpenApiGrantRepository;
import org.yu.flow.module.open.repository.FlowOpenPlatformRepository;
import org.yu.flow.module.release.support.ReleaseAssetResolver;
import org.yu.flow.module.release.support.ReleaseAssetResolver.AssetInfo;
import org.yu.flow.module.release.support.ReleaseAssetType;
import org.yu.flow.module.release.support.ReleaseDiffCalculator;
import org.yu.flow.module.transfer.dto.AssetBundle;
import org.yu.flow.module.transfer.dto.BundleOpenPlatform;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.yu.flow.module.release.support.ReleaseAssetSupport.configInfo;
import static org.yu.flow.module.release.support.ReleaseAssetSupport.first;
import static org.yu.flow.module.release.support.ReleaseAssetSupport.likeAny;
import static org.yu.flow.module.release.support.ReleaseAssetSupport.updatedAfter;

/** 目标环境按平台编码匹配；授权接口列表计入指纹与差异 */
@Component
public class OpenPlatformReleaseAsset implements ReleaseAssetType<FlowOpenPlatformDO> {

    private final FlowOpenPlatformRepository repository;
    private final FlowOpenApiGrantRepository grantRepository;
    private final OpenPlatformCache openPlatformCache;

    public OpenPlatformReleaseAsset(FlowOpenPlatformRepository repository, FlowOpenApiGrantRepository grantRepository,
                                    OpenPlatformCache openPlatformCache) {
        this.repository = repository;
        this.grantRepository = grantRepository;
        this.openPlatformCache = openPlatformCache;
    }

    @Override
    public String type() {
        return ReleaseAssetResolver.OPEN_PLATFORM;
    }

    @Override
    public Optional<FlowOpenPlatformDO> findById(String id) {
        return repository.findById(id);
    }

    @Override
    public List<FlowOpenPlatformDO> findAllById(Collection<String> ids) {
        return repository.findAllById(ids);
    }

    @Override
    public Optional<FlowOpenPlatformDO> findByKey(String key) {
        return repository.findByCode(key);
    }

    @Override
    public AssetInfo describe(FlowOpenPlatformDO p) {
        StringBuilder grants = new StringBuilder();
        grantRepository.findByPlatformId(p.getId()).stream()
                .map(g -> g.getApiId() + "=" + StrUtil.nullToEmpty(g.getAllowMethods()))
                .sorted().forEach(g -> grants.append(g).append(';'));
        return configInfo(type(), p.getId(), p.getName(), p.getCode(), p.getName(), p.getContact(),
                p.getRemark(), String.valueOf(p.getOpenCallLogEnabled()), String.valueOf(p.getRateLimitQps()), grants.toString());
    }

    @Override
    public String keyOf(FlowOpenPlatformDO p) {
        return p.getCode();
    }

    @Override
    public boolean online(FlowOpenPlatformDO p) {
        return Integer.valueOf(1).equals(p.getStatus());
    }

    @Override
    public List<FlowOpenPlatformDO> changedSince(LocalDateTime since, Pageable page) {
        return repository.findAll(updatedAfter(since), page).getContent();
    }

    @Override
    public List<FlowOpenPlatformDO> search(String keyword, Pageable page) {
        return repository.findAll(likeAny(keyword, "name", "code"), page).getContent();
    }

    @Override
    public Object diffCurrent(String key) {
        return findByKey(key).map(p -> {
            Map<String, String> view = ReleaseDiffCalculator.view(type(), p);
            view.put("grants", ReleaseDiffCalculator.grantsText(grantRepository.findByPlatformId(p.getId()).stream()
                    .map(g -> new BundleOpenPlatform.Grant(g.getApiId(), g.getAllowMethods())).toList()));
            return view;
        }).orElse(null);
    }

    @Override
    public Object diffIncoming(AssetBundle bundle, String key) {
        BundleOpenPlatform item = first(bundle.getOpenPlatforms(),
                x -> x.getPlatform() != null && key.equals(x.getPlatform().getCode()));
        if (item == null) {
            return null;
        }
        Map<String, String> view = ReleaseDiffCalculator.view(type(), item.getPlatform());
        view.put("grants", ReleaseDiffCalculator.grantsText(item.getGrants()));
        return view;
    }

    /** 平台按编码匹配，下线即停用并刷新各节点缓存 */
    @Override
    public boolean takeOffline(String id, String key) {
        return findByKey(key).map(p -> {
            p.setStatus(0);
            repository.save(p);
            openPlatformCache.publishRefresh();
            return true;
        }).orElse(false);
    }
}
