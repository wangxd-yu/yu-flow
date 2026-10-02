package org.yu.flow.module.release.service.impl;

import cn.hutool.core.util.StrUtil;
import jakarta.annotation.Resource;
import jakarta.persistence.criteria.Predicate;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.yu.flow.auto.dto.PageBean;
import org.yu.flow.auto.util.JwtTokenUtil;
import org.yu.flow.config.DemoModeGuard;
import org.yu.flow.exception.FlowException;
import org.yu.flow.log.audit.service.AuditLogService;
import org.yu.flow.module.release.domain.FlowReleaseDO;
import org.yu.flow.module.release.domain.FlowReleaseItemDO;
import org.yu.flow.module.release.dto.AddOfflineItemsDTO;
import org.yu.flow.module.release.dto.AddReleaseItemsDTO;
import org.yu.flow.module.release.dto.ReleaseCompareDTO;
import org.yu.flow.module.release.dto.ReleaseScanResultDTO;
import org.yu.flow.log.audit.AuditDetail;
import org.yu.flow.module.release.support.ReleaseReferenceChecker;
import org.yu.flow.module.release.support.ReleaseSigning;
import org.yu.flow.module.release.dto.ReleaseAssetOptionDTO;
import org.yu.flow.module.release.dto.ReleaseCheckResultDTO;
import org.yu.flow.module.release.dto.ReleaseDTO;
import org.yu.flow.module.release.dto.ReleaseItemDTO;
import org.yu.flow.module.release.dto.SaveReleaseDTO;
import org.yu.flow.module.release.repository.FlowRegressionRunRepository;
import org.yu.flow.module.release.repository.FlowReleaseItemRepository;
import org.yu.flow.module.release.repository.FlowReleaseRepository;
import org.yu.flow.module.release.service.ReleasePlanService;
import org.yu.flow.module.release.support.ReleaseAssetResolver;
import org.yu.flow.module.release.support.ReleaseAssetResolver.AssetInfo;
import org.yu.flow.module.release.support.ReleaseAssetResolver.AssetRef;
import org.yu.flow.module.release.support.ReleaseEnvironment;
import org.yu.flow.module.release.support.ReleasePackageFormat;
import org.yu.flow.module.release.support.ReleasePackageWriter;
import org.yu.flow.module.transfer.dto.AssetBundle;
import org.yu.flow.module.transfer.dto.AssetExportRequestDTO;
import org.yu.flow.module.transfer.service.AssetTransferService;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

@Slf4j
@Service
public class ReleasePlanServiceImpl implements ReleasePlanService {

    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");
    private static final Pattern CODE = ReleasePackageFormat.RELEASE_CODE;
    /** 单个版本单资产上限，防止依赖展开失控 */
    private static final int MAX_ITEMS = 500;
    /** 回归套件支持的资产类型 */
    private static final Set<String> REGRESSION_TYPES = Set.of(
            ReleaseAssetResolver.API, ReleaseAssetResolver.SERVICE, ReleaseAssetResolver.TASK);
    private static final int SCAN_LIMIT_PER_TYPE = 200;
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @Resource
    private FlowReleaseRepository flowReleaseRepository;
    @Resource
    private FlowReleaseItemRepository flowReleaseItemRepository;
    @Resource
    private ReleaseAssetResolver assetResolver;
    @Resource
    private AssetTransferService assetTransferService;
    @Resource
    private ReleaseEnvironment releaseEnvironment;
    @Resource
    private DemoModeGuard demoModeGuard;
    @Resource
    private AuditLogService auditLogService;
    @Resource
    private FlowRegressionRunRepository flowRegressionRunRepository;
    @Resource
    private ReleaseSigning releaseSigning;
    @Resource
    private ReleaseReferenceChecker referenceChecker;

    // ─────────────────────────────────────────────────────────────────────────
    // 版本单
    // ─────────────────────────────────────────────────────────────────────────

    @Override
    @Transactional(readOnly = true)
    public PageBean<ReleaseDTO> page(String keyword, String status, int page, int size) {
        String kw = StrUtil.trimToNull(keyword);
        Specification<FlowReleaseDO> spec = (root, query, cb) -> {
            List<Predicate> ps = new ArrayList<>();
            if (kw != null) {
                String like = "%" + kw.toLowerCase() + "%";
                ps.add(cb.or(cb.like(cb.lower(root.get("code")), like), cb.like(cb.lower(root.get("name")), like)));
            }
            if (StrUtil.isNotBlank(status)) {
                ps.add(cb.equal(root.get("status"), status.trim().toUpperCase()));
            }
            return cb.and(ps.toArray(new Predicate[0]));
        };
        Page<FlowReleaseDO> p = flowReleaseRepository.findAll(spec,
                PageRequest.of(Math.max(page, 1) - 1, Math.min(Math.max(size, 1), 100),
                        Sort.by(Sort.Direction.DESC, "createTime")));
        List<ReleaseDTO> items = p.getContent().stream().map(r -> {
            ReleaseDTO dto = ReleaseDTO.fromDO(r);
            dto.setItemCount(flowReleaseItemRepository.countByReleaseId(r.getId()));
            return dto;
        }).toList();
        return new PageBean<>(items, p.getNumber() + 1, p.getSize(), p.getTotalPages(), p.getTotalElements());
    }

    @Override
    @Transactional(readOnly = true)
    public List<ReleaseDTO> listDrafts() {
        return flowReleaseRepository.findAll(
                        (root, query, cb) -> cb.equal(root.get("status"), FlowReleaseDO.STATUS_DRAFT),
                        Sort.by(Sort.Direction.DESC, "createTime"))
                .stream().map(ReleaseDTO::fromDO).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public ReleaseDTO get(String id) {
        FlowReleaseDO release = require(id);
        ReleaseDTO dto = ReleaseDTO.fromDO(release);
        List<FlowReleaseItemDO> rows = items(id);
        Map<String, AssetInfo> infos = assetResolver.describeAll(rows.stream()
                .map(item -> new AssetRef(item.getAssetType(), item.getAssetId()))
                .toList());
        List<ReleaseItemDTO> items = new ArrayList<>();
        for (FlowReleaseItemDO item : rows) {
            items.add(toItemDTO(release, item,
                    infos.get(ReleaseAssetResolver.refKey(item.getAssetType(), item.getAssetId()))));
        }
        dto.setItems(items);
        dto.setItemCount(items.size());
        return dto;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ReleaseDTO create(SaveReleaseDTO dto) {
        assertWritable();
        String code = StrUtil.trimToEmpty(dto == null ? null : dto.getCode());
        if (!CODE.matcher(code).matches()) {
            throw new FlowException("RELEASE_INVALID", "版本号只能包含字母、数字、点、下划线、中划线，最长 64 位，如 v2026.10");
        }
        if (flowReleaseRepository.existsByCode(code)) {
            throw new FlowException("RELEASE_DUPLICATE", "版本号已存在: " + code);
        }
        LocalDateTime now = LocalDateTime.now(ZONE);
        String user = JwtTokenUtil.currentUsername();
        FlowReleaseDO saved = flowReleaseRepository.save(FlowReleaseDO.builder()
                .code(code)
                .name(StrUtil.trimToNull(dto.getName()))
                .remark(StrUtil.trimToNull(dto.getRemark()))
                .status(FlowReleaseDO.STATUS_DRAFT)
                .sourceEnv(releaseEnvironment.current())
                .createBy(user)
                .createTime(now)
                .updateBy(user)
                .updateTime(now)
                .build());
        audit("RELEASE_CREATE", saved);
        return ReleaseDTO.fromDO(saved);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ReleaseDTO update(String id, SaveReleaseDTO dto) {
        assertWritable();
        FlowReleaseDO release = require(id);
        if (dto.getName() != null) {
            release.setName(StrUtil.trimToNull(dto.getName()));
        }
        if (dto.getRemark() != null) {
            release.setRemark(StrUtil.trimToNull(dto.getRemark()));
        }
        touch(release);
        return ReleaseDTO.fromDO(flowReleaseRepository.save(release));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(String id) {
        assertWritable();
        FlowReleaseDO release = require(id);
        if (FlowReleaseDO.STATUS_EXPORTED.equals(release.getStatus())) {
            throw new FlowException("RELEASE_STATE", "已导出的版本单保留作为上线记录，不能删除");
        }
        flowReleaseItemRepository.deleteByReleaseId(id);
        flowReleaseRepository.delete(release);
        audit("RELEASE_DELETE", release);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 明细
    // ─────────────────────────────────────────────────────────────────────────

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ReleaseDTO addItems(String id, AddReleaseItemsDTO dto) {
        assertWritable();
        FlowReleaseDO release = requireDraft(id);
        boolean withDeps = !Boolean.FALSE.equals(dto.getIncludeDependencies());
        Set<String> existing = new LinkedHashSet<>();
        items(id).forEach(i -> existing.add(key(i.getAssetType(), i.getAssetId())));

        Deque<AssetRef> pending = new ArrayDeque<>();
        Map<String, String> origins = new HashMap<>();
        String explicitOrigin = FlowReleaseItemDO.ORIGIN_SCAN.equals(dto.getOrigin())
                ? FlowReleaseItemDO.ORIGIN_SCAN : FlowReleaseItemDO.ORIGIN_MANUAL;
        for (AddReleaseItemsDTO.AssetRefDTO ref : dto.getItems()) {
            String type = normalizeType(ref.getAssetType());
            if (StrUtil.isBlank(ref.getAssetId())) {
                continue;
            }
            pending.add(new AssetRef(type, ref.getAssetId()));
            origins.put(key(type, ref.getAssetId()), explicitOrigin);
        }

        String user = JwtTokenUtil.currentUsername();
        LocalDateTime now = LocalDateTime.now(ZONE);
        Set<String> visited = new LinkedHashSet<>();
        List<FlowReleaseItemDO> toSave = new ArrayList<>();
        while (!pending.isEmpty()) {
            AssetRef ref = pending.poll();
            String k = key(ref.type(), ref.id());
            if (!visited.add(k)) {
                continue;
            }
            AssetInfo info = assetResolver.describe(ref.type(), ref.id());
            if (info == null) {
                if (origins.containsKey(k)) {
                    throw new FlowException("RELEASE_ASSET_NOT_FOUND",
                            label(ref.type()) + "不存在或已删除: " + ref.id());
                }
                // 依赖已不存在时不阻断加入，冻结检查会提示
                continue;
            }
            String configRejected = ReleaseAssetResolver.SYS_CONFIG.equals(info.type())
                    ? assetResolver.configRejectReason(info.id()) : null;
            if (configRejected != null) {
                throw new FlowException("RELEASE_SENSITIVE_CONFIG",
                        "系统配置「" + info.name() + "」不能加入版本单：" + configRejected);
            }
            if (!existing.contains(k)) {
                if (existing.size() + toSave.size() >= MAX_ITEMS) {
                    throw new FlowException("RELEASE_TOO_LARGE", "单个版本单最多 " + MAX_ITEMS + " 个资产，请拆分");
                }
                toSave.add(FlowReleaseItemDO.builder()
                        .releaseId(id)
                        .assetType(info.type())
                        .assetId(info.id())
                        .assetName(info.name())
                        .assetKey(assetResolver.keyOf(info.type(), info.id()))
                        .action(FlowReleaseItemDO.ACTION_UPSERT)
                        .origin(origins.getOrDefault(k, FlowReleaseItemDO.ORIGIN_DEPENDENCY))
                        .createBy(user)
                        .createTime(now)
                        .build());
            }
            if (withDeps) {
                pending.addAll(info.dependencies());
            }
        }
        flowReleaseItemRepository.saveAll(toSave);
        touch(release);
        flowReleaseRepository.save(release);
        return get(id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ReleaseDTO addOfflineItems(String id, AddOfflineItemsDTO dto) {
        assertWritable();
        FlowReleaseDO release = requireDraft(id);
        Map<String, FlowReleaseItemDO> existing = new HashMap<>();
        items(id).forEach(i -> existing.put(key(i.getAssetType(), i.getAssetId()), i));
        String user = JwtTokenUtil.currentUsername();
        LocalDateTime now = LocalDateTime.now(ZONE);
        List<FlowReleaseItemDO> toSave = new ArrayList<>();
        for (AddOfflineItemsDTO.Item in : dto.getItems()) {
            String type = normalizeType(in.getAssetType());
            if (!ReleaseAssetResolver.OFFLINE_TYPES.contains(type)) {
                throw new FlowException("RELEASE_INVALID", label(type) + "没有在线状态，不能下线");
            }
            if (StrUtil.isBlank(in.getAssetId())) {
                continue;
            }
            FlowReleaseItemDO dup = existing.get(key(type, in.getAssetId()));
            if (dup != null) {
                if (FlowReleaseItemDO.ACTION_UPSERT.equals(dup.getAction())) {
                    throw new FlowException("RELEASE_INVALID",
                            label(type) + "「" + dup.getAssetName() + "」已作为更新项加入，不能同时下线");
                }
                continue;
            }
            AssetInfo info = assetResolver.describe(type, in.getAssetId());
            String assetKey = StrUtil.blankToDefault(in.getAssetKey(), assetResolver.keyOf(type, in.getAssetId()));
            if (ReleaseAssetResolver.KEYED_TYPES.contains(type) && StrUtil.isBlank(assetKey)) {
                throw new FlowException("RELEASE_INVALID", label(type) + "下线需要提供编码（目标环境按编码匹配）");
            }
            toSave.add(FlowReleaseItemDO.builder()
                    .releaseId(id)
                    .assetType(type)
                    .assetId(in.getAssetId())
                    .assetName(info != null ? info.name() : StrUtil.blankToDefault(in.getAssetName(), in.getAssetId()))
                    .assetKey(assetKey)
                    .action(FlowReleaseItemDO.ACTION_OFFLINE)
                    .origin(FlowReleaseItemDO.ORIGIN_MANUAL)
                    .createBy(user)
                    .createTime(now)
                    .build());
        }
        if (existing.size() + toSave.size() > MAX_ITEMS) {
            throw new FlowException("RELEASE_TOO_LARGE", "单个版本单最多 " + MAX_ITEMS + " 个资产，请拆分");
        }
        flowReleaseItemRepository.saveAll(toSave);
        touch(release);
        flowReleaseRepository.save(release);
        return get(id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ReleaseDTO removeItem(String id, String itemId) {
        assertWritable();
        FlowReleaseDO release = requireDraft(id);
        FlowReleaseItemDO item = flowReleaseItemRepository.findById(itemId)
                .filter(i -> id.equals(i.getReleaseId()))
                .orElseThrow(() -> new FlowException("RELEASE_ITEM_NOT_FOUND", "明细不存在: " + itemId));
        flowReleaseItemRepository.delete(item);
        touch(release);
        flowReleaseRepository.save(release);
        return get(id);
    }

    @Override
    @Transactional(readOnly = true)
    public ReleaseScanResultDTO scanChanges(String id, String since) {
        require(id);
        ReleaseScanResultDTO result = new ReleaseScanResultDTO();
        FlowReleaseDO baseline = flowReleaseRepository.findAll(
                        (root, query, cb) -> cb.and(cb.equal(root.get("status"), FlowReleaseDO.STATUS_EXPORTED),
                                cb.notEqual(root.get("id"), id)),
                        Sort.by(Sort.Direction.DESC, "exportedTime"))
                .stream().findFirst().orElse(null);
        LocalDateTime from;
        if (StrUtil.isNotBlank(since)) {
            try {
                from = LocalDateTime.parse(since.trim(), DATE_TIME);
            } catch (Exception e) {
                throw new FlowException("RELEASE_INVALID", "起始时间格式应为 yyyy-MM-dd HH:mm:ss");
            }
        } else if (baseline != null && baseline.getExportedTime() != null) {
            from = baseline.getExportedTime();
            result.setBaselineCode(baseline.getCode());
        } else {
            throw new FlowException("RELEASE_SCAN_NO_BASELINE", "还没有已导出的版本可作为基线，请指定扫描起始时间");
        }
        result.setSince(from.format(DATE_TIME));

        Set<String> included = new LinkedHashSet<>();
        items(id).forEach(i -> included.add(key(i.getAssetType(), i.getAssetId())));
        for (String type : ReleaseAssetResolver.ORDERED_TYPES) {
            for (AssetInfo info : assetResolver.changedSince(type, from, SCAN_LIMIT_PER_TYPE)) {
                if (included.contains(key(type, info.id()))) {
                    continue;
                }
                boolean selectable = true;
                String reason = (info.publishable() ? "发布于 " : "修改于 ") + result.getSince() + " 之后";
                if (info.unpublishedChanges()) {
                    reason += "；另有未发布的修改（冻结前需先发布或回滚）";
                }
                String configRejected = ReleaseAssetResolver.SYS_CONFIG.equals(type)
                        ? assetResolver.configRejectReason(info.id()) : null;
                if (configRejected != null) {
                    selectable = false;
                    reason = configRejected;
                }
                result.getCandidates().add(new ReleaseScanResultDTO.Candidate(type, info.id(),
                        assetResolver.keyOf(type, info.id()), info.name(), info.detail(),
                        FlowReleaseItemDO.ACTION_UPSERT, reason, selectable));
            }
        }
        if (baseline != null && result.getBaselineCode() != null) {
            for (FlowReleaseItemDO item : items(baseline.getId())) {
                String type = item.getAssetType();
                if (isOffline(item) || !ReleaseAssetResolver.OFFLINE_TYPES.contains(type)
                        || included.contains(key(type, item.getAssetId()))
                        || assetResolver.describe(type, item.getAssetId()) != null) {
                    continue;
                }
                result.getCandidates().add(new ReleaseScanResultDTO.Candidate(type, item.getAssetId(),
                        item.getAssetKey(), item.getAssetName(), null, FlowReleaseItemDO.ACTION_OFFLINE,
                        "上个版本 " + baseline.getCode() + " 包含，当前环境已删除，建议在目标环境下线", true));
            }
        }
        return result;
    }

    @Override
    @Transactional(readOnly = true)
    public ReleaseCompareDTO compare(String baseId, String targetId) {
        FlowReleaseDO base = require(baseId);
        FlowReleaseDO target = require(targetId);
        ReleaseCompareDTO result = new ReleaseCompareDTO();
        result.setBaseCode(base.getCode());
        result.setTargetCode(target.getCode());
        boolean comparable = !FlowReleaseDO.STATUS_DRAFT.equals(base.getStatus())
                && !FlowReleaseDO.STATUS_DRAFT.equals(target.getStatus());
        result.setContentComparable(comparable);
        Map<String, FlowReleaseItemDO> baseItems = new LinkedHashMap<>();
        items(baseId).forEach(i -> baseItems.put(key(i.getAssetType(), i.getAssetId()), i));
        Map<String, FlowReleaseItemDO> targetItems = new LinkedHashMap<>();
        items(targetId).forEach(i -> targetItems.put(key(i.getAssetType(), i.getAssetId()), i));
        for (Map.Entry<String, FlowReleaseItemDO> e : baseItems.entrySet()) {
            FlowReleaseItemDO b = e.getValue();
            FlowReleaseItemDO t = targetItems.get(e.getKey());
            if (t == null) {
                result.getOnlyInBase().add(new ReleaseCompareDTO.Entry(b.getAssetType(), b.getAssetId(), b.getAssetName(),
                        b.getAction(), null));
            } else if (!Objects.equals(b.getAction(), t.getAction())
                    || (comparable && !Objects.equals(b.getContentHash(), t.getContentHash()))) {
                result.getChanged().add(new ReleaseCompareDTO.Entry(t.getAssetType(), t.getAssetId(), t.getAssetName(),
                        b.getAction(), t.getAction()));
            } else {
                result.setUnchangedCount(result.getUnchangedCount() + 1);
            }
        }
        targetItems.forEach((k, t) -> {
            if (!baseItems.containsKey(k)) {
                result.getOnlyInTarget().add(new ReleaseCompareDTO.Entry(t.getAssetType(), t.getAssetId(), t.getAssetName(),
                        null, t.getAction()));
            }
        });
        return result;
    }

    @Override
    @Transactional(readOnly = true)
    public List<ReleaseAssetOptionDTO> searchAssets(String assetType, String keyword) {
        return assetResolver.search(normalizeType(assetType), keyword).stream()
                .map(i -> new ReleaseAssetOptionDTO(i.type(), i.id(), i.name(), i.detail(),
                        i.publishable(), i.published(), i.unpublishedChanges()))
                .toList();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 冻结 / 导出
    // ─────────────────────────────────────────────────────────────────────────

    @Override
    @Transactional(readOnly = true)
    public ReleaseCheckResultDTO check(String id) {
        require(id);
        return runCheck(items(id), new HashMap<>());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ReleaseCheckResultDTO freeze(String id) {
        assertWritable();
        FlowReleaseDO release = requireDraft(id);
        List<FlowReleaseItemDO> items = items(id);
        Map<String, AssetInfo> infos = new HashMap<>();
        ReleaseCheckResultDTO result = runCheck(items, infos);
        if (!result.isPassed()) {
            return result;
        }
        for (FlowReleaseItemDO item : items) {
            if (isOffline(item)) {
                continue;
            }
            AssetInfo info = infos.get(key(item.getAssetType(), item.getAssetId()));
            item.setContentHash(info.contentHash());
            item.setAssetName(info.name());
        }
        flowReleaseItemRepository.saveAll(items);
        release.setStatus(FlowReleaseDO.STATUS_FROZEN);
        release.setFrozenBy(JwtTokenUtil.currentUsername());
        release.setFrozenTime(LocalDateTime.now(ZONE));
        touch(release);
        flowReleaseRepository.save(release);
        audit("RELEASE_FREEZE", release, "items", items.size());
        result.setFrozen(true);
        return result;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ReleaseDTO unfreeze(String id) {
        assertWritable();
        FlowReleaseDO release = require(id);
        if (FlowReleaseDO.STATUS_DRAFT.equals(release.getStatus())) {
            return get(id);
        }
        List<FlowReleaseItemDO> items = items(id);
        items.forEach(i -> i.setContentHash(null));
        flowReleaseItemRepository.saveAll(items);
        release.setStatus(FlowReleaseDO.STATUS_DRAFT);
        release.setFrozenBy(null);
        release.setFrozenTime(null);
        touch(release);
        flowReleaseRepository.save(release);
        audit("RELEASE_UNFREEZE", release);
        return get(id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PackageFile exportPackage(String id) {
        FlowReleaseDO release = require(id);
        if (FlowReleaseDO.STATUS_DRAFT.equals(release.getStatus())) {
            throw new FlowException("RELEASE_STATE", "请先冻结版本单再导出");
        }
        List<FlowReleaseItemDO> items = items(id);
        List<String> drifted = new ArrayList<>();
        for (FlowReleaseItemDO item : items) {
            if (isOffline(item)) {
                continue;
            }
            AssetInfo info = assetResolver.describe(item.getAssetType(), item.getAssetId());
            if (info == null || !Objects.equals(info.contentHash(), item.getContentHash())) {
                drifted.add(label(item.getAssetType()) + "「" + item.getAssetName() + "」");
            }
        }
        if (!drifted.isEmpty()) {
            throw new FlowException("RELEASE_DRIFTED",
                    "以下资产在冻结后发生了变化，请解冻后重新冻结：" + String.join("、", drifted));
        }

        AssetExportRequestDTO request = new AssetExportRequestDTO();
        request.setApiIds(idsOf(items, ReleaseAssetResolver.API));
        request.setServiceIds(idsOf(items, ReleaseAssetResolver.SERVICE));
        request.setTaskIds(idsOf(items, ReleaseAssetResolver.TASK));
        request.setMqTaskIds(idsOf(items, ReleaseAssetResolver.MQ_TASK));
        request.setResponseTemplateIds(idsOf(items, ReleaseAssetResolver.RESPONSE_TEMPLATE));
        request.setPageIds(idsOf(items, ReleaseAssetResolver.PAGE));
        request.setModelIds(idsOf(items, ReleaseAssetResolver.MODEL));
        request.setSysMacroIds(idsOf(items, ReleaseAssetResolver.SYS_MACRO));
        request.setSysConfigIds(idsOf(items, ReleaseAssetResolver.SYS_CONFIG));
        request.setOpenPlatformIds(idsOf(items, ReleaseAssetResolver.OPEN_PLATFORM));
        request.setAlertRuleIds(idsOf(items, ReleaseAssetResolver.ALERT_RULE));
        // 版本单里已包含冻结时确认过的依赖，这里不再自动展开，保证导出范围与清单一致
        request.setIncludeDependencies(false);
        request.setIncludeRegression(true);
        request.setContentSource(AssetExportRequestDTO.SOURCE_PUBLISHED_FIRST);
        request.setSourceEnv(releaseEnvironment.current());
        boolean hasUpserts = items.stream().anyMatch(i -> !isOffline(i));
        AssetBundle bundle = hasUpserts ? assetTransferService.export(request) : emptyBundle();

        ReleasePackageFormat.ReleaseInfo info = new ReleasePackageFormat.ReleaseInfo();
        info.setCode(release.getCode());
        info.setName(release.getName());
        info.setRemark(release.getRemark());
        String env = releaseEnvironment.current();
        for (FlowReleaseItemDO item : items) {
            ReleasePackageFormat.ReleaseEntry entry = new ReleasePackageFormat.ReleaseEntry();
            entry.setAssetType(item.getAssetType());
            entry.setAssetId(item.getAssetId());
            entry.setAssetName(item.getAssetName());
            entry.setAssetKey(item.getAssetKey());
            entry.setAction(item.getAction());
            entry.setOrigin(item.getOrigin());
            entry.setContentHash(item.getContentHash());
            if (!isOffline(item)) {
                attachRegressionEvidence(entry, env);
            }
            info.getItems().add(entry);
        }

        ReleasePackageWriter.Result pkg;
        try {
            pkg = ReleasePackageWriter.write(info, bundle, releaseSigning.key());
        } catch (Exception e) {
            throw new FlowException("RELEASE_EXPORT_FAILED", "生成发布包失败: " + e.getMessage());
        }
        LocalDateTime now = LocalDateTime.now(ZONE);
        release.setStatus(FlowReleaseDO.STATUS_EXPORTED);
        release.setExportedBy(JwtTokenUtil.currentUsername());
        release.setExportedTime(now);
        release.setPackageDigest(pkg.digest());
        touch(release);
        flowReleaseRepository.save(release);
        audit("RELEASE_EXPORT", release, "digest", pkg.digest());
        String fileName = "yu-flow-release-" + release.getCode() + "-"
                + now.format(DateTimeFormatter.ofPattern("yyyyMMddHHmm")) + ReleasePackageFormat.EXTENSION;
        return new PackageFile(fileName, pkg.bytes());
    }

    /** 只认当前线上版本发布之后的通过记录，更早的回归测的是旧版本 */
    private void attachRegressionEvidence(ReleasePackageFormat.ReleaseEntry entry, String env) {
        if (!REGRESSION_TYPES.contains(entry.getAssetType())) {
            return;
        }
        LocalDateTime publishTime = assetResolver.publishTime(entry.getAssetType(), entry.getAssetId());
        if (publishTime == null) {
            return;
        }
        flowRegressionRunRepository
                .findFirstByAssetTypeAndAssetIdAndEnvCodeAndStatusAndFinishedAtAfterOrderByFinishedAtDesc(
                        entry.getAssetType(), entry.getAssetId(), env, "PASSED", publishTime)
                .ifPresent(run -> {
                    entry.setRegressionEnv(env);
                    entry.setRegressionRunId(run.getId());
                    entry.setRegressionPassedAt(run.getFinishedAt().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
                });
    }

    /**
     * 冻结检查：可发布资产必须已发布且无未发布改动（导出取线上版本，保证带到生产的就是测过的那一版）；
     * 引用了未纳入清单的资产给出提示。
     */
    private ReleaseCheckResultDTO runCheck(List<FlowReleaseItemDO> items, Map<String, AssetInfo> infosOut) {
        ReleaseCheckResultDTO result = new ReleaseCheckResultDTO();
        if (items.isEmpty()) {
            result.error(null, null, null, "版本单为空，请先加入资产");
        }
        Set<String> included = new LinkedHashSet<>();
        items.forEach(i -> included.add(key(i.getAssetType(), i.getAssetId())));
        Set<String> warnedDeps = new LinkedHashSet<>();
        for (FlowReleaseItemDO item : items) {
            if (isOffline(item)) {
                if (assetResolver.isOnline(item.getAssetType(), item.getAssetId())) {
                    result.warn(item.getAssetType(), item.getAssetId(), item.getAssetName(),
                            "下线项在当前环境仍处于在线状态，请确认确实要在目标环境下线");
                }
                if (ReleaseAssetResolver.KEYED_TYPES.contains(item.getAssetType()) && StrUtil.isBlank(item.getAssetKey())) {
                    result.error(item.getAssetType(), item.getAssetId(), item.getAssetName(), "缺少编码，目标环境无法匹配");
                }
                continue;
            }
            AssetInfo info = assetResolver.describe(item.getAssetType(), item.getAssetId());
            if (info == null) {
                result.error(item.getAssetType(), item.getAssetId(), item.getAssetName(),
                        "资产不存在或已删除，请从版本单移除；如需在目标环境下线，请改为加入下线项");
                continue;
            }
            infosOut.put(key(info.type(), info.id()), info);
            if (info.publishable() && !info.published()) {
                result.error(info.type(), info.id(), info.name(), "未发布：发布包取线上版本，请先在当前环境发布并完成测试");
            } else if (info.publishable() && info.unpublishedChanges()) {
                result.error(info.type(), info.id(), info.name(), "有未发布的修改：发布包取线上版本，请先发布或回滚草稿");
            }
            for (AssetRef dep : info.dependencies()) {
                String depKey = key(dep.type(), dep.id());
                if (included.contains(depKey) || !warnedDeps.add(depKey)) {
                    continue;
                }
                AssetInfo depInfo = assetResolver.describe(dep.type(), dep.id());
                String depLabel = label(dep.type()) + "「" + (depInfo == null ? dep.id() : depInfo.name()) + "」";
                result.warn(info.type(), info.id(), info.name(), depInfo == null
                        ? "引用的" + depLabel + "在当前环境已不存在"
                        : "引用的" + depLabel + "未加入版本单；若生产环境没有或版本不一致，调用会失败");
            }
        }
        List<AssetRef> offline = items.stream().filter(ReleasePlanServiceImpl::isOffline)
                .map(i -> new AssetRef(i.getAssetType(), i.getAssetId())).toList();
        if (!offline.isEmpty()) {
            Map<AssetRef, String> names = new HashMap<>();
            items.forEach(i -> names.put(new AssetRef(i.getAssetType(), i.getAssetId()), i.getAssetName()));
            for (ReleaseReferenceChecker.Conflict c : referenceChecker.offlineConflicts(offline, Map.of(), names)) {
                result.warn(c.target().type(), c.target().id(), c.targetName(), "当前环境仍有在线资产调用它："
                        + String.join("、", c.callers()) + "；目标环境若同样如此，导入时会被阻断，请一起下线调用方或先改掉调用");
            }
        }
        result.setPassed(!result.hasError());
        return result;
    }

    private ReleaseItemDTO toItemDTO(FlowReleaseDO release, FlowReleaseItemDO item, AssetInfo info) {
        ReleaseItemDTO dto = new ReleaseItemDTO();
        dto.setId(item.getId());
        dto.setAssetType(item.getAssetType());
        dto.setAssetId(item.getAssetId());
        dto.setAssetName(info != null ? info.name() : item.getAssetName());
        dto.setAssetKey(item.getAssetKey());
        dto.setAction(item.getAction());
        dto.setOrigin(item.getOrigin());
        dto.setContentHash(item.getContentHash());
        dto.setExists(info != null);
        if (info != null) {
            dto.setDetail(info.detail());
            dto.setPublishable(info.publishable());
            dto.setPublished(info.published());
            dto.setUnpublishedChanges(info.unpublishedChanges());
        }
        boolean frozen = !FlowReleaseDO.STATUS_DRAFT.equals(release.getStatus());
        dto.setDrifted(frozen && !isOffline(item)
                && (info == null || !Objects.equals(info.contentHash(), item.getContentHash())));
        return dto;
    }

    private List<FlowReleaseItemDO> items(String releaseId) {
        return flowReleaseItemRepository.findByReleaseIdOrderByCreateTimeAsc(releaseId);
    }

    private static boolean isOffline(FlowReleaseItemDO item) {
        return FlowReleaseItemDO.ACTION_OFFLINE.equals(item.getAction());
    }

    private static String label(String type) {
        return ReleaseAssetResolver.LABELS.getOrDefault(type, type);
    }

    /** 只有下线项时不需要导出资产，给一个合法的空资产包 */
    private AssetBundle emptyBundle() {
        AssetBundle bundle = new AssetBundle();
        bundle.setKind(AssetBundle.KIND);
        bundle.setSchemaVersion(AssetBundle.SCHEMA_VERSION);
        bundle.setExportedAt(LocalDateTime.now(ZONE).format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
        bundle.setExportedBy(JwtTokenUtil.currentUsername());
        bundle.setSourceEnv(releaseEnvironment.current());
        bundle.setContentSource(AssetExportRequestDTO.SOURCE_PUBLISHED_FIRST);
        return bundle;
    }

    /** 导出只带更新项，下线项只写在 release.json 里 */
    private static List<String> idsOf(List<FlowReleaseItemDO> items, String type) {
        return items.stream().filter(i -> type.equals(i.getAssetType()) && !isOffline(i))
                .map(FlowReleaseItemDO::getAssetId).toList();
    }

    private static String key(String type, String id) {
        return type + ":" + id;
    }

    private static String normalizeType(String type) {
        try {
            return ReleaseAssetResolver.normalizeType(type);
        } catch (IllegalArgumentException e) {
            throw new FlowException("RELEASE_INVALID", e.getMessage());
        }
    }

    private void touch(FlowReleaseDO release) {
        release.setUpdateBy(JwtTokenUtil.currentUsername());
        release.setUpdateTime(LocalDateTime.now(ZONE));
    }

    private FlowReleaseDO require(String id) {
        return flowReleaseRepository.findById(id)
                .orElseThrow(() -> new FlowException("RELEASE_NOT_FOUND", "版本单不存在: " + id));
    }

    private FlowReleaseDO requireDraft(String id) {
        FlowReleaseDO release = require(id);
        if (!FlowReleaseDO.STATUS_DRAFT.equals(release.getStatus())) {
            throw new FlowException("RELEASE_STATE", "版本单已冻结，如需调整请先解冻");
        }
        return release;
    }

    private void assertWritable() {
        if (demoModeGuard.isDemoMode()) {
            throw new FlowException("DEMO_RESTRICTED", "演示模式下不允许维护版本单");
        }
    }

    /** @param extra 附加明细，键值交替 */
    private void audit(String action, FlowReleaseDO release, Object... extra) {
        Object[] kv = new Object[extra.length + 2];
        kv[0] = "code";
        kv[1] = release.getCode();
        System.arraycopy(extra, 0, kv, 2, extra.length);
        auditLogService.record(action, "RELEASE", release.getId(), AuditDetail.of(kv));
    }
}
