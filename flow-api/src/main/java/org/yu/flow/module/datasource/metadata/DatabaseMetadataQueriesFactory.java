package org.yu.flow.module.datasource.metadata;

import java.util.HashMap;
import java.util.Map;

public class DatabaseMetadataQueriesFactory {
    private static final Map<String, DatabaseMetadataQueries> QUERY_MAP = new HashMap<>();

    static {
        QUERY_MAP.put("mysql", new MySQLMetadataQueries());
        QUERY_MAP.put("highgo", new HighgoMetadataQueries());
    }

    public static DatabaseMetadataQueries getQueries(String dbType) {
        return QUERY_MAP.getOrDefault(dbType.toLowerCase(), new DefaultMetadataQueries());
    }
}
