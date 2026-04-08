package org.yu.flow.module.datasource.metadata;

class HighgoMetadataQueries implements DatabaseMetadataQueries {
    @Override
    public String getTablesQuery() {
        return "SELECT " +
                "    c.relname AS table_name, " +
                "    CASE c.relkind " +
                "        WHEN 'r' THEN '普通表' " +
                "        WHEN 'v' THEN '视图' " +
                "        WHEN 'm' THEN '物化视图' " +
                "        WHEN 'f' THEN '外部表' " +
                "        WHEN 'p' THEN '分区表' " +
                "    END AS table_type, " +
                "    pg_size_pretty(pg_total_relation_size(c.oid)) AS total_size, " +
                "    pg_size_pretty(pg_table_size(c.oid)) AS table_size, " +
                "    pg_size_pretty(pg_indexes_size(c.oid)) AS indexes_size, " +
                "    (SELECT n_live_tup FROM pg_stat_user_tables WHERE relid = c.oid) AS live_rows, " +
                "    c.reltuples::bigint AS estimated_rows, " +
                "    obj_description(c.oid, 'pg_class') AS table_comment, " +
                "    u.usename AS owner, " +
                "    (SELECT count(*) FROM pg_index i WHERE i.indrelid = c.oid) AS index_count, " +
                "    (SELECT string_agg(a.attname, ', ' ORDER BY a.attnum) " +
                "     FROM pg_attribute a  " +
                "     WHERE a.attrelid = c.oid AND a.attnum > 0 AND NOT a.attisdropped) AS columns " +
                "FROM " +
                "    pg_class c " +
                "JOIN " +
                "    pg_namespace n ON c.relnamespace = n.oid " +
                "JOIN " +
                "    pg_user u ON c.relowner = u.usesysid " +
                "WHERE " +
                "    n.nspname = current_schema() " +
                "    AND c.relkind IN ('r', 'p', 'f')  -- r=普通表, p=分区表, f=外部表 " +
                "ORDER BY " +
                "    pg_total_relation_size(c.oid) DESC";
    }

    @Override
    public String getColumnsQuery(String schema, String tableName) {
        return "SELECT column_name, data_type, " +
               "col_description((table_schema||'.'||table_name)::regclass::oid, ordinal_position) AS column_comment, " +
               "is_nullable, column_default " +
               "FROM information_schema.columns " +
               "WHERE table_schema = '" + schema + "' AND table_name = '" + tableName + "' " +
               "ORDER BY ordinal_position";
    }
}
