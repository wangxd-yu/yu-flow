package org.yu.flow.module.sysmacro.support;

import cn.hutool.core.util.StrUtil;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;
import org.yu.flow.module.release.support.ReleaseAssetResolver;
import org.yu.flow.module.release.support.ReleaseAssetResolver.AssetInfo;
import org.yu.flow.module.release.support.ReleaseAssetType;
import org.yu.flow.module.sysmacro.domain.SysMacroDO;
import org.yu.flow.module.sysmacro.repository.SysMacroRepository;
import org.yu.flow.module.transfer.dto.AssetBundle;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import static org.yu.flow.module.release.support.ReleaseAssetSupport.configInfo;
import static org.yu.flow.module.release.support.ReleaseAssetSupport.first;
import static org.yu.flow.module.release.support.ReleaseAssetSupport.likeAny;
import static org.yu.flow.module.release.support.ReleaseAssetSupport.updatedAfter;

/** 目标环境按宏编码匹配 */
@Component
public class SysMacroReleaseAsset implements ReleaseAssetType<SysMacroDO> {

    private final SysMacroRepository repository;

    public SysMacroReleaseAsset(SysMacroRepository repository) {
        this.repository = repository;
    }

    @Override
    public String type() {
        return ReleaseAssetResolver.SYS_MACRO;
    }

    @Override
    public Optional<SysMacroDO> findById(String id) {
        return repository.findById(id);
    }

    @Override
    public List<SysMacroDO> findAllById(Collection<String> ids) {
        return repository.findAllById(ids);
    }

    @Override
    public Optional<SysMacroDO> findByKey(String key) {
        return repository.findByMacroCode(key);
    }

    @Override
    public AssetInfo describe(SysMacroDO m) {
        return configInfo(type(), m.getId(), m.getMacroCode() + " · " + StrUtil.nullToEmpty(m.getMacroName()),
                m.getMacroType(), m.getMacroName(), m.getMacroType(), m.getExpression(), m.getScope(),
                m.getReturnType(), m.getMacroParams(), String.valueOf(m.getStatus()));
    }

    @Override
    public String keyOf(SysMacroDO m) {
        return m.getMacroCode();
    }

    @Override
    public List<SysMacroDO> changedSince(LocalDateTime since, Pageable page) {
        return repository.findAll(updatedAfter(since), page).getContent();
    }

    @Override
    public List<SysMacroDO> search(String keyword, Pageable page) {
        return repository.findAll(likeAny(keyword, "macroCode", "macroName"), page).getContent();
    }

    @Override
    public Object diffCurrent(String key) {
        return findByKey(key).orElse(null);
    }

    @Override
    public Object diffIncoming(AssetBundle bundle, String key) {
        return first(bundle.getSysMacros(), m -> key.equals(m.getMacroCode()));
    }
}
