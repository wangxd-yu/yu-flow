package org.yu.flow.module.datasource.controller;
import org.yu.flow.config.DemoModeGuard;

import org.yu.flow.annotation.YuFlowApi;

import org.yu.flow.auto.dto.PageBean;
import org.yu.flow.dto.R;
import org.yu.flow.module.datasource.domain.DataSourceDO;
import org.yu.flow.module.datasource.dto.TestConnectionDTO;
import org.yu.flow.module.datasource.service.DynamicDataSourceService;
import org.yu.flow.module.datasource.wall.DataSourceWallGuard;
import org.yu.flow.module.rbac.support.RequirePerm;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import jakarta.annotation.Resource;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 动态数据源管理控制器
 * 提供数据源的增删改查、启用禁用、测试连接等功能
 */
@YuFlowApi
@RestController
@RequestMapping("/flow-api/dataSource")
@RequirePerm({"flow:ds:view", "flow:ds:write"})
public class DynamicDataSourceController {

    @Resource
    private DynamicDataSourceService dynamicDataSourceService;

    @Resource
    private DataSourceWallGuard dataSourceWallGuard;

    @Resource
    private DemoModeGuard demoModeGuard;

    /**
     * 获取数据源摘要列表（不含连接池内部对象与明文密码）。
     */
    @GetMapping
    public R<List<Map<String, Object>>> getAllDataSources() {
        PageBean<DataSourceDO> page = dynamicDataSourceService.findPage(null, null, 0, 1000);
        List<Map<String, Object>> list = page.getItems() == null
                ? List.of()
                : page.getItems().stream().map(this::toSafeSummary).collect(Collectors.toList());
        return R.ok(list, "获取所有数据源成功");
    }

    /**
     * 下拉用：已启用数据源（id / name / code）。系统默认源置顶。
     * <p>必须写在 {@code /{id}} 之前，否则 {@code /list} 会被当成主键查询。
     */
    @GetMapping("/list")
    public R<List<Map<String, Object>>> listEnabledDataSources() {
        return R.ok(dynamicDataSourceService.listEnabledSummaries());
    }

    /**
     * 根据 ID 获取单个数据源配置（密码已脱敏）
     */
    @GetMapping("/{id}")
    public R<DataSourceDO> getDataSource(@PathVariable String id) {
        try {
            DataSourceDO vo = dynamicDataSourceService.getById(id);
            return R.ok(vo, "获取数据源成功");
        } catch (Exception e) {
            return R.fail("未找到指定 ID 的数据源：" + e.getMessage());
        }
    }

    @GetMapping("/page")
    public R<PageBean<DataSourceDO>> getPage(
            @RequestParam(required = false) String name,
            @RequestParam(required = false) String dbType,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return R.ok(dynamicDataSourceService.findPage(name, dbType, page, size));
    }

    @PostMapping
    @RequirePerm("flow:ds:write")
    public R<Boolean> addDataSource(@RequestBody DataSourceDO config) {
        boolean result = dynamicDataSourceService.addDataSource(config);
        return result ? R.ok(true, "添加数据源成功") : R.fail("添加数据源失败");
    }

    @PutMapping("/{id}")
    @RequirePerm("flow:ds:write")
    public R<Boolean> updateDataSource(
            @PathVariable String id,
            @RequestBody DataSourceDO config) {

        try {
            config.setId(id);
            boolean result = dynamicDataSourceService.updateDataSource(config);
            return result ? R.ok(true, "更新数据源成功") : R.fail("更新数据源失败");
        } catch (Exception e) {
            return R.fail("更新数据源时出错：" + e.getMessage());
        }
    }

    @DeleteMapping("/{id}")
    @RequirePerm("flow:ds:write")
    public R<Boolean> removeDataSource(@PathVariable String id) {
        try {
            boolean result = dynamicDataSourceService.removeDataSource(id);
            return result ? R.ok(true, "删除数据源成功") : R.fail("删除数据源失败");
        } catch (Exception e) {
            return R.fail("删除数据源时出错：" + e.getMessage());
        }
    }

    @PostMapping("/{id}/enable")
    @RequirePerm("flow:ds:write")
    public R<Boolean> enableDataSource(@PathVariable String id) {
        try {
            boolean result = dynamicDataSourceService.enableDataSource(id);
            return result ? R.ok(true, "启用数据源成功") : R.fail("启用数据源失败");
        } catch (Exception e) {
            return R.fail("启用数据源时出错：" + e.getMessage());
        }
    }

    @PostMapping("/{id}/disable")
    @RequirePerm("flow:ds:write")
    public R<Boolean> disableDataSource(@PathVariable String id) {
        try {
            boolean result = dynamicDataSourceService.disableDataSource(id);
            return result ? R.ok(true, "禁用数据源成功") : R.fail("禁用数据源失败");
        } catch (Exception e) {
            return R.fail("禁用数据源时出错：" + e.getMessage());
        }
    }

    @PostMapping("/test-connection")
    @RequirePerm("flow:ds:write")
    public R<Map<String, Object>> testConnectionByParam(@RequestBody @Validated TestConnectionDTO dto) {
        Map<String, Object> result = dynamicDataSourceService.testConnectionByDTO(dto);
        boolean success = Boolean.TRUE.equals(result.get("success"));
        return success
                ? R.ok(result, "连接测试成功")
                : R.fail("连接测试失败：" + result.get("message"));
    }

    @GetMapping("/{id}/test-connection")
    @RequirePerm({"flow:ds:view", "flow:ds:write"})
    public R<Boolean> testConnection(@PathVariable String id) {
        try {
            boolean isConnected = dynamicDataSourceService.testConnection(id);
            return isConnected ? R.ok(true, "数据源连接测试成功") : R.fail("数据源连接测试失败");
        } catch (Exception e) {
            return R.fail("测试数据源连接时出错：" + e.getMessage());
        }
    }

    @PostMapping("/{code}/execute/query")
    @RequirePerm("flow:ds:write")
    public R<Object> executeQuery(
            @PathVariable String code,
            @RequestBody String sql) {

        try {
            dataSourceWallGuard.assertSqlAllowed(code, sql);
            Object result = dynamicDataSourceService.execute(code, jdbcTemplate -> jdbcTemplate.queryForList(sql));
            return R.ok(result, "SQL查询执行成功");
        } catch (Exception e) {
            return R.fail("执行SQL查询时出错：" + e.getMessage());
        }
    }

    @PostMapping("/{code}/execute/update")
    @RequirePerm("flow:ds:write")
    public R<Integer> executeUpdate(
            @PathVariable String code,
            @RequestBody String sql) {

        demoModeGuard.checkSqlWrite("SQL 直接写入");

        try {
            dataSourceWallGuard.assertSqlAllowed(code, sql);
            Integer result = dynamicDataSourceService.execute(code, jdbcTemplate -> jdbcTemplate.update(sql));
            return R.ok(result, "SQL更新执行成功");
        } catch (Exception e) {
            return R.fail("执行SQL更新时出错：" + e.getMessage());
        }
    }

    @GetMapping("/tableList/{code}")
    public R<List<Map<String, Object>>> getTableList(@PathVariable String code) {
        List<Map<String, Object>> tableList = dynamicDataSourceService.getTableList(code);
        return R.ok(tableList);
    }

    private Map<String, Object> toSafeSummary(DataSourceDO d) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", d.getId());
        m.put("name", d.getName());
        m.put("code", d.getCode());
        m.put("dbType", d.getDbType());
        m.put("status", d.getStatus());
        m.put("healthStatus", d.getHealthStatus());
        m.put("isSystem", d.getIsSystem());
        return m;
    }
}
