package org.yu.flow.module.responsetemplate.support;

import cn.hutool.core.util.StrUtil;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;
import org.yu.flow.module.release.support.ReleaseAssetResolver;
import org.yu.flow.module.release.support.ReleaseAssetResolver.AssetInfo;
import org.yu.flow.module.release.support.ReleaseAssetType;
import org.yu.flow.module.responsetemplate.domain.ResponseTemplateDO;
import org.yu.flow.module.responsetemplate.repository.ResponseTemplateRepository;
import org.yu.flow.module.transfer.dto.AssetBundle;
import org.yu.flow.util.ContentHashUtil;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import static org.yu.flow.module.release.support.ReleaseAssetSupport.first;
import static org.yu.flow.module.release.support.ReleaseAssetSupport.likeAny;
import static org.yu.flow.module.release.support.ReleaseAssetSupport.updatedAfter;

@Component
public class ResponseTemplateReleaseAsset implements ReleaseAssetType<ResponseTemplateDO> {

    private final ResponseTemplateRepository repository;

    public ResponseTemplateReleaseAsset(ResponseTemplateRepository repository) {
        this.repository = repository;
    }

    @Override
    public String type() {
        return ReleaseAssetResolver.RESPONSE_TEMPLATE;
    }

    @Override
    public Optional<ResponseTemplateDO> findById(String id) {
        return repository.findById(id);
    }

    @Override
    public List<ResponseTemplateDO> findAllById(Collection<String> ids) {
        return repository.findAllById(ids);
    }

    @Override
    public AssetInfo describe(ResponseTemplateDO t) {
        String hash = ContentHashUtil.sha256Hex(StrUtil.nullToEmpty(t.getSuccessWrapper()) + "\u0000"
                + StrUtil.nullToEmpty(t.getPageWrapper()) + "\u0000" + StrUtil.nullToEmpty(t.getFailWrapper()));
        return new AssetInfo(type(), t.getId(), t.getTemplateName(), t.getRemark(), false, false, false,
                hash, new ArrayList<>());
    }

    @Override
    public List<ResponseTemplateDO> changedSince(LocalDateTime since, Pageable page) {
        return repository.findAll(updatedAfter(since), page).getContent();
    }

    @Override
    public List<ResponseTemplateDO> search(String keyword, Pageable page) {
        return repository.findAll(likeAny(keyword, "templateName"), page).getContent();
    }

    @Override
    public Object diffCurrent(String key) {
        return repository.findById(key).orElse(null);
    }

    @Override
    public Object diffIncoming(AssetBundle bundle, String key) {
        return first(bundle.getResponseTemplates(), t -> key.equals(t.getId()));
    }
}
