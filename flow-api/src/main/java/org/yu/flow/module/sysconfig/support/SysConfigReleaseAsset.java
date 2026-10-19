package org.yu.flow.module.sysconfig.support;

import cn.hutool.core.util.StrUtil;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;
import org.yu.flow.module.release.support.ReleaseAssetResolver;
import org.yu.flow.module.release.support.ReleaseAssetResolver.AssetInfo;
import org.yu.flow.module.release.support.ReleaseAssetType;
import org.yu.flow.module.sysconfig.domain.SysConfigDO;
import org.yu.flow.module.sysconfig.repository.SysConfigRepository;
import org.yu.flow.module.transfer.dto.AssetBundle;
import org.yu.flow.module.transfer.support.ConfigTransferPolicy;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import static org.yu.flow.module.release.support.ReleaseAssetSupport.configInfo;
import static org.yu.flow.module.release.support.ReleaseAssetSupport.first;
import static org.yu.flow.module.release.support.ReleaseAssetSupport.likeAny;
import static org.yu.flow.module.release.support.ReleaseAssetSupport.updatedAfter;

/** 目标环境按配置键匹配；敏感键不随包迁移 */
@Component
public class SysConfigReleaseAsset implements ReleaseAssetType<SysConfigDO> {

    private final SysConfigRepository repository;

    public SysConfigReleaseAsset(SysConfigRepository repository) {
        this.repository = repository;
    }

    @Override
    public String type() {
        return ReleaseAssetResolver.SYS_CONFIG;
    }

    @Override
    public Optional<SysConfigDO> findById(String id) {
        return repository.findById(id);
    }

    @Override
    public List<SysConfigDO> findAllById(Collection<String> ids) {
        return repository.findAllById(ids);
    }

    @Override
    public Optional<SysConfigDO> findByKey(String key) {
        return repository.findByConfigKey(key);
    }

    /** 列表只展示分组与说明，不回显配置值：只读角色也能搜索资产 */
    @Override
    public AssetInfo describe(SysConfigDO c) {
        String detail = StrUtil.nullToEmpty(c.getConfigGroup())
                + (StrUtil.isBlank(c.getRemark()) ? "" : " · " + StrUtil.maxLength(c.getRemark(), 40))
                + (transferRejectReason(c) != null ? "（不随包迁移）" : "");
        return configInfo(type(), c.getId(), c.getConfigKey(), detail.trim(), c.getConfigValue(), c.getValueType(),
                c.getConfigGroup(), String.valueOf(c.getStatus()));
    }

    @Override
    public String keyOf(SysConfigDO c) {
        return c.getConfigKey();
    }

    @Override
    public String transferRejectReason(SysConfigDO c) {
        return ConfigTransferPolicy.rejectReason(c.getConfigKey(), c.getConfigGroup());
    }

    @Override
    public List<SysConfigDO> changedSince(LocalDateTime since, Pageable page) {
        return repository.findAll(updatedAfter(since), page).getContent();
    }

    @Override
    public List<SysConfigDO> search(String keyword, Pageable page) {
        return repository.findAll(likeAny(keyword, "configKey", "remark"), page).getContent();
    }

    @Override
    public Object diffCurrent(String key) {
        return findByKey(key).orElse(null);
    }

    @Override
    public Object diffIncoming(AssetBundle bundle, String key) {
        return first(bundle.getSysConfigs(), c -> key.equals(c.getConfigKey()));
    }
}
