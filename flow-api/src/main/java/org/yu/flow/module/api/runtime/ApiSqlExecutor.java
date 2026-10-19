package org.yu.flow.module.api.runtime;

import cn.hutool.core.util.StrUtil;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.yu.flow.config.DemoModeGuard;
import org.yu.flow.dto.PageBean;
import org.yu.flow.dto.SqlAndParams;
import org.yu.flow.engine.service.SqlExecutorService;
import org.yu.flow.engine.sql.DynamicSqlParser;
import org.yu.flow.module.datasource.service.DynamicDataSourceService;
import org.yu.flow.module.datasource.wall.DataSourceWallGuard;
import org.yu.flow.util.CamelCaseColumnMapRowMapper;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * 接口 DB 模式与流程数据库节点共用的 SQL 执行器。每条 SQL 先过数据源 SQL 防火墙，写操作再过演示模式拦截。
 * 查询结果列名转驼峰，数字转字符串（避免前端丢精度）。
 */
@Service
public class ApiSqlExecutor implements SqlExecutorService {

    private static final int BATCH_CHUNK_SIZE = 500;

    private final DynamicDataSourceService dynamicDataSourceService;
    private final DataSourceWallGuard dataSourceWallGuard;
    private final DemoModeGuard demoModeGuard;

    public ApiSqlExecutor(@Lazy DynamicDataSourceService dynamicDataSourceService,
                          DataSourceWallGuard dataSourceWallGuard,
                          DemoModeGuard demoModeGuard) {
        this.dynamicDataSourceService = dynamicDataSourceService;
        this.dataSourceWallGuard = dataSourceWallGuard;
        this.demoModeGuard = demoModeGuard;
    }

    @Override
    public Object executePageQuery(String datasource, SqlAndParams sqlAndParams, Pageable pageable) {
        dataSourceWallGuard.assertSqlAllowed(datasource, sqlAndParams.getSql());
        Object[] args = sqlAndParams.getParams().toArray();
        String countSql = ApiSqlText.countSql(sqlAndParams.getSql());
        Integer totalSize = dynamicDataSourceService.execute(datasource,
                jt -> jt.queryForObject(countSql, args, Integer.class));
        if (totalSize == null || totalSize == 0) {
            return new PageBean<>(Collections.emptyList(), pageable.getPageNumber(), pageable.getPageSize(), 0, 0L);
        }

        int totalPage = (int) Math.ceil((double) totalSize / pageable.getPageSize());
        String pageSql = ApiSqlText.pageSql(sqlAndParams.getSql(), pageable);
        List<Map<String, Object>> content = dynamicDataSourceService.executeInTransaction(datasource,
                jt -> jt.query(pageSql, args, new CamelCaseColumnMapRowMapper(true)));
        return new PageBean<>(content, pageable.getPageNumber(), pageable.getPageSize(), totalPage, totalSize.longValue());
    }

    @Override
    public Object executeListQuery(String datasource, SqlAndParams sqlAndParams, Pageable pageable) {
        dataSourceWallGuard.assertSqlAllowed(datasource, sqlAndParams.getSql());
        String sql = ApiSqlText.sortedSql(sqlAndParams.getSql(), pageable);
        return dynamicDataSourceService.execute(datasource, jt -> jt.query(
                sql, sqlAndParams.getParams().toArray(), new CamelCaseColumnMapRowMapper(true)));
    }

    @Override
    public Map<String, Object> executeObjectQuery(String datasource, SqlAndParams sqlAndParams) {
        dataSourceWallGuard.assertSqlAllowed(datasource, sqlAndParams.getSql());
        List<Map<String, Object>> result = dynamicDataSourceService.execute(datasource, jt -> jt.query(
                sqlAndParams.getSql(), sqlAndParams.getParams().toArray(), new CamelCaseColumnMapRowMapper(true)));
        return result.isEmpty() ? null : result.get(0);
    }

    @Override
    public int executeUpdate(String datasource, SqlAndParams sqlAndParams) {
        demoModeGuard.checkSqlWrite("UPDATE/DELETE");
        return update(datasource, sqlAndParams);
    }

    @Override
    public int executeInsert(String datasource, SqlAndParams sqlAndParams) {
        demoModeGuard.checkSqlWrite("INSERT");
        return update(datasource, sqlAndParams);
    }

    private int update(String datasource, SqlAndParams sqlAndParams) {
        dataSourceWallGuard.assertSqlAllowed(datasource, sqlAndParams.getSql());
        return dynamicDataSourceService.executeInTransaction(datasource,
                jt -> jt.update(sqlAndParams.getSql(), sqlAndParams.getParams().toArray()));
    }

    @Override
    public int executeBatchInsert(String datasource, String sqlTemplate, List<Map<String, Object>> rows) {
        demoModeGuard.checkSqlWrite("INSERT");
        if (rows == null || rows.isEmpty()) {
            return 0;
        }
        if (StrUtil.isBlank(sqlTemplate)) {
            throw new IllegalArgumentException("batch insert sqlTemplate 不能为空");
        }

        return dynamicDataSourceService.executeInTransaction(datasource, jt -> {
            int total = 0;
            String preparedSql = null;
            List<Object[]> batchArgs = new ArrayList<>(Math.min(rows.size(), BATCH_CHUNK_SIZE));

            for (int i = 0; i < rows.size(); i++) {
                Map<String, Object> row = rows.get(i) != null ? rows.get(i) : Collections.emptyMap();
                SqlAndParams sp = DynamicSqlParser.parseDynamicSqlToPrepared(sqlTemplate, row);
                dataSourceWallGuard.assertSqlAllowed(datasource, sp.getSql());
                if (preparedSql == null) {
                    preparedSql = sp.getSql();
                } else if (!preparedSql.equals(sp.getSql())) {
                    // 参数缺失会导致动态 SQL 变形；先刷掉已攒批次再换 SQL
                    if (!batchArgs.isEmpty()) {
                        total += sumBatchUpdate(jt.batchUpdate(preparedSql, batchArgs));
                        batchArgs = new ArrayList<>(BATCH_CHUNK_SIZE);
                    }
                    preparedSql = sp.getSql();
                }
                batchArgs.add(sp.getParams().toArray());

                if (batchArgs.size() >= BATCH_CHUNK_SIZE || i == rows.size() - 1) {
                    total += sumBatchUpdate(jt.batchUpdate(preparedSql, batchArgs));
                    batchArgs = new ArrayList<>(BATCH_CHUNK_SIZE);
                }
            }
            return total;
        });
    }

    private static int sumBatchUpdate(int[] counts) {
        if (counts == null || counts.length == 0) {
            return 0;
        }
        int sum = 0;
        for (int c : counts) {
            // SUCCESS_NO_INFO = -2：多数驱动表示执行成功但行数未知
            sum += (c >= 0) ? c : 1;
        }
        return sum;
    }
}
