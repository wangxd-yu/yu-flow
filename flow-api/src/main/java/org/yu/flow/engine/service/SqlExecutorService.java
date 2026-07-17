package org.yu.flow.engine.service;

import org.yu.flow.auto.dto.SqlAndParams;
import org.springframework.data.domain.Pageable;

/**
 * SQL 执行服务接口
 * 用于 DatabaseNodeExecutor 与底层 FlowApiServiceImpl 对接
 */
public interface SqlExecutorService {

    /**
     * 执行分页查询
     */
    Object executePageQuery(String datasource, SqlAndParams sqlAndParams, Pageable pageable);

    /**
     * 执行列表查询
     */
    Object executeListQuery(String datasource, SqlAndParams sqlAndParams, Pageable pageable);

    /**
     * 执行单对象查询
     */
    Object executeObjectQuery(String datasource, SqlAndParams sqlAndParams);

    /**
     * 执行更新操作
     */
    int executeUpdate(String datasource, SqlAndParams sqlAndParams);

    /**
     * 执行插入操作
     */
    int executeInsert(String datasource, SqlAndParams sqlAndParams);

    /**
     * 批量插入：按单行 INSERT 模板，对 rows 中每一行解析参数后执行 JDBC batchUpdate。
     *
     * @param sqlTemplate 单行模板，如 {@code INSERT INTO t(a,b) VALUES (${a}, ${b})}
     * @param rows        每行对应一组命名参数（Map）
     * @return 受影响行数合计（驱动返回 SUCCESS_NO_INFO/-2 时按 1 计）
     */
    int executeBatchInsert(String datasource, String sqlTemplate, java.util.List<java.util.Map<String, Object>> rows);
}
