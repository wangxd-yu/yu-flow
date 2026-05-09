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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import org.yu.flow.auto.dto.PageBean;

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

    /** 熔断探测定时调度器（自管理，不依赖宿主的 @EnableScheduling） */
    private ScheduledExecutorService circuitBreakerExecutor;

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
        startCircuitBreakerProbe();
    }

    @javax.annotation.PreDestroy
    public void destroy() {
        if (circuitBreakerExecutor != null && !circuitBreakerExecutor.isShutdown()) {
            circuitBreakerExecutor.shutdown();
            logger.info("熔断探测调度器已关闭");
        }
    }

    /**
     * 启动定时任务：
     * 1) 健康巡检：每 60 秒对所有已启用的非熔断数据源做连接测试，累积失败计数并触发熔断
     * 2) 熔断探测：每 5 分钟对已熔断的数据源做低频探测，恢复后自动重新注册连接池
     *
     * 使用自管理的 ScheduledExecutorService，避免对宿主项目强制开启 @EnableScheduling。
     */
    private void startCircuitBreakerProbe() {
        circuitBreakerExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "ds-health-check");
            t.setDaemon(true);
            return t;
        });

        // 任务 1：健康巡检（对所有已启用的非熔断数据源做连接测试）
        // 首次延迟 30 秒，之后每 60 秒执行一次
        circuitBreakerExecutor.scheduleWithFixedDelay(
                this::healthCheckAllEnabled,
                30, 60, TimeUnit.SECONDS
        );

        // 任务 2：熔断探测（对已熔断数据源做低频探测）
        // 首次延迟 60 秒，之后每 5 分钟执行一次
        circuitBreakerExecutor.scheduleWithFixedDelay(
                this::circuitBreakerProbe,
                60, 5 * 60, TimeUnit.SECONDS
        );
        logger.info("数据源健康检查调度器已启动（巡检间隔 60s，熔断探测间隔 5min）");
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

    @Override
    public PageBean<DataSourceDO> findPage(String name, String dbType, int page, int size) {
        StringBuilder sql = new StringBuilder("SELECT " + DETAIL_COLUMNS + " FROM flow_datasource WHERE 1=1");
        List<Object> params = new ArrayList<>();

        if (StrUtil.isNotBlank(name)) {
            sql.append(" AND name LIKE ?");
            params.add("%" + name + "%");
        }
        if (StrUtil.isNotBlank(dbType)) {
            sql.append(" AND db_type = ?");
            params.add(dbType);
        }

        // 查询总数
        String countSql = "SELECT COUNT(1) FROM (" + sql + ") as temp";
        Long total = defaultJdbcTemplate.queryForObject(countSql, params.toArray(), Long.class);
        if (total == null) {
            total = 0L;
        }

        // 追加排序和分页
        sql.append(" ORDER BY id DESC LIMIT ? OFFSET ?");
        params.add(size);
        params.add(page * size);

        // 查询分页记录
        List<DataSourceDO> records = defaultJdbcTemplate.query(sql.toString(), params.toArray(), (rs, rn) -> {
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
        });

        int pages = size > 0 ? (int) Math.ceil((double) total / size) : 0;
        return new PageBean<>(records, page, size, pages, total);
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
            // 启用时同时重置熔断状态，给数据源一个全新的机会
            String sql = "UPDATE flow_datasource SET status = 1, health_status = ?, error_count = 0, last_error_msg = NULL WHERE id = ?";
            int affected = defaultJdbcTemplate.update(sql, DataSourceDO.HEALTH_UNKNOWN, id);

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

            // 测试成功 → 更新健康状态，重置失败计数
            defaultJdbcTemplate.update(
                    "UPDATE flow_datasource SET health_status = ?, error_count = 0, last_error_msg = NULL WHERE id = ?",
                    DataSourceDO.HEALTH_HEALTHY, id);

            // 如果之前处于熔断状态（内存中已被移除），恢复连接池
            if (config.getStatus() != null && config.getStatus() == 1
                    && StrUtil.isNotBlank(config.getCode())
                    && !datasourceMap.containsKey(config.getCode())) {
                addDataSourceToMemory(config);
                logger.info("数据源从熔断状态恢复, code={}, name={}", config.getCode(), config.getName());
            }

            logger.info("数据源连接测试成功, code={}, name={}", config.getCode(), config.getName());
            return true;
        } catch (Exception e) {
            logger.error("数据源连接测试失败, code={}, name={}", config.getCode(), config.getName(), e);

            String errMsg = StrUtil.maxLength(e.getMessage(), 500);

            // 查询当前失败计数
            Integer currentErrorCount = queryErrorCountById(id);
            int newErrorCount = (currentErrorCount == null ? 0 : currentErrorCount) + 1;

            if (newErrorCount >= DataSourceDO.CIRCUIT_OPEN_THRESHOLD) {
                // 触发熔断 → 标记为 CIRCUIT_OPEN，从内存中移除连接池以停止 Druid 的空闲连接探测
                defaultJdbcTemplate.update(
                        "UPDATE flow_datasource SET health_status = ?, error_count = ?, last_error_msg = ? WHERE id = ?",
                        DataSourceDO.HEALTH_CIRCUIT_OPEN, newErrorCount, errMsg, id);

                if (StrUtil.isNotBlank(config.getCode())) {
                    removeDataSourceFromMemory(config.getCode());
                }
                logger.warn("数据源已触发熔断（连续失败 {} 次）, code={}, name={}",
                        newErrorCount, config.getCode(), config.getName());
            } else {
                // 未达阈值 → 标记为 UNHEALTHY
                defaultJdbcTemplate.update(
                        "UPDATE flow_datasource SET health_status = ?, error_count = ?, last_error_msg = ? WHERE id = ?",
                        DataSourceDO.HEALTH_UNHEALTHY, newErrorCount, errMsg, id);
            }
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
    //  定时健康检查（熔断探测）
    // ====================================================================

    /**
     * 定时探测处于 CIRCUIT_OPEN（熔断）状态的数据源。
     * <p>
     * 执行频率：每 5 分钟一次（相比 Druid testWhileIdle 的默认 60 秒大幅降频）。
     * 探测成功 → 状态恢复为 HEALTHY，重新将连接池注册到内存。
     * 探测失败 → 保持 CIRCUIT_OPEN，仅更新 errorCount 和 lastErrorMsg。
     * </p>
     */
    /**
     * 熔断探测核心逻辑（由 ScheduledExecutorService 调用，非 @Scheduled）。
     */
    private void circuitBreakerProbe() {
        String sql = "SELECT " + BASE_COLUMNS + ", error_count, health_status "
                + "FROM flow_datasource WHERE status = 1 AND health_status = ?";

        List<DataSourceDO> circuitOpenList;
        try {
            circuitOpenList = defaultJdbcTemplate.query(sql, (rs, rn) -> {
                DataSourceDO c = mapRowToDataSourceDO(rs);
                c.setErrorCount(rs.getInt("error_count"));
                c.setHealthStatus(rs.getString("health_status"));
                return c;
            }, DataSourceDO.HEALTH_CIRCUIT_OPEN);
        } catch (Exception e) {
            logger.error("熔断探测：查询 CIRCUIT_OPEN 数据源失败", e);
            return;
        }

        if (circuitOpenList.isEmpty()) {
            return;
        }

        logger.info("熔断探测：发现 {} 个处于熔断状态的数据源，开始探测...", circuitOpenList.size());

        for (DataSourceDO config : circuitOpenList) {
            Connection conn = null;
            try {
                Class.forName(config.getDriverClassName());
                DriverManager.setLoginTimeout(5);
                conn = DriverManager.getConnection(
                        config.getUrl(), config.getUsername(), config.getPassword());

                // 探测成功 → 恢复为 HEALTHY
                defaultJdbcTemplate.update(
                        "UPDATE flow_datasource SET health_status = ?, error_count = 0, last_error_msg = NULL WHERE id = ?",
                        DataSourceDO.HEALTH_HEALTHY, config.getId());

                // 重新注册连接池到内存
                if (StrUtil.isNotBlank(config.getCode()) && !datasourceMap.containsKey(config.getCode())) {
                    addDataSourceToMemory(config);
                }
                logger.info("熔断探测：数据源已恢复, code={}, name={}", config.getCode(), config.getName());
            } catch (Exception e) {
                // 探测仍然失败 → 仅更新 errorCount，保持 CIRCUIT_OPEN
                String errMsg = StrUtil.maxLength(e.getMessage(), 500);
                defaultJdbcTemplate.update(
                        "UPDATE flow_datasource SET error_count = error_count + 1, last_error_msg = ? WHERE id = ?",
                        errMsg, config.getId());
                logger.debug("熔断探测：数据源仍不可达, code={}, name={}, msg={}",
                        config.getCode(), config.getName(), errMsg);
            } finally {
                closeQuietly(conn);
            }
        }
    }

    /**
     * 定时健康巡检：对所有已启用且非熔断状态的数据源执行连接测试。
     * <p>
     * 这是触发熔断的关键环节：Druid 的 testWhileIdle 不会经过我们的 testConnection()，
     * 因此需要这个巡检任务主动调用 testConnection() 来累积 errorCount 并触发熔断。
     * </p>
     */
    private void healthCheckAllEnabled() {
        // 查询所有已启用且非熔断状态的数据源 ID
        String sql = "SELECT id, code, name FROM flow_datasource WHERE status = 1 AND (health_status IS NULL OR health_status != ?)";
        List<Map<String, Object>> enabledList;
        try {
            enabledList = defaultJdbcTemplate.queryForList(sql, DataSourceDO.HEALTH_CIRCUIT_OPEN);
        } catch (Exception e) {
            logger.error("健康巡检：查询已启用数据源失败", e);
            return;
        }

        if (enabledList.isEmpty()) {
            return;
        }

        for (Map<String, Object> row : enabledList) {
            String id = (String) row.get("id");
            String code = (String) row.get("code");
            String name = (String) row.get("name");
            try {
                testConnection(id);
            } catch (Exception e) {
                // testConnection 内部已处理异常并更新 DB，这里只防御性捕获
                logger.debug("健康巡检：数据源连接异常, code={}, name={}", code, name);
            }
        }
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
     * 根据 id 查询当前连续失败次数。
     */
    private Integer queryErrorCountById(String id) {
        try {
            return defaultJdbcTemplate.queryForObject(
                    "SELECT error_count FROM flow_datasource WHERE id = ?",
                    Integer.class, id);
        } catch (Exception e) {
            logger.warn("查询数据源 error_count 失败, id={}", id, e);
            return 0;
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
