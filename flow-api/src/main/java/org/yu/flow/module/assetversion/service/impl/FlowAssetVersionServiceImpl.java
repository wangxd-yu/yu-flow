package org.yu.flow.module.assetversion.service.impl;

import cn.hutool.core.util.StrUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.yu.flow.module.assetversion.AssetBizType;
import org.yu.flow.module.assetversion.domain.FlowAssetVersionDO;
import org.yu.flow.module.assetversion.dto.FlowAssetVersionDTO;
import org.yu.flow.module.assetversion.repository.FlowAssetVersionRepository;
import org.yu.flow.module.assetversion.service.FlowAssetVersionService;
import org.yu.flow.module.sysconfig.cache.SysConfigCacheManager;

import jakarta.annotation.Resource;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

@Slf4j
@Service
public class FlowAssetVersionServiceImpl implements FlowAssetVersionService {

    @Resource
    private FlowAssetVersionRepository flowAssetVersionRepository;

    @Resource
    private SysConfigCacheManager sysConfigCacheManager;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public FlowAssetVersionDO append(String bizType, String assetId, String snapshot, String source, String remark, String publisher) {
        if (StrUtil.isBlank(bizType) || StrUtil.isBlank(assetId) || StrUtil.isBlank(snapshot)) {
            throw new IllegalArgumentException("bizType / assetId / snapshot 不能为空");
        }
        String src = StrUtil.blankToDefault(source, AssetBizType.SOURCE_PUBLISH);
        int nextNo = flowAssetVersionRepository
                .findTopByBizTypeAndAssetIdOrderByVersionNoDesc(bizType, assetId)
                .map(v -> v.getVersionNo() + 1)
                .orElse(1);

        LocalDateTime now = LocalDateTime.now();
        FlowAssetVersionDO entity = FlowAssetVersionDO.builder()
                .bizType(bizType)
                .assetId(assetId)
                .versionNo(nextNo)
                .snapshot(snapshot)
                .source(src)
                .remark(remark)
                .publisher(publisher)
                .publishTime(now)
                .createTime(now)
                .build();
        FlowAssetVersionDO saved = flowAssetVersionRepository.save(entity);
        trim(bizType, assetId);
        return saved;
    }

    @Override
    @Transactional(readOnly = true)
    public List<FlowAssetVersionDTO> list(String bizType, String assetId, String currentSnapshot) {
        // 降序遍历，仅标记最新一条与线上快照匹配的版本为 current
        final boolean[] marked = {false};
        return flowAssetVersionRepository.findByBizTypeAndAssetIdOrderByVersionNoDesc(bizType, assetId)
                .stream()
                .map(v -> {
                    FlowAssetVersionDTO dto = FlowAssetVersionDTO.fromDO(v, false);
                    boolean match = !marked[0]
                            && StrUtil.isNotBlank(currentSnapshot)
                            && Objects.equals(currentSnapshot, v.getSnapshot());
                    dto.setCurrent(match);
                    if (match) {
                        marked[0] = true;
                    }
                    return dto;
                })
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public FlowAssetVersionDO require(String versionId) {
        return flowAssetVersionRepository.findById(versionId)
                .orElseThrow(() -> new RuntimeException("历史版本不存在: " + versionId));
    }

    @Override
    @Transactional(readOnly = true)
    public FlowAssetVersionDO requireOwned(String versionId, String bizType, String assetId) {
        FlowAssetVersionDO version = require(versionId);
        if (!Objects.equals(bizType, version.getBizType()) || !Objects.equals(assetId, version.getAssetId())) {
            throw new RuntimeException("历史版本与资产不匹配");
        }
        return version;
    }

    private void trim(String bizType, String assetId) {
        int retention = resolveRetention();
        FlowAssetVersionDO latest = flowAssetVersionRepository
                .findTopByBizTypeAndAssetIdOrderByVersionNoDesc(bizType, assetId)
                .orElse(null);
        if (latest == null) {
            return;
        }
        int minKeep = latest.getVersionNo() - retention + 1;
        if (minKeep <= 1) {
            return;
        }
        int deleted = flowAssetVersionRepository.deleteOlderThan(bizType, assetId, minKeep);
        if (deleted > 0) {
            log.info("[FlowAssetVersion] 裁剪历史版本: bizType={}, assetId={}, retention={}, deleted={}",
                    bizType, assetId, retention, deleted);
        }
    }

    private int resolveRetention() {
        Integer cfg = sysConfigCacheManager.getIntConfig(
                AssetBizType.CONFIG_RETENTION_COUNT, AssetBizType.DEFAULT_RETENTION_COUNT);
        if (cfg == null || cfg < 1) {
            return AssetBizType.DEFAULT_RETENTION_COUNT;
        }
        return cfg;
    }
}
