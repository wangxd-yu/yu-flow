package org.yu.flow.module.datasource.metadata;

class MySQLMetadataQueries implements DatabaseMetadataQueries {
    @Override
    public String getTablesQuery() {
        return null;
    }

    @Override
    public String getColumnsQuery(String schema, String tableName) {
        return null;
    }
}
