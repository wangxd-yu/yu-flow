package org.yu.flow.auto.dto;

import java.util.List;

/**
 * 用于存储 SQL 和参数的对象
 */
public class SqlAndParams {
    private final String sql;
    private final List<Object> params;

    public SqlAndParams(String sql, List<Object> params) {
        this.sql = sql;
        this.params = params;
    }

    public String getSql() {
        return sql;
    }

    public List<Object> getParams() {
        return params;
    }
}
