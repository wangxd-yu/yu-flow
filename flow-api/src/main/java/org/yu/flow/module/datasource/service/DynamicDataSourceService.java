package org.yu.flow.module.datasource.service;

import org.yu.flow.module.datasource.domain.DataSourceDO;
import org.yu.flow.auto.dto.PageBean;
import org.yu.flow.module.datasource.dto.TestConnectionDTO;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Propagation;

import javax.sql.DataSource;
import java.util.List;
import java.util.Map;

/**
 * 动态数据源管理服务接口。
 * <p>
 * 管理类接口（add / update / remove / enable / disable / getById / testConnection）使用数据库主键 <b>id</b> 进行定位。
 * 执行类接口（execute / executeInTransaction / getTableList）使用 <b>code</b>（全局唯一编码）作为缓存 key，
 * 保证跨系统、跨环境下数据源的一致性。
 * </p>
 */
public interface DynamicDataSourceService {

    DataSource getDefaultDataSource();

    /**
     * 获取所有已加载的数据源（key = code）
     */
    Map<String, DataSource> getAllDataSources();

    /**
     * 根据 ID 查询单条数据源配置（密码字段已脱敏 → null）
     */
    DataSourceDO getById(String id);

    PageBean<DataSourceDO> findPage(String name, String dbType, int page, int size);

    boolean addDataSource(DataSourceDO config);

    boolean updateDataSource(DataSourceDO config);

    boolean removeDataSource(String id);

    boolean enableDataSource(String id);

    boolean disableDataSource(String id);

    /**
     * 在指定数据源上执行操作（通过 code 定位缓存中的数据源）
     *
     * @param code     数据源全局唯一编码
     * @param callback 回调
     */
    <T> T execute(String code, DataSourceCallback<T> callback);

    /**
     * 在指定数据源上以事务方式执行操作（通过 code 定位）
     *
     * @param code     数据源全局唯一编码
     * @param callback 回调
     */
    <T> T executeInTransaction(String code, DataSourceCallback<T> callback);

    /**
     * 在指定数据源上以指定传播行为的事务方式执行操作（通过 code 定位）
     *
     * @param code        数据源全局唯一编码
     * @param propagation 事务传播行为
     * @param callback    回调
     */
    <T> T executeInTransaction(String code, Propagation propagation, DataSourceCallback<T> callback);

    /**
     * 根据已保存数据源 ID 测试连接（从库中取加密密码并解密后使用）
     *
     * @param id 数据源 ID
     */
    boolean testConnection(String id);

    /**
     * 独立连通性测试：使用 DTO 中携带的 url/username/password/driver 临时建立连接。
     * 不写库、不加缓存，连接成功后立即关闭。
     * 失败时返回包含 SQLException message 的结果。
     *
     * @param dto 连接参数
     * @return key="success" 布尔值；key="message" 错误信息（成功时为空）
     */
    Map<String, Object> testConnectionByDTO(TestConnectionDTO dto);

    /**
     * 获取数据源下的表格信息（通过 code 定位）
     *
     * @param code 数据源全局唯一编码
     */
    List<Map<String, Object>> getTableList(String code);

    @FunctionalInterface
    interface DataSourceCallback<T> {
        T doInDataSource(JdbcTemplate jdbcTemplate);
    }
}
