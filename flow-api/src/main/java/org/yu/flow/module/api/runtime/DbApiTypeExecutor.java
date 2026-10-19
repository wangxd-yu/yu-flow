package org.yu.flow.module.api.runtime;

import cn.hutool.core.util.StrUtil;
import org.springframework.stereotype.Component;
import org.yu.flow.dto.SqlAndParams;
import org.yu.flow.engine.sql.DynamicSqlParser;
import org.yu.flow.module.api.domain.FlowApiDO;

import java.util.Set;

/**
 * 数据库接口：解析动态 SQL 后按 responseType 执行查询或写入。
 */
@Component
public class DbApiTypeExecutor implements ApiTypeExecutor {

    private final ApiSqlExecutor sqlExecutor;

    public DbApiTypeExecutor(ApiSqlExecutor sqlExecutor) {
        this.sqlExecutor = sqlExecutor;
    }

    @Override
    public Set<String> serviceTypes() {
        return Set.of("DB");
    }

    @Override
    public Object execute(Invocation invocation) {
        FlowApiDO api = invocation.api();
        SqlAndParams sql = DynamicSqlParser.parseDynamicSqlToPrepared(invocation.content(), invocation.params());
        if (invocation.logContext() != null) {
            invocation.logContext().actualSql(ApiSqlText.forLog(api.getResponseType(), sql, invocation.pageable()));
        }
        String datasource = api.getDatasource();
        return switch (StrUtil.nullToEmpty(api.getResponseType())) {
            case "PAGE" -> sqlExecutor.executePageQuery(datasource, sql, invocation.pageable());
            case "LIST" -> sqlExecutor.executeListQuery(datasource, sql, invocation.pageable());
            case "OBJECT" -> sqlExecutor.executeObjectQuery(datasource, sql);
            case "UPDATE" -> sqlExecutor.executeUpdate(datasource, sql);
            case "INSERT" -> sqlExecutor.executeInsert(datasource, sql);
            default -> throw new UnsupportedOperationException("业务异常：未知的 responseType: " + api.getResponseType());
        };
    }
}
