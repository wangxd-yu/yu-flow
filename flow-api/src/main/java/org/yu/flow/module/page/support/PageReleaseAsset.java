package org.yu.flow.module.page.support;

import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;
import org.yu.flow.module.page.domain.PageInfoDO;
import org.yu.flow.module.page.repository.PageInfoRepository;
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
public class PageReleaseAsset implements ReleaseAssetType<PageInfoDO> {

    private final PageInfoRepository repository;

    public PageReleaseAsset(PageInfoRepository repository) {
        this.repository = repository;
    }

    @Override
    public String type() {
        return ReleaseAssetResolver.PAGE;
    }

    @Override
    public Optional<PageInfoDO> findById(String id) {
        return repository.findById(id);
    }

    @Override
    public List<PageInfoDO> findAllById(Collection<String> ids) {
        return repository.findAllById(ids);
    }

    @Override
    public AssetInfo describe(PageInfoDO p) {
        return configInfo(type(), p.getId(), p.getName(), p.getRoutePath(), p.getName(), p.getRoutePath(), p.getJson(),
                String.valueOf(p.getStatus()));
    }

    @Override
    public boolean online(PageInfoDO p) {
        return Integer.valueOf(1).equals(p.getStatus());
    }

    @Override
    public List<PageInfoDO> changedSince(LocalDateTime since, Pageable page) {
        return repository.findAll(updatedAfter(since), page).getContent();
    }

    @Override
    public List<PageInfoDO> search(String keyword, Pageable page) {
        return repository.findAll(likeAny(keyword, "name", "routePath"), page).getContent();
    }

    @Override
    public Object diffCurrent(String key) {
        return repository.findById(key).orElse(null);
    }

    @Override
    public Object diffIncoming(AssetBundle bundle, String key) {
        return first(bundle.getPages(), p -> key.equals(p.getId()));
    }

    /** 页面下线即改回草稿 */
    @Override
    public boolean takeOffline(String id, String key) {
        return repository.findById(id).map(p -> {
            p.setStatus(0);
            repository.save(p);
            return true;
        }).orElse(false);
    }
}
