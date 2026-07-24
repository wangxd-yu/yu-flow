# 动态数据源管理

系统通过 **Alibaba Druid** 连接池，在运行期动态注入与保活多个业务数据库连接。系统启动数据源（`spring.datasource`）以编码 `[DEFAULT]` 注册，供模型导入、未指定数据源的流程节点等场景使用。

## 场景概述

当您需要跨库联合查询，或者租户体系要求物理分离的独立 Database 时，无需在代码中手写 `DataSourceBuilder`，在【数据源管理】中维护即可。

## 操作指南

菜单路径：`/flow/dataSource`

1. **新建/编辑接入**：录入驱动类型（`mysql` / `postgresql` / `highgo`）、JDBC URL、用户名及密码，以及连接池初始/最小/最大连接数。
2. **连接测试与健康度**：
   - 列表透出健康度：正常 / 异常 / 已熔断。
   - 「测试连接」使用短超时拨测，不依赖连接池重试。
3. **启用 / 禁用**：禁用后内存连接池会卸载，相关流程/API 无法再使用该编码。
4. **系统默认数据源 `[DEFAULT]`**：
   - 列表置顶，带「系统」标签。
   - 连接信息只读（来自 `application.yml` 的 `spring.datasource.*`），不可删除或停用。
   - 可编辑 **SQL 安全墙**（见下）。

## SQL 安全墙（Druid Wall）

每个数据源（含系统默认）可配置产品化安全墙，底层映射 Druid `WallConfig`：

| 能力 | 说明 |
|------|------|
| 总开关 | 默认关闭，升级后不影响存量 SQL |
| 多语句 / 注释 / 非 CRUD | 默认禁止，降低注入与误操作面 |
| SELECT/INSERT/UPDATE/DELETE | 可按语句类型放行 |
| 表白名单 / 黑名单 | 白名单非空时仅允许列出的表 |
| 只读表 | 允许 SELECT，禁止对该表的 INSERT / UPDATE / DELETE |
| 函数黑名单 | 默认含 `sleep`、`load_file`、`pg_sleep` 等 |

生效范围：

- 流程 Database 节点、动态 API SQL 执行；
- 管理端「执行 SQL」类接口；
- 业务源在启用墙时同时挂载 Druid `WallFilter`；`[DEFAULT]` 通过统一 Guard 校验（不重建 Spring 连接池）。

修改安全墙后**立即热生效**，无需重启。生产环境建议开启，并按业务配置表白名单。
