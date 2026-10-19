package org.yu.flow.module.assetversion.service;

import cn.hutool.core.util.StrUtil;
import jakarta.annotation.Resource;
import org.springframework.transaction.annotation.Transactional;
import org.yu.flow.config.DemoModeGuard;
import org.yu.flow.module.assetref.FlowReferenceIndex;
import org.yu.flow.module.assetversion.AssetBizType;
import org.yu.flow.module.assetversion.domain.FlowAssetVersionDO;
import org.yu.flow.module.assetversion.dto.FlowAssetVersionDTO;
import org.yu.flow.module.log.audit.service.AuditLogService;
import org.yu.flow.module.release.service.PublishGateService;
import org.yu.flow.module.release.support.ReleaseEnvironment;
import org.yu.flow.util.JwtTokenUtil;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 可发布资产（接口 / 服务 / 定时任务 / MQ 任务）共用的发布生命周期：
 * 发布冻结快照、下线、草稿回滚到线上版本、回退历史版本。
 *
 * <p>每一步都记版本、刷新引用索引；子类提供快照内容、发布前校验和运行时联动（路由、调度、订阅）。</p>
 *
 * @param <T> 资产实体
 */
public abstract class PublishableAssetService<T> {

    @Resource
    protected DemoModeGuard demoModeGuard;

    @Resource
    protected FlowAssetVersionService flowAssetVersionService;

    @Resource
    protected FlowReferenceIndex flowReferenceIndex;

    @Resource
    protected AuditLogService auditLogService;

    @Resource
    protected PublishGateService publishGateService;

    @Resource
    protected ReleaseEnvironment releaseEnvironment;

    /** 审计与发布门禁里的资产类型，例如 {@code TASK} */
    protected abstract String assetType();

    /** 历史版本表里的类型，见 {@link AssetBizType} */
    protected abstract String versionBizType();

    /** 演示模式拦截提示里的资产名称 */
    protected abstract String demoTarget();

    /** 发布是否过门禁（回归只覆盖 API / TASK / SERVICE） */
    protected boolean gated() {
        return true;
    }

    /**
     * 发布、下线、回滚草稿时是否同步 updateTime。接口不同步：它的「有未发布变更」只看草稿与快照内容。
     */
    protected boolean tracksUpdateTime() {
        return true;
    }

    /** 回滚草稿时快照为空的提示 */
    protected abstract String noSnapshotMessage();

    protected abstract T require(String id);

    protected abstract T persist(T entity);

    /** 发布前校验，不通过直接抛异常 */
    protected abstract void checkPublishable(T entity);

    protected abstract String buildSnapshot(T entity);

    /** 快照字段写回草稿；旧快照缺的字段保持现值 */
    protected abstract void applySnapshotToDraft(T entity, String snapshot);

    /** 回退历史版本时写回草稿；默认同回滚草稿，启停类开关需要保持现值的类型覆盖 */
    protected void applyVersionToDraft(T entity, String snapshot) {
        applySnapshotToDraft(entity, snapshot);
    }

    protected abstract String publishedSnapshot(T entity);

    protected abstract LocalDateTime publishTime(T entity);

    protected abstract void setPublishState(T entity, Integer status, String snapshot);

    protected abstract void setPublishTime(T entity, LocalDateTime time);

    protected abstract void setUpdateTime(T entity, LocalDateTime time);

    /** 发布审计的 JSON 明细 */
    protected abstract String publishAuditDetail(T saved, String env);

    /** 写操作前的拦截（演示模式等）；接口还要挡住宿主保留接口 */
    protected void checkWritable(String id) {
        demoModeGuard.checkModifyOrDelete(id, demoTarget());
    }

    /** 发布后的运行时联动 */
    protected void afterPublished(T saved) {
    }

    protected void afterUnpublished(T saved) {
    }

    protected void afterRolledBack(T saved) {
    }

    protected void afterRestored(T saved) {
    }

    @Transactional(rollbackFor = Exception.class)
    public T publish(String id, String envCode) {
        checkWritable(id);
        if (gated()) {
            publishGateService.assertCanPublish(assetType(), id, envCode);
        }
        T entity = require(id);
        checkPublishable(entity);
        String snapshot = buildSnapshot(entity);
        LocalDateTime now = LocalDateTime.now();
        setPublishState(entity, 1, snapshot);
        setPublishTime(entity, now);
        if (tracksUpdateTime()) {
            setUpdateTime(entity, now);
        }
        T saved = persist(entity);
        flowAssetVersionService.append(versionBizType(), id, snapshot, AssetBizType.SOURCE_PUBLISH, null,
                JwtTokenUtil.currentUsername());
        afterPublished(saved);
        flowReferenceIndex.scheduleRebuildBroadcastAfterCommit();
        auditLogService.record(assetType() + "_PUBLISH", assetType(), id,
                publishAuditDetail(saved, releaseEnvironment.resolve(envCode)));
        return saved;
    }

    @Transactional(rollbackFor = Exception.class)
    public T unpublish(String id) {
        checkWritable(id);
        T entity = require(id);
        setPublishState(entity, 0, null);
        if (tracksUpdateTime()) {
            setUpdateTime(entity, LocalDateTime.now());
        }
        T saved = persist(entity);
        afterUnpublished(saved);
        flowReferenceIndex.scheduleRebuildBroadcastAfterCommit();
        return saved;
    }

    /** 把发布快照复制回草稿，updateTime 对齐到发布时间以消除「待更新发布」标记 */
    @Transactional(rollbackFor = Exception.class)
    public T rollbackToPublished(String id) {
        checkWritable(id);
        T entity = require(id);
        String snapshot = publishedSnapshot(entity);
        if (StrUtil.isBlank(snapshot)) {
            throw new RuntimeException(noSnapshotMessage());
        }
        applySnapshotToDraft(entity, snapshot);
        LocalDateTime publishedAt = publishTime(entity);
        setUpdateTime(entity, publishedAt != null || !tracksUpdateTime() ? publishedAt : LocalDateTime.now());
        T saved = persist(entity);
        afterRolledBack(saved);
        flowReferenceIndex.scheduleRebuildBroadcastAfterCommit();
        return saved;
    }

    @Transactional(readOnly = true)
    public List<FlowAssetVersionDTO> listVersions(String id) {
        return flowAssetVersionService.list(versionBizType(), id, publishedSnapshot(require(id)));
    }

    /** 线上快照与草稿同步为该历史版本并直接上线，避免编辑器仍显示回退前内容 */
    @Transactional(rollbackFor = Exception.class)
    public T restoreVersion(String id, String versionId) {
        checkWritable(id);
        T entity = require(id);
        FlowAssetVersionDO version = flowAssetVersionService.requireOwned(versionId, versionBizType(), id);
        LocalDateTime now = LocalDateTime.now();
        applyVersionToDraft(entity, version.getSnapshot());
        setPublishState(entity, 1, version.getSnapshot());
        setPublishTime(entity, now);
        setUpdateTime(entity, now);
        T saved = persist(entity);
        flowAssetVersionService.append(versionBizType(), id, version.getSnapshot(), AssetBizType.SOURCE_ROLLBACK,
                "回退至 v" + version.getVersionNo(), JwtTokenUtil.currentUsername());
        afterRestored(saved);
        flowReferenceIndex.scheduleRebuildBroadcastAfterCommit();
        return saved;
    }
}
