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
}
