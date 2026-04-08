package org.yu.flow.module.datasource.service;

import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;
import com.alibaba.druid.pool.DruidDataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.yu.flow.Constants;
import org.yu.flow.module.datasource.domain.DataSourceDO;
import org.yu.flow.module.datasource.dto.TestConnectionDTO;
import org.yu.flow.module.datasource.metadata.DatabaseMetadataQueries;
import org.yu.flow.module.datasource.metadata.DatabaseMetadataQueriesFactory;
import org.yu.flow.util.AesEncryptUtil;
import org.yu.flow.util.CamelCaseColumnMapRowMapper;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 动态数据源管理服务实现。
 * <p>
 * 核心设计：所有内存缓存（datasourceMap / jdbcTemplateMap / transactionManagerMap）
 * 统一以 <b>code</b>（数据源全局唯一编码）作为 key，确保跨系统、跨环境的一致性。
 * </p>
 */
@Service
public class DynamicDataSourceServiceImpl implements DynamicDataSourceService {
    private static final Logger logger = LoggerFactory.getLogger(DynamicDataSourceServiceImpl.class);

    /** 数据源缓存：key = code */
    private final Map<String, DataSource> datasourceMap = new ConcurrentHashMap<>();
    /** JdbcTemplate 缓存：key = code */
    private final Map<String, JdbcTemplate> jdbcTemplateMap = new ConcurrentHashMap<>();
    /** 事务管理器缓存：key = code */
    private final Map<String, PlatformTransactionManager> transactionManagerMap = new ConcurrentHashMap<>();

    @Resource
    private DataSource defaultDataSource;
    @Resource
    private JdbcTemplate defaultJdbcTemplate;
    @Resource
    private AesEncryptUtil aesEncryptUtil;

    // ====================================================================
    //  通用查询 SQL 片段
    // ====================================================================
    private static final String BASE_COLUMNS =
            "id, name, code, db_type, driver_class_name, url, username, password, "
                    + "initial_size, min_idle, max_active, status";

    private static final String DETAIL_COLUMNS =
            "id, name, code, db_type, driver_class_name, url, username, "
                    + "initial_size, min_idle, max_active, status, "
                    + "health_status, error_count, last_error_msg, create_time, update_time";

    // ====================================================================
    //  初始化
    // ====================================================================

    @PostConstruct
    public void init() {
        loadAllEnabledDataSources();
    }

    /**
     * 将所有启用的数据源加载到内存缓存。
     */
    private void loadAllEnabledDataSources() {
        String sql = "SELECT " + BASE_COLUMNS + " FROM flow_datasource WHERE status = 1";
        try {
            // 默认将本机数据源存储到 map 中
            registerDefaultDataSource();

            defaultJdbcTemplate.query(sql, rs -> {
                DataSourceDO config = mapRowToDataSourceDO(rs);
                addDataSourceToMemory(config);
                logger.info("初始化数据源成功: code={}, name={}", config.getCode(), config.getName());
            });
        } catch (Exception e) {
            logger.error("从数据库加载数据源失败", e);
        }
    }

    /**
     * 根据 ID 加载单条数据源到内存（用于测试连接等临时场景）。
     */
    private void loadDataSourceById(String id) {
        String sql = "SELECT " + BASE_COLUMNS + " FROM flow_datasource WHERE status = 1 AND id = ?";
        try {
            registerDefaultDataSource();

            defaultJdbcTemplate.query(sql, new Object[]{id}, rs -> {
                DataSourceDO config = mapRowToDataSourceDO(rs);
                addDataSourceToMemory(config);
                logger.info("临时加载数据源成功: code={}, name={}", config.getCode(), config.getName());
            });
        } catch (Exception e) {
            logger.error("从数据库加载数据源失败, id={}", id, e);
        }
    }

    /**
     * 注册默认数据源到缓存。
     */
    private void registerDefaultDataSource() {
        datasourceMap.put(Constants.DEFAULT_DATASOURCE_NAME, defaultDataSource);
        jdbcTemplateMap.put(Constants.DEFAULT_DATASOURCE_NAME, defaultJdbcTemplate);
    }

    // ====================================================================
    //  ResultSet → DO 映射（消除重复的 RowMapper 代码）
    // ====================================================================

    /**
     * 从 ResultSet 中读取一行并映射为 DataSourceDO（含密码解密）。
     */
    private DataSourceDO mapRowToDataSourceDO(java.sql.ResultSet rs) throws SQLException {
        DataSourceDO config = new DataSourceDO();
        config.setId(rs.getString("id"));
        config.setName(rs.getString("name"));
        config.setCode(rs.getString("code"));
        config.setDbType(rs.getString("db_type"));
        config.setDriverClassName(rs.getString("driver_class_name"));
        config.setUrl(rs.getString("url"));
        config.setUsername(rs.getString("username"));

        // 解密密码
        String encryptedPassword = rs.getString("password");
        try {
            config.setPassword(aesEncryptUtil.decrypt(encryptedPassword));
        } catch (Exception e) {
            logger.error("数据源[code={}, name={}]密码解密失败, 加密密码值: {}",
                    config.getCode(), config.getName(), encryptedPassword, e);
            throw new RuntimeException("数据源密码解密失败", e);
        }

        config.setInitialSize(rs.getInt("initial_size"));
        config.setMinIdle(rs.getInt("min_idle"));
        config.setMaxActive(rs.getInt("max_active"));
        config.setStatus(rs.getInt("status"));
        return config;
    }

    // ====================================================================
    //  连接池生命周期管理
    // ====================================================================

    /**
     * 根据配置创建 Druid 连接池。
     */
    private DruidDataSource createDataSource(DataSourceDO config) {
        DruidDataSource ds = new DruidDataSource();
        ds.setName(config.getName());
        ds.setDbType(config.getDbType());
        ds.setDriverClassName(config.getDriverClassName());
        ds.setUrl(config.getUrl());
        ds.setUsername(config.getUsername());
        ds.setPassword(config.getPassword());
        ds.setInitialSize(config.getInitialSize());
        ds.setMinIdle(config.getMinIdle());
        ds.setMaxActive(config.getMaxActive());
        ds.setTestWhileIdle(true);
        ds.setTestOnBorrow(false);
        ds.setTestOnReturn(false);
        ds.setValidationQuery(getValidationQuery(config.getDbType()));

        try {
            ds.init();
        } catch (SQLException e) {
            logger.error("Druid 数据源初始化失败, code={}", config.getCode(), e);
            throw new RuntimeException("Failed to initialize Druid datasource", e);
        }
        return ds;
    }

    private String getValidationQuery(String dbType) {
        // 目前所有数据库类型统一使用 SELECT 1
        return "SELECT 1";
    }

    /**
     * 将数据源加入内存缓存（key = code）。
     */
    private void addDataSourceToMemory(DataSourceDO config) {
        String cacheKey = config.getCode();
        if (StrUtil.isBlank(cacheKey)) {
            logger.warn("数据源 code 为空，跳过缓存注册, id={}, name={}", config.getId(), config.getName());
            return;
        }
        DruidDataSource ds = createDataSource(config);
        datasourceMap.put(cacheKey, ds);
        jdbcTemplateMap.put(cacheKey, new JdbcTemplate(ds));
        logger.debug("数据源已注册到缓存, code={}", cacheKey);
    }

    /**
     * 从内存缓存中移除数据源并关闭连接池（key = code）。
     */
    private void removeDataSourceFromMemory(String code) {
        DataSource ds = datasourceMap.remove(code);
        jdbcTemplateMap.remove(code);
        transactionManagerMap.remove(code);
        if (ds instanceof DruidDataSource) {
            ((DruidDataSource) ds).close();
            logger.debug("数据源连接池已关闭, code={}", code);
        }
    }

    // ====================================================================
    //  公开查询接口
    // ====================================================================

    @Override
    public DataSource getDefaultDataSource() {
        return defaultDataSource;
    }

    @Override
    public Map<String, DataSource> getAllDataSources() {
        return new HashMap<>(datasourceMap);
    }

    @Override
    public DataSourceDO getById(String id) {
        String sql = "SELECT " + DETAIL_COLUMNS + " FROM flow_datasource WHERE id = ?";
        return defaultJdbcTemplate.queryForObject(sql, (rs, rn) -> {
            DataSourceDO c = new DataSourceDO();
            c.setId(rs.getString("id"));
            c.setName(rs.getString("name"));
            c.setCode(rs.getString("code"));
            c.setDbType(rs.getString("db_type"));
            c.setDriverClassName(rs.getString("driver_class_name"));
            c.setUrl(rs.getString("url"));
            c.setUsername(rs.getString("username"));
            c.setPassword(null);  // ⚠️ 脱敏：密码不返回给前端
            c.setInitialSize(rs.getInt("initial_size"));
            c.setMinIdle(rs.getInt("min_idle"));
            c.setMaxActive(rs.getInt("max_active"));
            c.setStatus(rs.getInt("status"));
            c.setHealthStatus(rs.getString("health_status"));
            c.setErrorCount(rs.getInt("error_count"));
            c.setLastErrorMsg(rs.getString("last_error_msg"));
            c.setCreateTime(rs.getTimestamp("create_time"));
            c.setUpdateTime(rs.getTimestamp("update_time"));
            return c;
        }, id);
    }

    // ====================================================================
    //  CRUD 操作
    // ====================================================================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean addDataSource(DataSourceDO config) {
        try {
            // code 唯一性校验
            if (StrUtil.isNotBlank(config.getCode())) {
                Integer count = defaultJdbcTemplate.queryForObject(
                        "SELECT COUNT(1) FROM flow_datasource WHERE code = ?",
                        Integer.class, config.getCode());
                if (count != null && count > 0) {
                    throw new IllegalArgumentException("数据源编码已存在: " + config.getCode());
                }
            } else {
                throw new IllegalArgumentException("数据源编码(code)不能为空");
            }

            String encryptedPassword = aesEncryptUtil.encrypt(config.getPassword());
            String sql = "INSERT INTO flow_datasource(id, name, code, db_type, driver_class_name, url, " +
                    "username, password, initial_size, min_idle, max_active, status) " +
                    "VALUES(?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

            config.setId(String.valueOf(IdUtil.getSnowflake(1, 1).nextId()));

            int affected = defaultJdbcTemplate.update(sql,
                    config.getId(), config.getName(), config.getCode(),
                    config.getDbType(), config.getDriverClassName(),
                    config.getUrl(), config.getUsername(), encryptedPassword,
                    config.getInitialSize(), config.getMinIdle(), config.getMaxActive(), 1);

            if (affected > 0) {
                addDataSourceToMemory(config);
                return true;
            }
            return false;
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            logger.error("添加数据源失败, code={}", config.getCode(), e);
            throw new RuntimeException("Failed to add datasource", e);
        }
    }

    @Override
    public boolean updateDataSource(DataSourceDO config) {
        try {
            // 查询当前数据源的 code，用于更新内存缓存
            String code = queryCodeById(config.getId());
            if (StrUtil.isBlank(code)) {
                throw new IllegalArgumentException("数据源不存在, id=" + config.getId());
            }

            final int affected;
            if (StrUtil.isBlank(config.getPassword())) {
                // 前端未传密码 → 不更新密码列
                String sql = "UPDATE flow_datasource SET name=?, db_type=?, driver_class_name=?, url=?, "
                        + "username=?, initial_size=?, min_idle=?, max_active=? "
                        + "WHERE id=?";
                affected = defaultJdbcTemplate.update(sql,
                        config.getName(), config.getDbType(), config.getDriverClassName(), config.getUrl(),
                        config.getUsername(),
                        config.getInitialSize(), config.getMinIdle(), config.getMaxActive(),
                        config.getId());

                // 需要用数据库已有密码重建内存中的数据源
                if (affected > 0) {
                    String rawPassword = defaultJdbcTemplate.queryForObject(
                            "SELECT password FROM flow_datasource WHERE id = ?",
                            String.class, config.getId());
                    config.setPassword(aesEncryptUtil.decrypt(rawPassword));
                }
            } else {
                // 密码非空：加密后一并更新
                String encryptedPassword = aesEncryptUtil.encrypt(config.getPassword());
                String sql = "UPDATE flow_datasource SET name=?, db_type=?, driver_class_name=?, url=?, "
                        + "username=?, password=?, initial_size=?, min_idle=?, max_active=? "
                        + "WHERE id=?";
                affected = defaultJdbcTemplate.update(sql,
                        config.getName(), config.getDbType(), config.getDriverClassName(), config.getUrl(),
                        config.getUsername(), encryptedPassword,
                        config.getInitialSize(), config.getMinIdle(), config.getMaxActive(),
                        config.getId());
            }

            if (affected > 0) {
                // 用 code 操作内存缓存
                removeDataSourceFromMemory(code);
                // config 里可能没设 code，补上
                config.setCode(code);
                addDataSourceToMemory(config);
                return true;
            }
            return false;
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            logger.error("更新数据源失败, id={}", config.getId(), e);
            throw new RuntimeException("Failed to update datasource", e);
        }
    }

    @Override
    public boolean removeDataSource(String id) {
        try {
            // 先查出 code，用于清理缓存
            String code = queryCodeById(id);

            String sql = "DELETE FROM flow_datasource WHERE id = ?";
            int affected = defaultJdbcTemplate.update(sql, id);

            if (affected > 0 && StrUtil.isNotBlank(code)) {
                removeDataSourceFromMemory(code);
            }
            return affected > 0;
        } catch (Exception e) {
            logger.error("删除数据源失败, id={}", id, e);
            throw new RuntimeException("Failed to remove datasource", e);
        }
    }

    @Override
    public boolean enableDataSource(String id) {
        try {
            String sql = "UPDATE flow_datasource SET status = 1 WHERE id = ?";
            int affected = defaultJdbcTemplate.update(sql, id);

            if (affected > 0) {
                // 重新加载到内存
                loadDataSourceById(id);
                return true;
            }
            return false;
        } catch (Exception e) {
            logger.error("启用数据源失败, id={}", id, e);
            throw new RuntimeException("Failed to enable datasource", e);
        }
    }

    @Override
    public boolean disableDataSource(String id) {
        try {
            // 先查出 code
            String code = queryCodeById(id);

            String sql = "UPDATE flow_datasource SET status = 0 WHERE id = ?";
            int affected = defaultJdbcTemplate.update(sql, id);

            if (affected > 0 && StrUtil.isNotBlank(code)) {
                removeDataSourceFromMemory(code);
                return true;
            }
            return affected > 0;
        } catch (Exception e) {
            logger.error("禁用数据源失败, id={}", id, e);
            throw new RuntimeException("Failed to disable datasource", e);
        }
    }

    // ====================================================================
    //  执行引擎（外部通过 code 访问数据源）
    // ====================================================================

    @Override
    public <T> T execute(String code, DataSourceCallback<T> callback) {
        JdbcTemplate jt = jdbcTemplateMap.get(code);
        if (jt == null) {
            throw new IllegalArgumentException("数据源未找到, code=" + code);
        }
        return callback.doInDataSource(jt);
    }

    @Override
    public <T> T executeInTransaction(String code, DataSourceCallback<T> callback) {
        return executeInTransaction(code, Propagation.REQUIRED, callback);
    }

    @Override
    public <T> T executeInTransaction(String code, Propagation propagation, DataSourceCallback<T> callback) {
        DataSource ds = datasourceMap.get(code);
        if (ds == null) {
            throw new IllegalArgumentException("数据源未找到, code=" + code);
        }

        // 获取或创建事务管理器
        PlatformTransactionManager txManager = transactionManagerMap.computeIfAbsent(
                code, k -> new DataSourceTransactionManager(ds)
        );

        TransactionTemplate txTemplate = new TransactionTemplate(txManager);
        txTemplate.setPropagationBehavior(propagation.value());

        return txTemplate.execute(status -> {
            JdbcTemplate jt = jdbcTemplateMap.get(code);
            return callback.doInDataSource(jt);
        });
    }

    // ====================================================================
    //  连接测试
    // ====================================================================

    @Override
    public boolean testConnection(String id) {
        // 1. 从数据库查询数据源配置（不限 status，启用/禁用均可测试）
        DataSourceDO config = queryDataSourceById(id);
        if (config == null) {
            throw new IllegalArgumentException("数据源不存在, id=" + id);
        }

        // 2. 使用原生 JDBC 测试连接，不创建 Druid 连接池（避免池初始化的重试机制导致延迟）
        Connection conn = null;
        try {
            Class.forName(config.getDriverClassName());
            DriverManager.setLoginTimeout(5); // 5 秒超时，单次尝试

            conn = DriverManager.getConnection(
                    config.getUrl(), config.getUsername(), config.getPassword());

            // 测试成功 → 更新健康状态
            defaultJdbcTemplate.update(
                    "UPDATE flow_datasource SET health_status = ?, error_count = 0, last_error_msg = NULL WHERE id = ?",
                    DataSourceDO.HEALTH_HEALTHY, id);
            logger.info("数据源连接测试成功, code={}, name={}", config.getCode(), config.getName());
            return true;
        } catch (Exception e) {
            logger.error("数据源连接测试失败, code={}, name={}", config.getCode(), config.getName(), e);

            String errMsg = StrUtil.maxLength(e.getMessage(), 500);

            // 测试失败 → 更新为 UNHEALTHY
            defaultJdbcTemplate.update(
                    "UPDATE flow_datasource SET health_status = ?, error_count = error_count + 1, last_error_msg = ? WHERE id = ?",
                    DataSourceDO.HEALTH_UNHEALTHY, errMsg, id);
            return false;
        } finally {
            closeQuietly(conn);
        }
    }

    @Override
    public Map<String, Object> testConnectionByDTO(TestConnectionDTO dto) {
        Map<String, Object> result = new LinkedHashMap<>();
        Connection conn = null;
        try {
            // 1. 加载驱动
            Class.forName(dto.getDriverClassName());

            // 2. 设置登录超时（秒）
            DriverManager.setLoginTimeout(5);

            // 3. 获取测试连接用的密码
            String testPassword = dto.getPassword();
            if (StrUtil.isBlank(testPassword) && StrUtil.isNotBlank(dto.getId())) {
                // 编辑模式下，如果前端未填新密码，就用传入的 ID 从数据库查现有密码
                String rawPassword = defaultJdbcTemplate.queryForObject(
                        "SELECT password FROM flow_datasource WHERE id = ?",
                        String.class, dto.getId());
                if (StrUtil.isNotBlank(rawPassword)) {
                    testPassword = aesEncryptUtil.decrypt(rawPassword);
                }
            }
            if (testPassword == null) {
                testPassword = "";
            }

            // 4. 尝试建立连接
            conn = DriverManager.getConnection(dto.getUrl(), dto.getUsername(), testPassword);

            result.put("success", true);
            result.put("message", "");
            logger.info("[testConnectionByDTO] 连接成功, url={}", dto.getUrl());
        } catch (ClassNotFoundException e) {
            result.put("success", false);
            result.put("message", "驱动类未找到: " + e.getMessage());
            logger.warn("[testConnectionByDTO] 驱动加载失败", e);
        } catch (SQLException e) {
            result.put("success", false);
            result.put("message", e.getMessage());
            logger.warn("[testConnectionByDTO] 连接失败, url={}, msg={}", dto.getUrl(), e.getMessage());
        } catch (Exception e) {
            result.put("success", false);
            result.put("message", "未知异常: " + e.getMessage());
            logger.error("[testConnectionByDTO] 未知异常", e);
        } finally {
            closeQuietly(conn);
        }
        return result;
    }

    // ====================================================================
    //  元数据查询
    // ====================================================================

    @Override
    public List<Map<String, Object>> getTableList(String code) {
        DatabaseMetadataQueries queries = DatabaseMetadataQueriesFactory.getQueries(
                getDbTypeByCode(code));
        return execute(code,
                jt -> jt.query(queries.getTablesQuery(), new CamelCaseColumnMapRowMapper()));
    }

    // ====================================================================
    //  私有工具方法
    // ====================================================================

    /**
     * 根据 id 查询对应的 code。
     */
    private String queryCodeById(String id) {
        try {
            return defaultJdbcTemplate.queryForObject(
                    "SELECT code FROM flow_datasource WHERE id = ?",
                    String.class, id);
        } catch (Exception e) {
            logger.warn("查询数据源 code 失败, id={}", id, e);
            return null;
        }
    }

    /**
     * 根据 id 查询完整的数据源配置（不限 status，返回含解密密码的 DO）。
     * 用于 testConnection 等需要临时创建连接池的场景。
     */
    private DataSourceDO queryDataSourceById(String id) {
        String sql = "SELECT " + BASE_COLUMNS + " FROM flow_datasource WHERE id = ?";
        try {
            return defaultJdbcTemplate.queryForObject(sql, (rs, rn) -> {
                return mapRowToDataSourceDO(rs);
            }, id);
        } catch (Exception e) {
            logger.warn("查询数据源配置失败, id={}", id, e);
            return null;
        }
    }

    /**
     * 根据 code 从缓存中查询 dbType（用于元数据查询等场景）。
     */
    private String getDbTypeByCode(String code) {
        DataSource ds = datasourceMap.get(code);
        if (ds instanceof DruidDataSource) {
            return ((DruidDataSource) ds).getDbType();
        }
        // 兜底：从数据库查询
        try {
            return defaultJdbcTemplate.queryForObject(
                    "SELECT db_type FROM flow_datasource WHERE code = ?",
                    String.class, code);
        } catch (Exception e) {
            logger.warn("查询数据库类型失败, code={}", code, e);
            return "postgresql";
        }
    }

    /**
     * 安静地关闭 JDBC 连接。
     */
    private void closeQuietly(Connection conn) {
        if (conn != null) {
            try {
                conn.close();
            } catch (SQLException ignore) {
                // ignore close error
            }
        }
    }
}
