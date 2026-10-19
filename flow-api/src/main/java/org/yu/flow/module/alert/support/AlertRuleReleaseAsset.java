package org.yu.flow.module.alert.support;

import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;
import org.yu.flow.module.alert.domain.AlertRuleDO;
import org.yu.flow.module.alert.repository.AlertRuleRepository;
import org.yu.flow.module.release.support.ReleaseAssetResolver;
import org.yu.flow.module.release.support.ReleaseAssetResolver.AssetInfo;
import org.yu.flow.module.release.support.ReleaseAssetType;
import org.yu.flow.module.transfer.dto.AssetBundle;
import org.yu.flow.module.transfer.dto.BundleAlertRule;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import static org.yu.flow.module.release.support.ReleaseAssetSupport.configInfo;
import static org.yu.flow.module.release.support.ReleaseAssetSupport.first;
import static org.yu.flow.module.release.support.ReleaseAssetSupport.likeAny;
import static org.yu.flow.module.release.support.ReleaseAssetSupport.updatedAfter;

@Component
public class AlertRuleReleaseAsset implements ReleaseAssetType<AlertRuleDO> {

    private final AlertRuleRepository repository;

    public AlertRuleReleaseAsset(AlertRuleRepository repository) {
        this.repository = repository;
    }

    @Override
    public String type() {
        return ReleaseAssetResolver.ALERT_RULE;
    }

    @Override
    public Optional<AlertRuleDO> findById(String id) {
        return repository.findById(id);
    }

    @Override
    public List<AlertRuleDO> findAllById(Collection<String> ids) {
        return repository.findAllById(ids);
    }

    @Override
    public AssetInfo describe(AlertRuleDO r) {
        return configInfo(type(), r.getId(), r.getName(), r.getWindow() + " / " + r.getMinHealth(), r.getName(),
                r.getScopeAssetTypes(), r.getWindow(), r.getMinHealth(), String.valueOf(r.getTopN()), r.getChannelIds(),
                String.valueOf(r.getIntervalMinutes()), String.valueOf(r.getDedupMinutes()));
    }

    @Override
    public boolean online(AlertRuleDO r) {
        return Integer.valueOf(1).equals(r.getEnabled());
    }

    @Override
    public List<AlertRuleDO> changedSince(LocalDateTime since, Pageable page) {
        return repository.findAll(updatedAfter(since), page).getContent();
    }

    @Override
    public List<AlertRuleDO> search(String keyword, Pageable page) {
        return repository.findAll(likeAny(keyword, "name"), page).getContent();
    }

    @Override
    public Object diffCurrent(String key) {
        return repository.findById(key).orElse(null);
    }

    @Override
    public Object diffIncoming(AssetBundle bundle, String key) {
        BundleAlertRule item = first(bundle.getAlertRules(), x -> x.getRule() != null && key.equals(x.getRule().getId()));
        return item == null ? null : item.getRule();
    }

    /** 规则下线即停用 */
    @Override
    public boolean takeOffline(String id, String key) {
        return repository.findById(id).map(r -> {
            r.setEnabled(0);
            repository.save(r);
            return true;
        }).orElse(false);
    }
}
