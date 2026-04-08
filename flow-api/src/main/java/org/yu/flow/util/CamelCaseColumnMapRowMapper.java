package org.yu.flow.util;

import cn.hutool.core.util.StrUtil;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.JdbcUtils;

import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 将 ResultSet 中的列名自动转换为驼峰形式的 RowMapper。
 * 包含列名缓存优化，避免在大数据量查询时重复使用正则进行名字转换。
 */
public class CamelCaseColumnMapRowMapper implements RowMapper<Map<String, Object>> {

    private String[] camelCaseKeys;
    private final boolean forceToString;

    public CamelCaseColumnMapRowMapper() {
        this.forceToString = false;
    }

    public CamelCaseColumnMapRowMapper(boolean forceToString) {
        this.forceToString = forceToString;
    }

    @Override
    public Map<String, Object> mapRow(ResultSet rs, int rowNum) throws SQLException {
        ResultSetMetaData rsmd = rs.getMetaData();
        int columnCount = rsmd.getColumnCount();

        // 延迟初始化并缓存驼峰列名，避免每行数据都重复执行 StrUtil.toCamelCase (其内部可能涉正则或复杂循环)
        if (this.camelCaseKeys == null) {
            this.camelCaseKeys = new String[columnCount + 1];
            for (int i = 1; i <= columnCount; i++) {
                String column = JdbcUtils.lookupColumnName(rsmd, i);
                this.camelCaseKeys[i] = StrUtil.toCamelCase(column);
            }
        }

        Map<String, Object> mapOfColValues = new LinkedHashMap<>(columnCount);
        for (int i = 1; i <= columnCount; i++) {
            Object obj = JdbcUtils.getResultSetValue(rs, i);
            if (forceToString) {
                mapOfColValues.put(this.camelCaseKeys[i], obj == null ? "" : obj.toString());
            } else {
                mapOfColValues.put(this.camelCaseKeys[i], obj);
            }
        }
        return mapOfColValues;
    }
}
