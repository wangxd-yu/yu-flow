package org.yu.flow.module.model.service;

import cn.hutool.core.util.StrUtil;
import org.yu.flow.Constants;
import org.yu.flow.auto.dto.PageBean;
import org.yu.flow.module.model.domain.FlowModelInfoDO;
import org.yu.flow.module.model.dto.FlowModelInfoDTO;
import org.yu.flow.module.model.dto.SaveModelDTO;
import org.yu.flow.module.model.repository.FlowModelInfoRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import org.yu.flow.module.directory.domain.FlowDirectoryDO;
import org.yu.flow.module.directory.repository.FlowDirectoryRepository;
import org.yu.flow.module.directory.service.FlowDirectoryService;
import org.yu.flow.module.datasource.service.DynamicDataSourceService;
import org.yu.flow.module.model.dto.FieldMetaSchema;

import com.alibaba.druid.DbType;
import com.alibaba.druid.sql.SQLUtils;
import com.alibaba.druid.sql.ast.SQLStatement;
import com.alibaba.druid.sql.ast.statement.SQLColumnDefinition;
import com.alibaba.druid.sql.ast.statement.SQLCreateTableStatement;
import com.alibaba.druid.sql.ast.statement.SQLTableElement;
import com.alibaba.druid.sql.ast.statement.SQLColumnConstraint;
import com.alibaba.druid.sql.ast.statement.SQLNotNullConstraint;

import javax.annotation.Resource;
import javax.persistence.criteria.Predicate;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 数据模型信息 Service 实现
 */
@Service
public class FlowModelInfoServiceImpl implements FlowModelInfoService {

    @Resource
    private FlowModelInfoRepository flowModelInfoRepository;

    @Resource
    private FlowDirectoryRepository flowDirectoryRepository;

    @Resource
    private FlowDirectoryService flowDirectoryService;

    @Resource
    private org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;

    @Resource
    private DynamicDataSourceService dynamicDataSourceService;

    @Override
    public PageBean<FlowModelInfoDTO> findPage(String directoryId, String name, String tableName, int page, int size) {
        Pageable pageable = PageRequest.of(Math.max(page - 1, 0), size, Sort.by(Sort.Direction.DESC, "createTime"));

        Specification<FlowModelInfoDO> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (StrUtil.isNotBlank(directoryId)) {
                List<String> dirIds = flowDirectoryService.getAllChildIds(directoryId);
                if (!dirIds.isEmpty()) {
                    predicates.add(root.get("directoryId").in(dirIds));
                } else {
                    predicates.add(cb.equal(root.get("directoryId"), "-1")); // 用一个不存在的值保证查不到数据
                }
            }
            if (StrUtil.isNotBlank(name)) {
                predicates.add(cb.like(root.get("name"), "%" + name + "%"));
            }
            if (StrUtil.isNotBlank(tableName)) {
                predicates.add(cb.like(root.get("tableName"), "%" + tableName + "%"));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };

        Page<FlowModelInfoDO> result = flowModelInfoRepository.findAll(spec, pageable);

        List<FlowModelInfoDTO> content = result.getContent().stream()
                .map(entity -> {
                    FlowModelInfoDTO dto = FlowModelInfoDTO.fromDO(entity);
                    // 列表查询不返回大体积的 schema 字段，减少网络传输
                    dto.setFieldsSchema(null);
                    return dto;
                })
                .collect(Collectors.toList());

        // 使用纯 JPA 方案 B（内存拼装）解决 N+1 问题：批量获取 directoryName
        Set<String> directoryIds = content.stream()
                .map(FlowModelInfoDTO::getDirectoryId)
                .filter(StrUtil::isNotBlank)
                .collect(Collectors.toSet());
        if (!directoryIds.isEmpty()) {
            Map<String, String> dirMap = flowDirectoryRepository.findAllById(directoryIds).stream()
                    .collect(Collectors.toMap(
                            FlowDirectoryDO::getId,
                            FlowDirectoryDO::getName));
            content.forEach(dto -> dto.setDirectoryName(dirMap.get(dto.getDirectoryId())));
        }

        // 批量查询动态数据源名称
        Set<String> datasourceCodes = content.stream()
                .map(FlowModelInfoDTO::getDatasource)
                .filter(StrUtil::isNotBlank)
                .collect(Collectors.toSet());
        if (!datasourceCodes.isEmpty()) {
            Map<String, String> dsMap = new HashMap<>();
            try {
                String inClause = String.join(",", Collections.nCopies(datasourceCodes.size(), "?"));
                String sql = "SELECT code, name FROM flow_datasource WHERE code IN (" + inClause + ")";
                jdbcTemplate.query(sql, datasourceCodes.toArray(), rs -> {
                    dsMap.put(rs.getString("code"), rs.getString("name"));
                });
            } catch (Exception e) {
                // ignore
            }
            content.forEach(dto -> dto.setDatasourceName(dsMap.get(dto.getDatasource())));
        }

        return new PageBean<>(
                content,
                result.getSize(),
                result.getNumber(),
                result.getTotalPages(),
                result.getTotalElements()
        );
    }

    @Override
    public FlowModelInfoDTO findById(String id) {
        FlowModelInfoDO entity = flowModelInfoRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("数据模型不存在，id: " + id));
        return FlowModelInfoDTO.fromDO(entity);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public FlowModelInfoDO create(SaveModelDTO saveModelDTO) {
        // 校验 tableName 唯一性
        if (flowModelInfoRepository.existsByTableName(saveModelDTO.getTableName())) {
            throw new RuntimeException("物理表名已存在: " + saveModelDTO.getTableName());
        }

        FlowModelInfoDO modelInfo = FlowModelInfoDO.builder()
                .directoryId(saveModelDTO.getDirectoryId())
                .name(saveModelDTO.getName())
                .tableName(saveModelDTO.getTableName())
                .fieldsSchema(saveModelDTO.getFieldsSchema())
                .status(saveModelDTO.getStatus() == null ? 0 : saveModelDTO.getStatus())
                .createTime(LocalDateTime.now())
                .updateTime(LocalDateTime.now())
                .build();

        return flowModelInfoRepository.save(modelInfo);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public FlowModelInfoDO update(String id, SaveModelDTO saveModelDTO) {
        FlowModelInfoDO existing = flowModelInfoRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("数据模型不存在，id: " + id));

        // 校验 tableName 唯一性（排除自身）
        if (StrUtil.isNotBlank(saveModelDTO.getTableName())
                && !saveModelDTO.getTableName().equals(existing.getTableName())
                && flowModelInfoRepository.existsByTableNameAndIdNot(saveModelDTO.getTableName(), id)) {
            throw new RuntimeException("物理表名已被占用: " + saveModelDTO.getTableName());
        }

        if (StrUtil.isNotBlank(saveModelDTO.getName())) {
            existing.setName(saveModelDTO.getName());
        }
        if (StrUtil.isNotBlank(saveModelDTO.getTableName())) {
            existing.setTableName(saveModelDTO.getTableName());
        }
        if (saveModelDTO.getDirectoryId() != null) {
            existing.setDirectoryId(saveModelDTO.getDirectoryId());
        }
        if (saveModelDTO.getFieldsSchema() != null) {
            existing.setFieldsSchema(saveModelDTO.getFieldsSchema());
        }
        if (saveModelDTO.getStatus() != null) {
            existing.setStatus(saveModelDTO.getStatus());
        }

        if (saveModelDTO.getDatasource() != null) {
            existing.setDatasource(saveModelDTO.getDatasource());
        }

        existing.setUpdateTime(LocalDateTime.now());
        return flowModelInfoRepository.save(existing);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(String id) {
        if (!flowModelInfoRepository.existsById(id)) {
            throw new RuntimeException("数据模型不存在，id: " + id);
        }
        flowModelInfoRepository.deleteById(id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void batchMove(List<String> ids, String targetDirectoryId) {
        if (ids == null || ids.isEmpty()) {
            return;
        }
        if ("0".equals(targetDirectoryId) || StrUtil.isBlank(targetDirectoryId)) {
            targetDirectoryId = null;
        }
        flowModelInfoRepository.updateDirectoryIdByIds(targetDirectoryId, ids);
    }

    @Override
    public List<FieldMetaSchema> importFromDb(String datasourceCode, String tableName) {
        if (StrUtil.isBlank(datasourceCode)) {
            datasourceCode = Constants.DEFAULT_DATASOURCE_NAME;
        }

        return dynamicDataSourceService.execute(datasourceCode, jdbcTemplate -> {
            return jdbcTemplate.execute((Connection conn) -> {
                DatabaseMetaData metaData = conn.getMetaData();

                String databaseProductName = "";
                try {
                    databaseProductName = metaData.getDatabaseProductName().toLowerCase();
                } catch (Exception e) {}
                boolean isMysql = databaseProductName.contains("mysql") || databaseProductName.contains("mariadb");

                Map<String, String> mysqlColumnTypeMap = new HashMap<>();
                if (isMysql) {
                    try (java.sql.Statement stmt = conn.createStatement();
                         ResultSet rsCols = stmt.executeQuery("SHOW FULL COLUMNS FROM `" + tableName + "`")) {
                        while (rsCols.next()) {
                            mysqlColumnTypeMap.put(rsCols.getString("Field"), rsCols.getString("Type"));
                        }
                    } catch (Exception e) {
                        // ignore
                    }
                }

                // 尝试用大写表名或原名查（某些数据库对大小写敏感）
                ResultSet rs = metaData.getColumns(null, null, tableName, "%");
                if (!rs.isBeforeFirst()) {
                    rs = metaData.getColumns(null, null, tableName.toUpperCase(), "%");
                }
                if (!rs.isBeforeFirst()) {
                    rs = metaData.getColumns(null, null, tableName.toLowerCase(), "%");
                }

                List<FieldMetaSchema> list = new ArrayList<>();
                while (rs.next()) {
                    String colName = rs.getString("COLUMN_NAME");
                    String remarks = rs.getString("REMARKS");
                    String typeName = rs.getString("TYPE_NAME");
                    int nullable = rs.getInt("NULLABLE");
                    int size = rs.getInt("COLUMN_SIZE");

                    String dbType = typeName != null ? typeName.toUpperCase() : "VARCHAR";
                    String parseType = dbType;

                    if (isMysql && mysqlColumnTypeMap.containsKey(colName)) {
                        String fullType = mysqlColumnTypeMap.get(colName);
                        if (fullType != null && fullType.toUpperCase().startsWith("ENUM")) {
                            parseType = fullType;
                            dbType = "ENUM";
                        }
                    }

                    List<Map<String, String>> options = parseOptions(parseType, remarks);
                    String uiType = options != null && !options.isEmpty() ? "select" : mapDbTypeToUiType(dbType);

                    FieldMetaSchema field = FieldMetaSchema.builder()
                            .fieldId(StrUtil.toCamelCase(colName))
                            .fieldName(StrUtil.isNotBlank(remarks) ? remarks : colName)
                            .dbType(dbType)
                            .uiType(uiType)
                            .isRequired(nullable == DatabaseMetaData.columnNoNulls)
                            .length(size)
                            .options(options)
                            .build();
                    list.add(field);
                }
                return list;
            });
        });
    }

    @Override
    public List<FieldMetaSchema> importFromDdl(String ddl, String dbTypeStr) {
        if (StrUtil.isBlank(ddl)) {
            return Collections.emptyList();
        }

        DbType dbType = null;
        if (StrUtil.isNotBlank(dbTypeStr)) {
            try {
                dbType = DbType.valueOf(dbTypeStr.toLowerCase());
            } catch (Exception e) {
                // Ignore parsing invalid
            }
        }

        List<SQLStatement> stmtList;
        try {
            if (dbType != null) {
                stmtList = SQLUtils.parseStatements(ddl, dbType);
            } else {
                // try mysql by default
                try {
                    stmtList = SQLUtils.parseStatements(ddl, DbType.mysql);
                } catch (Exception e) {
                    // fallback to postgresql
                    stmtList = SQLUtils.parseStatements(ddl, DbType.postgresql);
                }
            }
        } catch (Exception e) {
            throw new RuntimeException("DDL 建表语句解析失败: " + e.getMessage(), e);
        }

        List<FieldMetaSchema> list = new ArrayList<>();
        for (SQLStatement stmt : stmtList) {
            if (stmt instanceof SQLCreateTableStatement) {
                SQLCreateTableStatement createTable = (SQLCreateTableStatement) stmt;
                for (SQLTableElement element : createTable.getTableElementList()) {
                    if (element instanceof SQLColumnDefinition) {
                        SQLColumnDefinition col = (SQLColumnDefinition) element;
                        String colName = col.getName().getSimpleName().replace("`", "").replace("\"", "");
                        String typeName = col.getDataType().getName();

                        String comment = "";
                        if (col.getComment() != null) {
                            comment = col.getComment().toString().replace("'", "").replace("\"", "");
                        }

                        boolean isRequired = false;
                        for (SQLColumnConstraint constraint : col.getConstraints()) {
                            if (constraint instanceof SQLNotNullConstraint) {
                                isRequired = true;
                            }
                        }

                        List<Map<String, String>> options = parseOptions(col.getDataType().toString(), comment);
                        String uiType = options != null && !options.isEmpty() ? "select" : mapDbTypeToUiType(typeName);

                        FieldMetaSchema field = FieldMetaSchema.builder()
                                .fieldId(StrUtil.toCamelCase(colName))
                                .fieldName(StrUtil.isNotBlank(comment) ? comment : colName)
                                .dbType(typeName)
                                .uiType(uiType)
                                .isRequired(isRequired)
                                .options(options)
                                .build();
                        list.add(field);
                    }
                }
            }
        }
        return list;
    }

    private String mapDbTypeToUiType(String dbType) {
        if (dbType == null) return "input-text";
        String upper = dbType.toUpperCase();
        if (upper.contains("VARCHAR") || upper.contains("CHAR") || upper.contains("TEXT")) return "input-text";
        if (upper.contains("INT") || upper.contains("NUMBER") || upper.contains("DECIMAL") || upper.contains("DOUBLE") || upper.contains("FLOAT") || upper.contains("NUMERIC")) return "input-number";
        if (upper.contains("DATE") || upper.contains("TIME")) return "input-date";
        if (upper.contains("BOOL")) return "switch";
        return "input-text";
    }

    private List<Map<String, String>> parseOptions(String dbTypeStr, String comment) {
        List<Map<String, String>> options = new ArrayList<>();
        // 1. 尝试从 ENUM('M', 'F') 中提取 (不分大小写)
        if (StrUtil.isNotBlank(dbTypeStr) && dbTypeStr.toUpperCase().contains("ENUM")) {
            java.util.regex.Matcher m = java.util.regex.Pattern.compile("'([^']+)'").matcher(dbTypeStr);
            while (m.find()) {
                Map<String, String> option = new HashMap<>();
                option.put("label", m.group(1));
                option.put("value", m.group(1));
                options.add(option);
            }
        }

        // 2. 从注释中提取字典描述，形如: 性别(1:男, 2:女) 或 状态[1=开启 0=关闭] 或 删除标志(1-是,0-否)
        List<Map<String, String>> commentOptions = new ArrayList<>();
        if (StrUtil.isNotBlank(comment)) {
            // 匹配各类中文、英文的括号包裹的内容
            java.util.regex.Matcher bracketMatcher = java.util.regex.Pattern.compile("[(（\\[【](.*?)[)）\\]】]").matcher(comment);
            if (bracketMatcher.find()) {
                String inner = bracketMatcher.group(1);
                // 常见的间隔符拆分：逗号，分号，或者偶尔会只用空格
                String[] parts = inner.split("[,，;；\\s]+");
                for (String part : parts) {
                    if (StrUtil.isBlank(part)) continue;
                    // 匹配常见的映射键值对，例如："1:男" or "1=男" or "1-男" (支持冒号、等号、减号，允许中间有空格)
                    java.util.regex.Matcher kvMatcher = java.util.regex.Pattern.compile("^([a-zA-Z0-9_]+)\\s*[:=：\\-]\\s*(.+)$").matcher(part.trim());
                    if (kvMatcher.find()) {
                        Map<String, String> option = new HashMap<>();
                        option.put("value", kvMatcher.group(1).trim());
                        option.put("label", kvMatcher.group(2).trim());
                        commentOptions.add(option);
                    }
                }
            }
        }

        // 注释里面的映射往往带有更有意义的中文 Label，所以优先级高于单纯的字母 ENUM 配置
        if (!commentOptions.isEmpty()) {
            return commentOptions;
        }
        if (!options.isEmpty()) {
            return options;
        }
        return null;
    }
}
