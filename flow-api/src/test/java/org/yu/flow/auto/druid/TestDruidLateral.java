package org.yu.flow.auto.druid;

import com.alibaba.druid.sql.dialect.postgresql.parser.PGSQLStatementParser;
import com.alibaba.druid.sql.parser.SQLStatementParser;

public class TestDruidLateral {
    public static void main(String[] args) {
        String[] sqls = {
            "SELECT generate_series(DATE_TRUNC('month', CURRENT_TIMESTAMP)::DATE, ...)",
            "WITH month_all_dates AS ( SELECT generate_series( DATE_TRUNC('month', CURRENT_TIMESTAMP)::DATE, (DATE_TRUNC('month', CURRENT_TIMESTAMP) + INTERVAL '1 month' - INTERVAL '1 day')::DATE, INTERVAL '1 day' )::DATE AS sales_date ) SELECT 1"
        };
        for (String sql : sqls) {
            try {
                System.out.println("Parsing: " + sql);
                SQLStatementParser parser = new PGSQLStatementParser(sql);
                parser.parseStatement();
                System.out.println("  -> SUCCESS");
            } catch (Exception e) {
                System.out.println("  -> FAILED: " + e.getMessage());
            }
        }
    }
}
