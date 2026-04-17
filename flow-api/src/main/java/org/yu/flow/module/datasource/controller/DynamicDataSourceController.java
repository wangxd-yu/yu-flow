package org.yu.flow.module.datasource.controller;

import org.yu.flow.annotation.YuFlowApi;

import org.yu.flow.auto.dto.PageBean;
import org.yu.flow.dto.R;
import org.yu.flow.module.datasource.domain.DataSourceDO;
import org.yu.flow.module.datasource.dto.TestConnectionDTO;
import org.yu.flow.module.datasource.service.DynamicDataSourceService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import javax.sql.DataSource;
import java.util.List;
import java.util.Map;

/**
 * 动态数据源管理控制器
 * 提供数据源的增删改查、启用禁用、测试连接等功能
 */
@YuFlowApi
@RestController
@RequestMapping("/flow-api/dataSource")
public class DynamicDataSourceController {

    @Resource
    private DynamicDataSourceService dynamicDataSourceService;

    /**
     * 获取所有数据源
     * @return 包含所有数据源的Map，key为数据源名称，value为数据源对象
     */
    @GetMapping
    public R<Map<String, DataSource>> getAllDataSources() {
        return R.ok(dynamicDataSourceService.getAllDataSources(), "获取所有数据源成功");
    }

    /**
     * 根据 ID 获取单个数据源配置（密码已脱敏）
     *
     * @param id 数据源 ID
     * @return 脱敏后的数据源配置
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

    /**
     * 分页查询数据源列表
     * 支持根据 name 模糊搜索, dbType 精确搜索
     *
     * @param name        模型名称（模糊，可选）
     * @param dbType   物理表名（模糊，可选）
     * @param page        页码
     * @param size        每页条数
     */
    @GetMapping("/page")
    public R<PageBean<DataSourceDO>> getPage(
            @RequestParam(required = false) String name,
            @RequestParam(required = false) String dbType,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        return R.ok(dynamicDataSourceService.findPage(name, dbType, page, size));
    }


    /**
     * 添加新数据源
     * @param config 数据源配置信息
     * @return 操作结果
     */
    @PostMapping
    public R<Boolean> addDataSource(@RequestBody DataSourceDO config) {
        boolean result = dynamicDataSourceService.addDataSource(config);
        return result ? R.ok(true, "添加数据源成功") : R.fail("添加数据源失败");
    }

    /**
     * 更新数据源配置
     * @param id 数据源名称
     * @param config 新的数据源配置
     * @return 操作结果
     */
    @PutMapping("/{id}")
    public R<Boolean> updateDataSource(
            @PathVariable String id,
            @RequestBody DataSourceDO config) {

        try {
            if (!id.equals(config.getId())) {
                return R.fail("路径中的名称与请求体中的名称不一致");
            }

            boolean result = dynamicDataSourceService.updateDataSource(config);
            return result ? R.ok(true, "更新数据源成功") : R.fail("更新数据源失败");
        } catch (Exception e) {
            return R.fail("更新数据源时出错：" + e.getMessage());
        }
    }

    /**
     * 删除数据源
     * @param id 要删除的数据源名称
     * @return 操作结果
     */
    @DeleteMapping("/{id}")
    public R<Boolean> removeDataSource(@PathVariable String id) {
        try {
            boolean result = dynamicDataSourceService.removeDataSource(id);
            return result ? R.ok(true, "删除数据源成功") : R.fail("删除数据源失败");
        } catch (Exception e) {
            return R.fail("删除数据源时出错：" + e.getMessage());
        }
    }

    /**
     * 启用数据源
     * @param id 要启用的数据源名称
     * @return 操作结果
     */
    @PostMapping("/{id}/enable")
    public R<Boolean> enableDataSource(@PathVariable String id) {
        try {
            boolean result = dynamicDataSourceService.enableDataSource(id);
            return result ? R.ok(true, "启用数据源成功") : R.fail("启用数据源失败");
        } catch (Exception e) {
            return R.fail("启用数据源时出错：" + e.getMessage());
        }
    }

    /**
     * 禁用数据源
     * @param id 要禁用的数据源名称
     * @return 操作结果
     */
    @PostMapping("/{id}/disable")
    public R<Boolean> disableDataSource(@PathVariable String id) {
        try {
            boolean result = dynamicDataSourceService.disableDataSource(id);
            return result ? R.ok(true, "禁用数据源成功") : R.fail("禁用数据源失败");
        } catch (Exception e) {
            return R.fail("禁用数据源时出错：" + e.getMessage());
        }
    }

    // =========================================================================
    // 任务三：独立连通性测试接口（不依赖已保存的数据源，直接用 DTO 参数测试）
    // 注意：此接口必须放在 /{id}/test-connection 之前，防止被路径变量吞掉
    // =========================================================================
    /**
     * POST /flow-api/dataSource/test-connection
     * 使用表单中输入的 url/username/password/driver 临时建立 JDBC 连接进行测试。
     * 不写数据库，连接成功后立即关闭。超时时间 5 秒。
     *
     * @param dto 连接参数
     * @return {success: true/false, message: "错误信息"}
     */
    @PostMapping("/test-connection")
    public R<Map<String, Object>> testConnectionByParam(@RequestBody @Validated TestConnectionDTO dto) {
        Map<String, Object> result = dynamicDataSourceService.testConnectionByDTO(dto);
        boolean success = Boolean.TRUE.equals(result.get("success"));
        return success
                ? R.ok(result, "连接测试成功")
                : R.fail("连接测试失败：" + result.get("message"));
    }

    /**
     * 测试已保存数据源的连接（使用数据库中存储的加密密码）
     *
     * @param id 要测试的数据源 ID
     * @return 连接测试结果
     */
    @GetMapping("/{id}/test-connection")
    public R<Boolean> testConnection(@PathVariable String id) {
        try {
            boolean isConnected = dynamicDataSourceService.testConnection(id);
            return isConnected ? R.ok(true, "数据源连接测试成功") : R.fail("数据源连接测试失败");
        } catch (Exception e) {
            return R.fail("测试数据源连接时出错：" + e.getMessage());
        }
    }

    /**
     * 执行查询SQL
     * @param code 数据源编码
     * @param sql 要执行的SQL语句
     * @return 查询结果
     */
    @PostMapping("/{code}/execute/query")
    public R<Object> executeQuery(
            @PathVariable String code,
            @RequestBody String sql) {

        try {
            Object result = dynamicDataSourceService.execute(code, jdbcTemplate -> jdbcTemplate.queryForList(sql));
            return R.ok(result, "SQL查询执行成功");
        } catch (Exception e) {
            return R.fail("执行SQL查询时出错：" + e.getMessage());
        }
    }

    /**
     * 执行更新SQL
     * @param code 数据源编码
     * @param sql 要执行的SQL语句
     * @return 影响的行数
     */
    @PostMapping("/{code}/execute/update")
    public R<Integer> executeUpdate(
            @PathVariable String code,
            @RequestBody String sql) {

        try {
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
}
