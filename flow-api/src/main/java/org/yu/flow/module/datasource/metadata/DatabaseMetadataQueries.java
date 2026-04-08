package org.yu.flow.module.datasource.metadata;

public interface DatabaseMetadataQueries {
    String getTablesQuery();
    String getColumnsQuery(String schema, String tableName);
}
