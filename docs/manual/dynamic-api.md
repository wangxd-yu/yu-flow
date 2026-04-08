# 动态 API 开发引擎

引擎最具商业价值的核心突破点：**无需代码**，实时编译构建出全特性的 `RESTful API`。

## 基本流程

通过菜单导航到 【API配置管理】 (`/flow/controller`)，通过纯界面的操作，所有的后端路由规则都可以挂载。您可以直观地定义出接口的具体属性：

- **URL 定义**：支持静态路径如 `/dynamic/student21/page` 以及动态占位符（Path Variable），例如 `/dynamic/student21?id=${id}` 用于提取主键约束条件。
- **请求方法**：全面覆盖 HTTP 规范（`GET`, `POST`, `PUT`, `DELETE`）。
- **执行生命周期**：包含状态上下架切换。在草稿状态下仅限测试引擎联调；标为“已发布”即可全局路由通行。

## 参数动态透传 (${变量名})

系统底层依赖于 `SpEL (Spring Expression Language)` 表达式栈解析机制与强大的 MyBatis / JdbcTemplate 引擎。
在 API 设计面板上，您填写的每一个 `${ids}` 或 `${id}` 都会被引擎智能提取至环境 Context。
例如：批量删除配置为 `DELETE /dynamic/student19/batch?ids=${ids}`。
系统会自动将前端传入的 URL Params 拆解，并将集合 `ids` 映射到底层的 SQL 语法树 `where id in (:ids)`，整个过程无需敲一行 Service 或 Controller 代码。
