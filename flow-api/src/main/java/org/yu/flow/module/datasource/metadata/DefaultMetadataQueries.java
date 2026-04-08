package org.yu.flow.module.datasource.metadata;

class DefaultMetadataQueries implements DatabaseMetadataQueries {
    @Override
    public String getTablesQuery() {
        throw new UnsupportedOperationException("Unsupported database type");
    }

    @Override
    public String getColumnsQuery(String schema, String tableName) {
        throw new UnsupportedOperationException("Unsupported database type");
    }
}
