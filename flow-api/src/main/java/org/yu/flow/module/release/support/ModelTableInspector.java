package org.yu.flow.module.release.support;

import cn.hutool.core.util.StrUtil;
import com.fasterxml.jackson.core.type.TypeReference;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.stereotype.Component;
import org.yu.flow.module.datasource.service.DynamicDataSourceService;
import org.yu.flow.module.model.domain.FlowModelInfoDO;
import org.yu.flow.module.model.dto.FieldMetaSchema;
import org.yu.flow.util.FlowObjectMapperUtil;

import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 数据模型对应的业务表在目标数据源中是否存在；不存在时按模型字段生成参考 DDL 交给 DBA 审核执行。
 *
 * <p>只读检查、只生成不执行：业务表结构变更仍走 DBA 既有流程。</p>
 */
@Slf4j
@Component
public class ModelTableInspector {

    public record Result(String modelId, String modelName, String datasource, String tableName,
                         Boolean exists, String ddl, String message) {
    }

    private static final Set<String> LENGTH_TYPES = Set.of("VARCHAR", "CHAR", "NVARCHAR", "NCHAR", "VARCHAR2", "VARBINARY");

    private static final Pattern SAFE_TYPE =
            Pattern.compile("^[A-Z][A-Z0-9 ]{0,30}(\\(\\s*\\d{1,5}\\s*(,\\s*\\d{1,3}\\s*)?\\))?$");

    @Resource
    private DynamicDataSourceService dynamicDataSourceService;

    public List<Result> inspect(List<FlowModelInfoDO> models) {
        List<Result> results = new ArrayList<>();
        if (models == null || models.isEmpty()) {
            return results;
        }
        Map<String, javax.sql.DataSource> loaded = dynamicDataSourceService.getAllDataSources();
        for (FlowModelInfoDO m : models) {
            if (StrUtil.isBlank(m.getDatasource()) || !loaded.containsKey(m.getDatasource())) {
                results.add(new Result(m.getId(), m.getName(), m.getDatasource(), m.getTableName(), null, null,
                        "数据源未就绪，无法检查业务表"));
                continue;
            }
            try {
                boolean exists = dynamicDataSourceService.execute(m.getDatasource(),
                        jt -> jt.execute((ConnectionCallback<Boolean>) con -> tableExists(con.getMetaData(),
                                con.getCatalog(), con.getSchema(), m.getTableName())));
                String dbType = dynamicDataSourceService.findDbTypeByCode(m.getDatasource());
                results.add(new Result(m.getId(), m.getName(), m.getDatasource(), m.getTableName(), exists,
                        exists ? null : generateDdl(m, dbType),
                        exists ? "业务表已存在" : "业务表不存在，请 DBA 审核参考 DDL 后建表"));
            } catch (Exception e) {
                log.warn("[ModelTableInspector] 检查业务表失败: {}.{} {}", m.getDatasource(), m.getTableName(), e.getMessage());
                results.add(new Result(m.getId(), m.getName(), m.getDatasource(), m.getTableName(), null, null,
                        "检查失败：" + e.getMessage()));
            }
        }
        return results;
    }

    /** getTables 的表名参数是 LIKE 模式：表名里的 _ / % 需要转义，并按返回的表名精确比对 */
    static boolean tableExists(DatabaseMetaData meta, String catalog, String schema, String table) throws java.sql.SQLException {
        String escape = meta.getSearchStringEscape();
        for (String name : new String[]{table, table.toUpperCase(Locale.ROOT), table.toLowerCase(Locale.ROOT)}) {
            try (ResultSet rs = meta.getTables(catalog, schema, likeLiteral(name, escape), new String[]{"TABLE", "VIEW"})) {
                while (rs.next()) {
                    if (table.equalsIgnoreCase(rs.getString("TABLE_NAME"))) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    static String likeLiteral(String name, String escape) {
        if (StrUtil.isEmpty(escape)) {
            return name;
        }
        return name.replace(escape, escape + escape).replace("_", escape + "_").replace("%", escape + "%");
    }

    /**
     * 参考 DDL：主键与索引需 DBA 按业务补充。
     * <p>模型内容来自发布包，名称、类型都按不可信输入处理：类型只接受「类型名(长度,精度)」形式，
     * 字符串字面量按目标库规则转义，注释行里的换行替换掉，避免 DBA 复制执行时混进额外语句。</p>
     */
    static String generateDdl(FlowModelInfoDO model, String dbType) {
        List<FieldMetaSchema> fields = parseFields(model.getFieldsSchema());
        boolean mysql = dbType == null || dbType.toLowerCase(Locale.ROOT).contains("mysql");
        String table = model.getTableName();
        StringBuilder sb = new StringBuilder();
        sb.append("-- 参考 DDL（由数据模型「").append(oneLine(model.getName())).append("」生成），请 DBA 补充主键与索引后执行\n");
        List<String> unknownTypes = new ArrayList<>();
        List<String> cols = new ArrayList<>();
        for (FieldMetaSchema f : fields) {
            if (StrUtil.isBlank(f.getFieldId())) {
                continue;
            }
            String type = columnType(f, mysql);
            if (type == null) {
                unknownTypes.add(oneLine(f.getFieldId()));
                type = "VARCHAR(255)";
            }
            StringBuilder col = new StringBuilder("  ").append(quote(f.getFieldId(), mysql)).append(' ').append(type);
            if (Boolean.TRUE.equals(f.getIsRequired())) {
                col.append(" NOT NULL");
            }
            if (mysql && StrUtil.isNotBlank(f.getFieldName())) {
                col.append(" COMMENT '").append(escape(f.getFieldName(), true)).append('\'');
            }
            cols.add(col.toString());
        }
        if (!unknownTypes.isEmpty()) {
            sb.append("-- 以下字段的类型无法识别，已按 VARCHAR(255) 生成，请核对：").append(String.join("、", unknownTypes)).append('\n');
        }
        sb.append("CREATE TABLE ").append(quote(table, mysql)).append(" (\n");
        sb.append(String.join(",\n", cols)).append("\n)");
        if (mysql) {
            sb.append(" ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='").append(escape(model.getName(), true)).append("';\n");
        } else {
            sb.append(";\n");
            sb.append("COMMENT ON TABLE ").append(quote(table, false)).append(" IS '").append(escape(model.getName(), false)).append("';\n");
            for (FieldMetaSchema f : fields) {
                if (StrUtil.isNotBlank(f.getFieldId()) && StrUtil.isNotBlank(f.getFieldName())) {
                    sb.append("COMMENT ON COLUMN ").append(quote(table, false)).append('.').append(quote(f.getFieldId(), false))
                            .append(" IS '").append(escape(f.getFieldName(), false)).append("';\n");
                }
            }
        }
        return sb.toString();
    }

    /** @return 列类型；类型不是「类型名(长度, 精度)」形式时返回 null */
    private static String columnType(FieldMetaSchema f, boolean mysql) {
        String type = StrUtil.blankToDefault(f.getDbType(), "VARCHAR").trim().toUpperCase(Locale.ROOT);
        if (!SAFE_TYPE.matcher(type).matches()) {
            return null;
        }
        if (!mysql) {
            type = switch (type) {
                case "DATETIME" -> "TIMESTAMP";
                case "TINYINT" -> "SMALLINT";
                case "INT" -> "INTEGER";
                case "DOUBLE" -> "DOUBLE PRECISION";
                case "LONGTEXT", "MEDIUMTEXT", "TINYTEXT" -> "TEXT";
                case "LONGBLOB", "MEDIUMBLOB", "BLOB" -> "BYTEA";
                default -> type;
            };
        }
        if (!type.contains("(") && LENGTH_TYPES.contains(type)) {
            int length = f.getLength() != null && f.getLength() > 0 ? f.getLength() : 255;
            type = type + "(" + length + ")";
        }
        return type;
    }

    private static List<FieldMetaSchema> parseFields(String json) {
        if (StrUtil.isBlank(json)) {
            return List.of();
        }
        try {
            return FlowObjectMapperUtil.flowObjectMapper().readValue(json, new TypeReference<List<FieldMetaSchema>>() {
            });
        } catch (Exception e) {
            return List.of();
        }
    }

    private static String quote(String identifier, boolean mysql) {
        String safe = oneLine(identifier).replace("`", "").replace("\"", "");
        return mysql ? "`" + safe + "`" : "\"" + safe + "\"";
    }

    /** MySQL 默认把反斜杠当转义符，先转义反斜杠再双写单引号 */
    private static String escape(String s, boolean mysql) {
        String text = oneLine(s);
        if (mysql) {
            text = text.replace("\\", "\\\\");
        }
        return text.replace("'", "''");
    }

    private static String oneLine(String s) {
        return nullToEmpty(s).replaceAll("[\\p{Cntrl}]", " ");
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }
}
