package org.yu.flow.module.release.service.impl;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.jayway.jsonpath.JsonPath;
import com.jayway.jsonpath.PathNotFoundException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.yu.flow.auto.dto.PageBean;
import org.yu.flow.auto.util.JwtTokenUtil;
import org.yu.flow.config.DemoModeGuard;
import org.yu.flow.engine.evaluator.FlowEngine;
import org.yu.flow.engine.model.FlowTrace;
import org.yu.flow.exception.ValidationException;
import org.yu.flow.log.audit.service.AuditLogService;
import org.yu.flow.module.api.domain.FlowApiDO;
import org.yu.flow.module.api.repository.FlowApiRepository;
import org.yu.flow.module.release.domain.*;
import org.yu.flow.module.release.dto.*;
import org.yu.flow.module.release.repository.*;
import org.yu.flow.module.release.service.ReleaseManageService;
import org.yu.flow.module.release.support.RegressionSecurity;
import org.yu.flow.module.serviceflow.domain.FlowServiceFlowDO;
import org.yu.flow.module.serviceflow.repository.FlowServiceFlowRepository;
import org.yu.flow.module.task.domain.FlowTaskDO;
import org.yu.flow.module.task.repository.FlowTaskRepository;

import jakarta.annotation.Resource;
import jakarta.persistence.criteria.Predicate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

@Service
public class ReleaseManageServiceImpl implements ReleaseManageService {

    private static final Semaphore RUN_PERMITS = new Semaphore(RegressionSecurity.MAX_CONCURRENT_RUNS);
    private static final AtomicInteger ACTIVE_RUNS = new AtomicInteger(0);

    @Resource
    private FlowEnvRepository flowEnvRepository;
    @Resource
    private FlowRegressionSuiteRepository suiteRepository;
    @Resource
    private FlowRegressionCaseRepository caseRepository;
    @Resource
    private FlowRegressionRunRepository runRepository;
    @Resource
    private FlowRegressionRunCaseRepository runCaseRepository;
    @Resource
    private FlowApiRepository flowApiRepository;
    @Resource
    private FlowTaskRepository flowTaskRepository;
    @Resource
    private FlowServiceFlowRepository flowServiceFlowRepository;
    @Resource
    private FlowEngine flowEngine;
    @Resource
    private DemoModeGuard demoModeGuard;
    @Resource
    private AuditLogService auditLogService;
    @Resource
    private org.yu.flow.module.release.service.PublishGateService publishGateService;

    @Override
    public List<FlowEnvDTO> listEnvs() {
        return flowEnvRepository.findByEnabledOrderBySortOrderAsc(1).stream()
                .map(FlowEnvDTO::fromDO)
                .collect(Collectors.toList());
    }

    @Override
    public PublishGateResultDTO checkGate(String assetType, String assetId, String envCode) {
        return publishGateService.check(assetType, assetId, envCode);
    }

    @Override
    public PageBean<RegressionSuiteDTO> pageSuites(String assetType, String assetId, int page, int size) {
        Specification<FlowRegressionSuiteDO> spec = (root, query, cb) -> {
            List<Predicate> ps = new ArrayList<>();
            if (StrUtil.isNotBlank(assetType)) {
                ps.add(cb.equal(root.get("assetType"),
                        RegressionSecurity.normalizeAssetType(assetType)));
            }
            if (StrUtil.isNotBlank(assetId)) {
                ps.add(cb.equal(root.get("assetId"), assetId));
            }
            return cb.and(ps.toArray(new Predicate[0]));
        };
        Page<FlowRegressionSuiteDO> p = suiteRepository.findAll(spec,
                PageRequest.of(Math.max(page - 1, 0), Math.max(size, 1),
                        Sort.by(Sort.Direction.DESC, "updateTime")));
        List<RegressionSuiteDTO> items = p.getContent().stream().map(d -> {
            RegressionSuiteDTO dto = RegressionSuiteDTO.fromDO(d);
            dto.setCaseCount((int) caseRepository.countBySuiteId(d.getId()));
            return dto;
        }).collect(Collectors.toList());
        return new PageBean<>(items, p.getNumber() + 1, p.getSize(), p.getTotalPages(), p.getTotalElements());
    }

    @Override
    public RegressionSuiteDTO getSuite(String id, boolean withCases) {
        FlowRegressionSuiteDO suite = suiteRepository.findById(id)
                .orElseThrow(() -> new ValidationException("套件不存在"));
        RegressionSuiteDTO dto = RegressionSuiteDTO.fromDO(suite);
        dto.setCaseCount((int) caseRepository.countBySuiteId(id));
        if (withCases) {
            dto.setCases(caseRepository.findBySuiteIdOrderBySortOrderAsc(id).stream()
                    .map(RegressionCaseDTO::fromDO)
                    .collect(Collectors.toList()));
        }
        return dto;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public RegressionSuiteDTO createSuite(SaveRegressionSuiteDTO dto) {
        String type = RegressionSecurity.normalizeAssetType(dto.getAssetType());
        if (StrUtil.isBlank(dto.getAssetId())) {
            throw new ValidationException("assetId 不能为空");
        }
        demoModeGuard.checkModifyOrDelete(dto.getAssetId(), "回归套件");
        ensureAssetExists(type, dto.getAssetId());
        if (suiteRepository.countByAssetTypeAndAssetId(type, dto.getAssetId()) >= 5) {
            throw new ValidationException("同一资产最多 5 个回归套件");
        }
        LocalDateTime now = LocalDateTime.now();
        FlowRegressionSuiteDO suite = FlowRegressionSuiteDO.builder()
                .name(StrUtil.blankToDefault(dto.getName(), "默认套件"))
                .assetType(type)
                .assetId(dto.getAssetId())
                .enabled(dto.getEnabled() == null ? 1 : dto.getEnabled())
                .createTime(now)
                .updateTime(now)
                .build();
        return RegressionSuiteDTO.fromDO(suiteRepository.save(suite));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public RegressionSuiteDTO updateSuite(String id, SaveRegressionSuiteDTO dto) {
        FlowRegressionSuiteDO suite = suiteRepository.findById(id)
                .orElseThrow(() -> new ValidationException("套件不存在"));
        demoModeGuard.checkModifyOrDelete(suite.getAssetId(), "回归套件");
        if (StrUtil.isNotBlank(dto.getName())) {
            suite.setName(dto.getName().trim());
        }
        if (dto.getEnabled() != null) {
            suite.setEnabled(dto.getEnabled());
        }
        suite.setUpdateTime(LocalDateTime.now());
        return RegressionSuiteDTO.fromDO(suiteRepository.save(suite));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteSuite(String id) {
        FlowRegressionSuiteDO suite = suiteRepository.findById(id)
                .orElseThrow(() -> new ValidationException("套件不存在"));
        demoModeGuard.checkModifyOrDelete(suite.getAssetId(), "回归套件");
        caseRepository.deleteBySuiteId(id);
        suiteRepository.delete(suite);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public RegressionCaseDTO createCase(String suiteId, SaveRegressionCaseDTO dto) {
        FlowRegressionSuiteDO suite = suiteRepository.findById(suiteId)
                .orElseThrow(() -> new ValidationException("套件不存在"));
        demoModeGuard.checkModifyOrDelete(suite.getAssetId(), "回归用例");
        if (caseRepository.countBySuiteId(suiteId) >= RegressionSecurity.MAX_CASES_PER_SUITE) {
            throw new ValidationException("单套件最多 "
                    + RegressionSecurity.MAX_CASES_PER_SUITE + " 条用例");
        }
        FlowRegressionCaseDO c = applyCaseFields(new FlowRegressionCaseDO(), dto);
        c.setSuiteId(suiteId);
        LocalDateTime now = LocalDateTime.now();
        c.setCreateTime(now);
        c.setUpdateTime(now);
        suite.setUpdateTime(now);
        suiteRepository.save(suite);
        return RegressionCaseDTO.fromDO(caseRepository.save(c));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public RegressionCaseDTO updateCase(String caseId, SaveRegressionCaseDTO dto) {
        FlowRegressionCaseDO c = caseRepository.findById(caseId)
                .orElseThrow(() -> new ValidationException("用例不存在"));
        FlowRegressionSuiteDO suite = suiteRepository.findById(c.getSuiteId())
                .orElseThrow(() -> new ValidationException("套件不存在"));
        demoModeGuard.checkModifyOrDelete(suite.getAssetId(), "回归用例");
        applyCaseFields(c, dto);
        c.setUpdateTime(LocalDateTime.now());
        suite.setUpdateTime(LocalDateTime.now());
        suiteRepository.save(suite);
        return RegressionCaseDTO.fromDO(caseRepository.save(c));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteCase(String caseId) {
        FlowRegressionCaseDO c = caseRepository.findById(caseId)
                .orElseThrow(() -> new ValidationException("用例不存在"));
        FlowRegressionSuiteDO suite = suiteRepository.findById(c.getSuiteId())
                .orElseThrow(() -> new ValidationException("套件不存在"));
        demoModeGuard.checkModifyOrDelete(suite.getAssetId(), "回归用例");
        caseRepository.delete(c);
    }

    @Override
    public RegressionRunDTO runSuite(String suiteId, String envCode) {
        FlowRegressionSuiteDO suite = suiteRepository.findById(suiteId)
                .orElseThrow(() -> new ValidationException("套件不存在"));
        demoModeGuard.checkModifyOrDelete(suite.getAssetId(), "回归运行");
        if (!Integer.valueOf(1).equals(suite.getEnabled())) {
            throw new ValidationException("套件已停用");
        }
        String env = RegressionSecurity.normalizeEnvCode(envCode);
        flowEnvRepository.findByCode(env)
                .filter(e -> Integer.valueOf(1).equals(e.getEnabled()))
                .orElseThrow(() -> new ValidationException("环境不可用: " + env));

        boolean acquired;
        try {
            acquired = RUN_PERMITS.tryAcquire(1, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ValidationException("获取运行许可被中断");
        }
        if (!acquired) {
            throw new ValidationException("回归运行过于频繁，请稍后重试（并发上限 "
                    + RegressionSecurity.MAX_CONCURRENT_RUNS + "）");
        }
        ACTIVE_RUNS.incrementAndGet();
        try {
            return doRun(suite, env);
        } finally {
            ACTIVE_RUNS.decrementAndGet();
            RUN_PERMITS.release();
        }
    }

    private RegressionRunDTO doRun(FlowRegressionSuiteDO suite, String env) {
        List<FlowRegressionCaseDO> cases =
                caseRepository.findBySuiteIdAndEnabledOrderBySortOrderAsc(suite.getId(), 1);
        if (cases.isEmpty()) {
            throw new ValidationException("套件没有启用中的用例");
        }

        AssetExecContext ctx = resolveExecContext(suite.getAssetType(), suite.getAssetId());

        FlowRegressionRunDO run = FlowRegressionRunDO.builder()
                .suiteId(suite.getId())
                .assetType(suite.getAssetType())
                .assetId(suite.getAssetId())
                .envCode(env)
                .status("RUNNING")
                .totalCases(cases.size())
                .passedCases(0)
                .failedCases(0)
                .startedAt(LocalDateTime.now())
                .triggeredBy(JwtTokenUtil.currentUsername())
                .build();
        run = runRepository.save(run);

        int passed = 0;
        int failed = 0;
        List<RegressionRunCaseDTO> details = new ArrayList<>();

        for (FlowRegressionCaseDO c : cases) {
            FlowRegressionRunCaseDO rc = executeOneCase(run.getId(), ctx, c);
            runCaseRepository.save(rc);
            details.add(RegressionRunCaseDTO.fromDO(rc));
            if ("PASSED".equals(rc.getStatus())) {
                passed++;
            } else {
                failed++;
            }
        }

        run.setPassedCases(passed);
        run.setFailedCases(failed);
        run.setFinishedAt(LocalDateTime.now());
        run.setStatus(failed == 0 ? "PASSED" : "FAILED");
        run.setSummary(failed == 0
                ? "全部通过 " + passed + "/" + cases.size()
                : "失败 " + failed + "/" + cases.size());
        run = runRepository.save(run);

        try {
            auditLogService.record("REGRESSION_RUN", suite.getAssetType(), suite.getAssetId(),
                    "{\"runId\":\"" + run.getId() + "\",\"env\":\"" + env
                            + "\",\"status\":\"" + run.getStatus() + "\"}");
        } catch (Exception ignored) {
        }

        RegressionRunDTO dto = RegressionRunDTO.fromDO(run);
        dto.setCases(details);
        return dto;
    }

    private FlowRegressionRunCaseDO executeOneCase(String runId, AssetExecContext ctx,
                                                   FlowRegressionCaseDO c) {
        long start = System.currentTimeMillis();
        FlowRegressionRunCaseDO rc = FlowRegressionRunCaseDO.builder()
                .runId(runId)
                .caseId(c.getId())
                .caseName(c.getName())
                .build();
        try {
            if (StrUtil.isBlank(ctx.dslContent)) {
                rc.setStatus("ERROR");
                rc.setMessage("资产 DSL 为空，无法执行回归");
                return finish(rc, start);
            }
            if ("API".equals(ctx.assetType) && StrUtil.isNotBlank(ctx.serviceType)
                    && !"FLOW".equalsIgnoreCase(ctx.serviceType)) {
                rc.setStatus("ERROR");
                rc.setMessage("P0 仅支持 FLOW 类型接口回归（当前: " + ctx.serviceType + "）");
                return finish(rc, start);
            }

            Map<String, String> headers = StrUtil.isBlank(c.getHeadersJson())
                    ? new HashMap<>()
                    : RegressionSecurity.parseStringMap(c.getHeadersJson(), "headersJson");
            Map<String, String> query = StrUtil.isBlank(c.getQueryJson())
                    ? new HashMap<>()
                    : RegressionSecurity.parseStringMap(c.getQueryJson(), "queryJson");

            Map<String, Object> requestMap = new HashMap<>();
            requestMap.put("headers", headers);
            requestMap.put("params", query);
            Object body = c.getBody();
            if (StrUtil.isNotBlank(c.getBody())) {
                try {
                    body = JSONUtil.parse(c.getBody());
                } catch (Exception ignored) {
                    // keep raw string
                }
            }
            requestMap.put("body", body);

            Map<String, Object> args = new HashMap<>();
            args.put("request", requestMap);

            int timeout = RegressionSecurity.clampTimeout(c.getTimeoutMs());
            FlowTrace trace = executeWithTimeout(ctx.dslContent, args, ctx.assetId, ctx.assetName, timeout);

            String status = trace != null ? StrUtil.blankToDefault(trace.getStatus(), "error") : "error";
            if (StrUtil.isNotBlank(c.getExpectTraceStatus())
                    && !c.getExpectTraceStatus().equalsIgnoreCase(status)) {
                rc.setStatus("FAILED");
                rc.setMessage("期望 trace.status=" + c.getExpectTraceStatus() + "，实际=" + status);
                rc.setDetailJson(truncDetail(status, trace));
                return finish(rc, start);
            }

            if (StrUtil.isNotBlank(c.getExpectJsonPath())) {
                RegressionSecurity.validateJsonPath(c.getExpectJsonPath());
                Object actual = readJsonPath(trace, c.getExpectJsonPath());
                String actualStr = actual == null ? "null" : String.valueOf(actual);
                String expect = c.getExpectValue() == null ? "" : c.getExpectValue();
                if (!Objects.equals(actualStr, expect)) {
                    rc.setStatus("FAILED");
                    rc.setMessage("JSONPath 断言失败: " + c.getExpectJsonPath()
                            + " 期望=" + expect + " 实际=" + RegressionSecurity.truncate(actualStr, 120));
                    rc.setDetailJson(truncDetail(status, trace));
                    return finish(rc, start);
                }
            }

            if ("error".equalsIgnoreCase(status) && StrUtil.isBlank(c.getExpectTraceStatus())) {
                rc.setStatus("FAILED");
                rc.setMessage(RegressionSecurity.truncate(
                        StrUtil.blankToDefault(trace != null ? trace.getErrorMsg() : null, "执行失败"),
                        RegressionSecurity.MAX_DETAIL_CHARS));
                rc.setDetailJson(truncDetail(status, trace));
                return finish(rc, start);
            }

            rc.setStatus("PASSED");
            rc.setMessage("OK");
            rc.setDetailJson(truncDetail(status, trace));
            return finish(rc, start);
        } catch (Exception e) {
            rc.setStatus("ERROR");
            rc.setMessage(RegressionSecurity.truncate(
                    StrUtil.blankToDefault(e.getMessage(), e.getClass().getSimpleName()),
                    RegressionSecurity.MAX_DETAIL_CHARS));
            return finish(rc, start);
        }
    }

    private FlowTrace executeWithTimeout(String dsl, Map<String, Object> args,
                                         String sourceRef, String sourceName, int timeoutMs)
            throws Exception {
        java.util.concurrent.ExecutorService es = java.util.concurrent.Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "regression-run");
            t.setDaemon(true);
            return t;
        });
        try {
            return es.submit(() -> {
                Object result = flowEngine.execute(dsl, args, true, "REGRESSION", sourceRef, sourceName);
                if (result instanceof FlowTrace) {
                    return (FlowTrace) result;
                }
                FlowTrace t = new FlowTrace();
                t.setStatus("success");
                t.setGlobalOutputs(result);
                return t;
            }).get(timeoutMs, TimeUnit.MILLISECONDS);
        } catch (java.util.concurrent.TimeoutException te) {
            throw new ValidationException("用例执行超时（" + timeoutMs + "ms）");
        } finally {
            es.shutdownNow();
        }
    }

    private Object readJsonPath(FlowTrace trace, String path) {
        Map<String, Object> wrap = new LinkedHashMap<>();
        wrap.put("status", trace != null ? trace.getStatus() : null);
        wrap.put("errorMsg", trace != null ? trace.getErrorMsg() : null);
        wrap.put("outputs", trace != null ? trace.getGlobalOutputs() : null);
        wrap.put("inputs", trace != null ? trace.getGlobalInputs() : null);
        try {
            return JsonPath.read(wrap, path);
        } catch (PathNotFoundException e) {
            return null;
        } catch (Exception e) {
            throw new ValidationException("JSONPath 读取失败: "
                    + RegressionSecurity.truncate(e.getMessage(), 120));
        }
    }

    private String truncDetail(String status, FlowTrace trace) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("status", status);
        if (trace != null && StrUtil.isNotBlank(trace.getErrorMsg())) {
            m.put("errorMsg", RegressionSecurity.truncate(trace.getErrorMsg(), 200));
        }
        return RegressionSecurity.truncate(JSONUtil.toJsonStr(m), 2000);
    }

    private FlowRegressionRunCaseDO finish(FlowRegressionRunCaseDO rc, long start) {
        rc.setDurationMs(System.currentTimeMillis() - start);
        return rc;
    }

    @Override
    public BatchRunRegressionResultDTO batchRun(BatchRunRegressionRequestDTO request) {
        if (request == null) {
            throw new ValidationException("请求体不能为空");
        }
        String type = RegressionSecurity.normalizeAssetType(request.getAssetType());
        String env = RegressionSecurity.normalizeEnvCode(request.getEnvCode());
        String missingPolicy = StrUtil.blankToDefault(request.getMissingSuitePolicy(), "SKIP")
                .trim().toUpperCase(Locale.ROOT);
        if (!"SKIP".equals(missingPolicy) && !"FAIL".equals(missingPolicy)) {
            throw new ValidationException("missingSuitePolicy 仅支持 SKIP|FAIL");
        }
        List<String> ids = request.getAssetIds() == null ? List.of() : request.getAssetIds().stream()
                .filter(StrUtil::isNotBlank)
                .map(String::trim)
                .distinct()
                .collect(Collectors.toList());
        if (ids.isEmpty()) {
            throw new ValidationException("请至少选择一个资产");
        }
        if (ids.size() > RegressionSecurity.MAX_BATCH_ASSETS) {
            throw new ValidationException("单次批量回归最多 "
                    + RegressionSecurity.MAX_BATCH_ASSETS + " 个资产");
        }
        flowEnvRepository.findByCode(env)
                .filter(e -> Integer.valueOf(1).equals(e.getEnabled()))
                .orElseThrow(() -> new ValidationException("环境不可用: " + env));

        int passed = 0;
        int failed = 0;
        int skipped = 0;
        int error = 0;
        List<BatchRunRegressionItemDTO> items = new ArrayList<>();

        for (String assetId : ids) {
            String assetName = resolveAssetName(type, assetId);
            demoModeGuard.checkModifyOrDelete(assetId, "批量回归");
            List<FlowRegressionSuiteDO> suites =
                    suiteRepository.findByAssetTypeAndAssetIdAndEnabled(type, assetId, 1);
            if (suites.isEmpty()) {
                if ("FAIL".equals(missingPolicy)) {
                    failed++;
                    items.add(BatchRunRegressionItemDTO.builder()
                            .assetId(assetId)
                            .assetName(assetName)
                            .status("FAILED")
                            .message("无启用中的回归套件")
                            .build());
                } else {
                    skipped++;
                    items.add(BatchRunRegressionItemDTO.builder()
                            .assetId(assetId)
                            .assetName(assetName)
                            .status("SKIPPED")
                            .message("无启用中的回归套件，已跳过")
                            .build());
                }
                continue;
            }
            FlowRegressionSuiteDO suite = suites.get(0);
            try {
                RegressionRunDTO run = runSuite(suite.getId(), env);
                String st = run.getStatus();
                if ("PASSED".equals(st)) {
                    passed++;
                } else {
                    failed++;
                }
                items.add(BatchRunRegressionItemDTO.builder()
                        .assetId(assetId)
                        .assetName(assetName)
                        .suiteId(suite.getId())
                        .runId(run.getId())
                        .status(st)
                        .message(run.getSummary())
                        .build());
            } catch (Exception e) {
                error++;
                items.add(BatchRunRegressionItemDTO.builder()
                        .assetId(assetId)
                        .assetName(assetName)
                        .suiteId(suite.getId())
                        .status("ERROR")
                        .message(RegressionSecurity.truncate(
                                StrUtil.blankToDefault(e.getMessage(), e.getClass().getSimpleName()),
                                RegressionSecurity.MAX_DETAIL_CHARS))
                        .build());
            }
        }

        try {
            auditLogService.record("REGRESSION_BATCH_RUN", type, ids.get(0),
                    "{\"env\":\"" + env + "\",\"total\":" + ids.size()
                            + ",\"passed\":" + passed + ",\"failed\":" + failed
                            + ",\"skipped\":" + skipped + ",\"error\":" + error + "}");
        } catch (Exception ignored) {
        }

        return BatchRunRegressionResultDTO.builder()
                .assetType(type)
                .envCode(env)
                .total(ids.size())
                .passed(passed)
                .failed(failed)
                .skipped(skipped)
                .error(error)
                .items(items)
                .build();
    }

    private String resolveAssetName(String type, String assetId) {
        try {
            return switch (type) {
                case "API" -> flowApiRepository.findById(assetId).map(FlowApiDO::getName).orElse(assetId);
                case "TASK" -> flowTaskRepository.findById(assetId).map(FlowTaskDO::getName).orElse(assetId);
                case "SERVICE" -> flowServiceFlowRepository.findById(assetId)
                        .map(FlowServiceFlowDO::getName).orElse(assetId);
                default -> assetId;
            };
        } catch (Exception e) {
            return assetId;
        }
    }

    @Override
    public RegressionRunDTO getRun(String runId) {
        FlowRegressionRunDO run = runRepository.findById(runId)
                .orElseThrow(() -> new ValidationException("运行记录不存在"));
        RegressionRunDTO dto = RegressionRunDTO.fromDO(run);
        dto.setCases(runCaseRepository.findByRunIdOrderByIdAsc(runId).stream()
                .map(RegressionRunCaseDTO::fromDO)
                .collect(Collectors.toList()));
        return dto;
    }

    @Override
    public PageBean<RegressionRunDTO> pageRuns(String assetType, String assetId,
                                               String envCode, int page, int size) {
        Specification<FlowRegressionRunDO> spec = (root, query, cb) -> {
            List<Predicate> ps = new ArrayList<>();
            if (StrUtil.isNotBlank(assetType)) {
                ps.add(cb.equal(root.get("assetType"),
                        RegressionSecurity.normalizeAssetType(assetType)));
            }
            if (StrUtil.isNotBlank(assetId)) {
                ps.add(cb.equal(root.get("assetId"), assetId));
            }
            if (StrUtil.isNotBlank(envCode)) {
                ps.add(cb.equal(root.get("envCode"),
                        RegressionSecurity.normalizeEnvCode(envCode)));
            }
            return cb.and(ps.toArray(new Predicate[0]));
        };
        Page<FlowRegressionRunDO> p = runRepository.findAll(spec,
                PageRequest.of(Math.max(page - 1, 0), Math.max(size, 1),
                        Sort.by(Sort.Direction.DESC, "startedAt")));
        List<RegressionRunDTO> items = p.getContent().stream()
                .map(RegressionRunDTO::fromDO)
                .collect(Collectors.toList());
        return new PageBean<>(items, p.getNumber() + 1, p.getSize(), p.getTotalPages(), p.getTotalElements());
    }

    private FlowRegressionCaseDO applyCaseFields(FlowRegressionCaseDO c, SaveRegressionCaseDTO dto) {
        if (StrUtil.isBlank(dto.getName())) {
            throw new ValidationException("用例名称不能为空");
        }
        c.setName(dto.getName().trim());
        c.setSortOrder(dto.getSortOrder() == null ? 0 : dto.getSortOrder());
        c.setEnabled(dto.getEnabled() == null ? 1 : dto.getEnabled());
        c.setHeadersJson(RegressionSecurity.sanitizeHeadersJson(dto.getHeadersJson()));
        c.setQueryJson(RegressionSecurity.sanitizeQueryJson(dto.getQueryJson()));
        c.setBody(RegressionSecurity.sanitizeBody(dto.getBody()));
        if (StrUtil.isNotBlank(dto.getExpectTraceStatus())) {
            String s = dto.getExpectTraceStatus().trim().toLowerCase(Locale.ROOT);
            if (!"success".equals(s) && !"error".equals(s)) {
                throw new ValidationException("expectTraceStatus 仅支持 success|error");
            }
            c.setExpectTraceStatus(s);
        } else {
            c.setExpectTraceStatus(null);
        }
        RegressionSecurity.validateJsonPath(dto.getExpectJsonPath());
        c.setExpectJsonPath(StrUtil.trim(dto.getExpectJsonPath()));
        if (StrUtil.isNotBlank(dto.getExpectValue()) && dto.getExpectValue().length() > 500) {
            throw new ValidationException("expectValue 过长（≤500）");
        }
        c.setExpectValue(dto.getExpectValue());
        c.setTimeoutMs(RegressionSecurity.clampTimeout(dto.getTimeoutMs()));
        return c;
    }

    private void ensureAssetExists(String type, String assetId) {
        boolean ok = switch (type) {
            case "API" -> flowApiRepository.existsById(assetId);
            case "TASK" -> flowTaskRepository.existsById(assetId);
            case "SERVICE" -> flowServiceFlowRepository.existsById(assetId);
            default -> false;
        };
        if (!ok) {
            throw new ValidationException("资产不存在");
        }
    }

    private AssetExecContext resolveExecContext(String assetType, String assetId) {
        return switch (assetType) {
            case "API" -> {
                FlowApiDO api = flowApiRepository.findById(assetId)
                        .orElseThrow(() -> new ValidationException("API 不存在"));
                yield new AssetExecContext("API", assetId, api.getName(),
                        api.getDslContent(), api.getServiceType());
            }
            case "TASK" -> {
                FlowTaskDO task = flowTaskRepository.findById(assetId)
                        .orElseThrow(() -> new ValidationException("任务不存在"));
                yield new AssetExecContext("TASK", assetId, task.getName(),
                        task.getDslContent(), "FLOW");
            }
            case "SERVICE" -> {
                FlowServiceFlowDO svc = flowServiceFlowRepository.findById(assetId)
                        .orElseThrow(() -> new ValidationException("服务不存在"));
                yield new AssetExecContext("SERVICE", assetId, svc.getName(),
                        svc.getDslContent(), "FLOW");
            }
            default -> throw new ValidationException("不支持的资产类型");
        };
    }

    private record AssetExecContext(String assetType, String assetId, String assetName,
                                    String dslContent, String serviceType) {
    }
}
