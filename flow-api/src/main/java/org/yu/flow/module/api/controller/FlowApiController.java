package org.yu.flow.module.api.controller;

import org.yu.flow.annotation.YuFlowApi;

import lombok.extern.slf4j.Slf4j;
import org.yu.flow.auto.dto.BatchMoveDTO;
import org.yu.flow.auto.dto.PageBean;
import org.yu.flow.module.api.cache.ApiCacheContentDTO;
import org.yu.flow.module.api.cache.ApiCacheContentDTO;
import org.yu.flow.module.api.cache.ApiCacheEntryDTO;
import org.yu.flow.module.api.cache.ApiResponseCacheService;
import org.yu.flow.module.api.domain.FlowApiDO;
import org.yu.flow.module.api.dto.FlowApiDTO;
import org.yu.flow.module.api.query.FlowApiQueryDTO;
import org.yu.flow.engine.evaluator.FlowEngine;
import org.yu.flow.engine.model.ExecutionLog;
import org.yu.flow.engine.model.FlowTrace;
import org.yu.flow.module.api.dto.FlowDebugRequestDTO;
import org.yu.flow.module.api.dto.FlowDbDebugRequestDTO;
import org.yu.flow.dto.R;
import org.yu.flow.auto.service.FlowApiExecutionService;

import org.springframework.web.bind.annotation.*;
import org.yu.flow.module.api.service.FlowApiCrudService;
import org.yu.flow.module.assetversion.dto.FlowAssetVersionDTO;

import jakarta.annotation.Resource;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * FlowApi CRUD 管理控制器
 *
 * @author yu-flow
 * @date 2025-03-05 23:32
 */
@Slf4j
@YuFlowApi
@RestController
@RequestMapping(value = {"flow-api/api"})
public class FlowApiController {

    @Resource
    private FlowApiCrudService flowApiCrudService;

    @Resource
    private ApiResponseCacheService apiResponseCacheService;

    @Resource
    private FlowEngine flowEngine;

    @Resource
    private FlowApiExecutionService flowApiExecutionService;

    @PostMapping("/debug/run")
    public R<FlowTrace> debugRun(@RequestBody FlowDebugRequestDTO requestDTO) {
        try {
            Map<String, Object> args = new HashMap<>();
            Map<String, Object> requestMap = new HashMap<>();
            requestMap.put("headers", requestDTO.getHeaders() != null ? requestDTO.getHeaders() : new HashMap<>());
            requestMap.put("params", requestDTO.getQueryParams() != null ? requestDTO.getQueryParams() : new HashMap<>());

            // Try to parse body as JSON if possible, otherwise keep as string
            Object parsedBody = requestDTO.getBody();
            if (requestDTO.getBody() != null && !requestDTO.getBody().trim().isEmpty()) {
                try {
                    parsedBody = cn.hutool.json.JSONUtil.parse(requestDTO.getBody());
                } catch (Exception e) {
                    // Ignore parse error, treat as raw string
                }
            }
            requestMap.put("body", parsedBody);
            args.put("request", requestMap);

            // Execute flow engine in trace mode（调试运行标记 DEBUG，并带上来源接口信息）
            FlowTrace trace = flowEngine.execute(requestDTO.getDslContent(), args, true, "DEBUG",
                    requestDTO.getSourceRef(), requestDTO.getSourceName());

            return R.ok(trace != null ? trace : new FlowTrace());
        } catch (Exception e) {
            log.error("Debug run failed", e);
            ExecutionLog errorLog = new ExecutionLog()
                .setId("err_global")
                .setNodeId("__global__")
                .setNodeName("Global Error")
                .setNodeType("error")
                .setStatus("error")
                .setStartTime(new SimpleDateFormat("HH:mm:ss.SSS").format(new Date()))
                .setError(e.getMessage());
            FlowTrace errorTrace = new FlowTrace();
            errorTrace.setStatus("error");
            errorTrace.setErrorMsg(e.getMessage());
            errorTrace.setStepLogs(Collections.singletonList(errorLog));
            return R.ok(errorTrace);
        }
    }

    /**
     * 数据库模式调试运行：传入 SQL / 数据源 / 响应类型与请求参数，直接执行并返回 FlowTrace。
     */
    @PostMapping("/debug/db/run")
    public R<FlowTrace> debugDbRun(@RequestBody FlowDbDebugRequestDTO requestDTO) {
        return R.ok(flowApiExecutionService.debugRunDb(requestDTO));
    }

    @PostMapping
    public R<FlowApiDO> create(@RequestBody FlowApiDO flowApiDO) {
        FlowApiDO savedConfig = flowApiCrudService.save(flowApiDO);
        return R.ok(savedConfig);
    }

    @PostMapping("/batch/create")
    public R<List<FlowApiDO>> batchCreate(@RequestBody List<FlowApiDO> flowApiDOList) {
        List<FlowApiDO> savedList = flowApiCrudService.batchSave(flowApiDOList);
        return R.ok(savedList);
    }

    @PutMapping("/batch/delete")
    public R<Void> batchDelete(@RequestBody List<String> ids) {
        flowApiCrudService.batchDelete(ids);
        return R.ok();
    }

    @PutMapping("/batch/moveToDir")
    public R<Void> batchMove(@RequestBody BatchMoveDTO batchMoveDTO) {
        flowApiCrudService.batchMove(batchMoveDTO.getIds(), batchMoveDTO.getTargetDirectoryId());
        return R.ok();
    }

    /**
     * 校验 API 是否已被占用 (精确匹配 URL 和 Method)
     *
     * @param url    API 路径
     * @param method 请求方法
     * @return true-已占用/存在冲突, false-可用
     */
    @GetMapping("/check-exact")
    public R<Boolean> checkExact(@RequestParam String url, @RequestParam String method) {
        return R.ok(flowApiCrudService.existsByUrlAndMethod(url, method));
    }

    @PutMapping("/{id}")
    public R<FlowApiDO> update(@PathVariable String id, @RequestBody FlowApiDO flowApiDO) {
        flowApiDO.setId(id);
        return R.ok(flowApiCrudService.update(flowApiDO));
    }

    @PutMapping("/{id}/log-enabled")
    public R<FlowApiDO> updateLogEnabled(@PathVariable String id, @RequestParam boolean enabled) {
        return R.ok(flowApiCrudService.updateLogEnabled(id, enabled));
    }

    /**
     * 更新查询响应缓存配置（即时生效）。
     * <p>Body 可为完整 cacheConfig JSON 字符串，或 {"cacheConfig":"{...}"} / 直接对象。</p>
     */
    @PutMapping("/{id}/cache-config")
    public R<FlowApiDO> updateCacheConfig(@PathVariable String id, @RequestBody(required = false) Object body) {
        String cacheConfigJson = resolveCacheConfigBody(body);
        return R.ok(flowApiCrudService.updateCacheConfig(id, cacheConfigJson));
    }

    /**
     * 查询该接口当前生效的响应缓存条目。
     */
    @GetMapping("/{id}/cache/entries")
    public R<List<ApiCacheEntryDTO>> listCacheEntries(@PathVariable String id) {
        return R.ok(apiResponseCacheService.listEntries(id));
    }

    /**
     * 按需查看单条响应缓存内容（不含列表批量返回 body）。
     */
    @GetMapping("/{id}/cache/entries/content")
    public R<ApiCacheContentDTO> getCacheEntryContent(@PathVariable String id, @RequestParam String key) {
        ApiCacheContentDTO content = apiResponseCacheService.getEntryContent(id, key);
        if (content == null) {
            return R.fail("缓存不存在或已过期");
        }
        return R.ok(content);
    }

    /**
     * 清除该接口全部响应缓存。
     */
    @DeleteMapping("/{id}/cache")
    public R<Long> clearCache(@PathVariable String id) {
        return R.ok(apiResponseCacheService.evictAll(id));
    }

    /**
     * 清除单条响应缓存。
     */
    @DeleteMapping("/{id}/cache/entries")
    public R<Boolean> clearCacheEntry(@PathVariable String id, @RequestParam String key) {
        return R.ok(apiResponseCacheService.evictOne(id, key));
    }

    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable String id) {
        flowApiCrudService.delete(id);
        return R.ok();
    }

    @SuppressWarnings("unchecked")
    private String resolveCacheConfigBody(Object body) {
        if (body == null) {
            return null;
        }
        if (body instanceof String) {
            return (String) body;
        }
        if (body instanceof Map) {
            Map<String, Object> map = (Map<String, Object>) body;
            Object nested = map.get("cacheConfig");
            if (nested instanceof String) {
                return (String) nested;
            }
            if (nested != null) {
                return cn.hutool.json.JSONUtil.toJsonStr(nested);
            }
            // 直接传配置对象
            if (map.containsKey("enabled") || map.containsKey("keyParams") || map.containsKey("ttlSeconds")) {
                return cn.hutool.json.JSONUtil.toJsonStr(map);
            }
            return null;
        }
        return cn.hutool.json.JSONUtil.toJsonStr(body);
    }

    @GetMapping("/{id}")
    public R<FlowApiDTO> getById(@PathVariable String id) {
        FlowApiDO configDO = flowApiCrudService.findById(id);
        return R.ok(configDO != null ? FlowApiDTO.fromDO(configDO) : null);
    }

    @GetMapping
    public R<List<FlowApiDTO>> getAll() {
        List<FlowApiDTO> configs = flowApiCrudService.findAll();
        return R.ok(configs);
    }

    @GetMapping("/page")
    public R<PageBean<FlowApiDTO>> getPage(FlowApiQueryDTO queryDTO) {
        return R.ok(flowApiCrudService.findPage(queryDTO));
    }

    @GetMapping("/publish-status/{status}")
    public R<List<FlowApiDTO>> getByPublishStatus(@PathVariable Integer status) {
        List<FlowApiDTO> configs = flowApiCrudService.findByPublishStatus(status);
        return R.ok(configs);
    }

    /**
     * 发布 API（冻结草稿为线上快照）
     */
    @PutMapping("/{id}/publish")
    public R<FlowApiDO> publish(@PathVariable String id) {
        return R.ok(flowApiCrudService.publish(id));
    }

    /**
     * 下线 API（清除快照，停止线上服务）
     */
    @PutMapping("/{id}/unpublish")
    public R<FlowApiDO> unpublish(@PathVariable String id) {
        return R.ok(flowApiCrudService.unpublish(id));
    }

    /**
     * 回滚草稿到发布版本
     */
    @PutMapping("/{id}/rollback")
    public R<FlowApiDO> rollback(@PathVariable String id) {
        return R.ok(flowApiCrudService.rollbackToPublished(id));
    }

    /**
     * 重新发布（将最新草稿冻结为快照并上线）
     */
    @PutMapping("/{id}/republish")
    public R<FlowApiDO> republish(@PathVariable String id) {
        return R.ok(flowApiCrudService.republish(id));
    }

    /** 历史版本列表 */
    @GetMapping("/{id}/versions")
    public R<List<FlowAssetVersionDTO>> listVersions(@PathVariable String id) {
        return R.ok(flowApiCrudService.listVersions(id));
    }

    /** 回退至指定历史版本（同步覆盖草稿与线上快照） */
    @PostMapping("/{id}/versions/{versionId}/restore")
    public R<FlowApiDO> restoreVersion(@PathVariable String id, @PathVariable String versionId) {
        return R.ok(flowApiCrudService.restoreVersion(id, versionId));
    }

    @GetMapping("/name/{name}")
    public R<FlowApiDTO> getByName(@PathVariable String name) {
        FlowApiDTO config = flowApiCrudService.findByName(name);
        return R.ok(config);
    }
}
