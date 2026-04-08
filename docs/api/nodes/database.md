---
title: 数据库节点 (Database)
outline: deep
---

# 数据库节点 (Database)

**Database 节点是流程中与数据库直接对话的"执行器"**。它让你无需编写任何后端代码，只需在画布上写一段 SQL，就能完成对数据库的查询、新增、修改和删除操作，并将结果传递给下游节点继续处理。

---

## 🎨 画布配置指南

将 Database 节点从左侧「调用节点」分类拖入画布后，你会看到一个蓝色的卡片，从上到下依次排列着四个配置区域。

### 1. SQL 操作类型 (sqlType)

点击节点右上角的 **操作类型标签**（默认显示 `SELECT`），可切换为以下四种：

| 操作类型 | 含义 | 典型用途 |
|---------|------|---------|
| **SELECT** | 查询数据 | 列表查询、详情查询、分页查询 |
| **INSERT** | 插入数据 | 新增用户、创建订单 |
| **UPDATE** | 更新数据 | 修改状态、更新字段 |
| **DELETE** | 删除数据 | 移除记录 |

::: tip 操作提示
当你在 SQL 编辑器中输入 SQL 语句时，系统会**自动检测**你写的 SQL 首关键字（如 `SELECT`、`INSERT`），并将操作类型自动切换过去，免去手动选择的麻烦。
:::

### 2. 返回类型 (returnType)

**仅在 `SELECT` 操作时可见。** 点击操作类型标签左侧的返回类型标签进行切换：

| 返回类型 | 引擎行为 | 适用场景 |
|---------|---------|---------|
| **LIST** | 返回一个 JSON 数组 `[{...}, {...}]` | 列表页、多条记录查询 |
| **OBJECT** | 只返回第一条记录 `{...}` | 详情页、按 ID 查单条 |
| **PAGE** | 返回分页结构 `{ content: [...], totalElements: N, ... }` | 分页表格、带 `page`/`size` 参数 |

::: warning 注意事项
当你选择 `PAGE` 返回类型时，上游传入的参数中**必须包含** `page`（页码，从 1 开始）和 `size`（每页条数）。如果不传，系统默认取第 1 页、每页 20 条。
:::

### 3. 数据源选择 (datasourceId)

在标题栏下方有一个 **"数据源"** 下拉选择器，列出的是系统中已配置的所有数据连接。

- 如果你的项目**只有一个数据库**，选择默认的主库即可
- 如果你的项目配置了**多数据源**（如主库/从库、业务库/分析库），请在此选择目标数据库

::: tip 操作提示
不选择数据源时，引擎会使用系统默认数据源执行 SQL。
:::

### 4. 动态变量 (inputs)

在数据源下方，你可以通过 **点击 `+` 按钮** 添加变量行。每个变量代表一个 SQL 中需要从上游节点动态注入的参数。

**添加变量后：**

- 节点左侧会自动出现一个**输入端口**（蓝色圆点）
- 将上游节点的输出端口连线到这个输入端口，系统会自动将上游数据绑定到该变量
- 变量名就是 SQL 中 `${变量名}` 占位符要匹配的名称

例如：你创建了一个名为 `orderId` 的变量，那在 SQL 中就用 `${orderId}` 来引用它。

### 5. SQL 编辑器

节点中间最大的区域就是 **SQL 编辑器**，支持语法高亮。在这里直接编写你的 SQL 语句，使用 `${变量名}` 作为参数占位符：

```sql
SELECT * FROM orders
WHERE status = ${status}
AND create_time > ${startDate}
```

::: tip 智能动态 SQL
引擎内置了**动态 SQL 解析器**：当某个 `${变量名}` 对应的上游参数为空或不存在时，引擎会**自动移除**包含该变量的 WHERE 条件，而不是报错。这意味着你只需写一条"全量"SQL，不需要为"有无某个条件"去维护多条 SQL。

例如下面这条 SQL：

```sql
SELECT * FROM users
WHERE name LIKE '%${keyword}%'
AND dept_id = ${deptId}
```

- 如果 `keyword` 和 `deptId` 都有值 → 两个条件都生效
- 如果只传了 `deptId`，`keyword` 为空 → `LIKE` 条件自动移除，只保留 `dept_id = ?`
- 如果都没传 → WHERE 子句整体移除，变成 `SELECT * FROM users`

:::

### 端口连线说明

- 🟢 **Result (out)**：位于节点右下角。SQL 执行成功后，查询结果或影响行数从此端口输出到下游节点

::: warning 注意事项
如果 SQL 执行过程中发生错误（语法错误、连接失败等），引擎将抛出 `DB_EXECUTE_ERROR` 异常，整条流程会中断。请确保 SQL 语法正确、数据源连接正常。
:::

---

## 🔌 下游节点如何取值

Database 节点执行完成后，会将结果存入上下文。**下游节点通过连线自动获取结果**，在需要引用具体字段时，可以使用以下路径格式：

**SELECT + LIST 返回类型（数组）：**

```text
$.result           → 整个数组 [{...}, {...}, ...]
$.result[0].name   → 第一条记录的 name 字段
```

**SELECT + OBJECT 返回类型（单条记录）：**

```text
$.result           → 单个对象 { "id": 1, "name": "张三", ... }
$.result.name      → 直接取 name 字段
```

**SELECT + PAGE 返回类型（分页结构）：**

```text
$.result.content          → 当前页数据数组
$.result.totalElements    → 总记录数
$.result.totalPages       → 总页数
```

**INSERT 返回类型（新增记录的主键）：**

```text
$.result           → 新插入记录的自增 ID
```

**UPDATE / DELETE 返回类型（影响行数）：**

```text
$.result           → 受影响的行数（整数）
```

::: info 💡小贴士
和 Request 节点一样——你在画布上连线时，系统会自动为下游节点补全取值路径。大多数情况下你无需手动填写路径，直接通过 `$.字段名` 即可获取数据。只有跨节点引用时，才需要写完整路径 `$.{节点ID}.result.{字段名}`。
:::

---

## 💡 业务场景示例

### 场景一：按条件查询用户列表

> 前端通过 `GET /api/flow/users?keyword=张&deptId=3` 调用此流程。

**流程搭建步骤：**

1. 拖入 **Request 节点**，方法为 `GET`
2. 从 **Params** 端口引出连线，连接到 **Database 节点** 的变量输入端口
3. Database 节点配置：
   - 操作类型：`SELECT`
   - 返回类型：`LIST`
   - 添加变量：`keyword`、`deptId`，分别连线到 Request 的 Params 端口
   - SQL：`SELECT * FROM users WHERE name LIKE '%${keyword}%' AND dept_id = ${deptId}`
4. 从 Database 节点的 **Result** 端口连线到 **Response 节点**

当 `keyword` 为空时，模糊搜索条件会自动消失，只按部门筛选。

### 场景二：创建订单并返回新 ID

> 前端通过 `POST /api/flow/order/create` 提交订单表单。

**流程搭建步骤：**

1. 拖入 **Request 节点**，方法为 `POST`
2. 从 **Body** 端口连线到 **Database 节点**
3. Database 节点配置：
   - 操作类型：`INSERT`
   - 添加变量：`userId`、`productName`、`amount`
   - SQL：`INSERT INTO orders (user_id, product_name, amount) VALUES (${userId}, ${productName}, ${amount})`
4. Database 节点的 **Result** 端口返回新插入的主键 ID，连接到 **Response 节点**

---

## 🛠️ 高级指引

::: details 查看该节点对应的底层 DSL 结构 (JSON)

Database 节点在 Flow DSL 中的完整结构如下。通常您无需直接编辑此 JSON，可视化画布会自动生成。

**SELECT 查询示例：**

```json
{
  "id": "database_1772498001234_1",
  "type": "database",
  "label": "查询用户列表",
  "ports": [
    { "id": "in:var:var_abc123", "group": "absolute-in-solid" },
    { "id": "out", "group": "absolute-out-solid" }
  ],
  "data": {
    "datasourceId": "ds1",
    "sqlType": "SELECT",
    "returnType": "LIST",
    "sql": "SELECT * FROM users WHERE dept_id = ${deptId}",
    "inputs": {
      "deptId": {
        "id": "var_abc123",
        "extractPath": "$.request_xxx.params.deptId"
      }
    }
  }
}
```

**INSERT 写入示例：**

```json
{
  "id": "database_1772498005678_2",
  "type": "database",
  "label": "新增订单",
  "ports": [
    { "id": "in:var:var_def456", "group": "absolute-in-solid" },
    { "id": "out", "group": "absolute-out-solid" }
  ],
  "data": {
    "datasourceId": "ds1",
    "sqlType": "INSERT",
    "sql": "INSERT INTO orders (user_id, amount) VALUES (${userId}, ${amount})",
    "inputs": {
      "userId": {
        "id": "var_def456",
        "extractPath": "$.request_xxx.body.userId"
      },
      "amount": {
        "extractPath": "$.request_xxx.body.amount"
      }
    }
  }
}
```

**关键字段说明：**

| 字段 | 类型 | 说明 |
|------|------|------|
| `type` | String | 固定为 `"database"`，引擎路由到 `DatabaseNodeExecutor` |
| `data.datasourceId` | String | 数据源标识，为空时使用默认数据源 |
| `data.sqlType` | String | `SELECT` / `INSERT` / `UPDATE` / `DELETE` |
| `data.returnType` | String | 仅 SELECT 有效：`LIST` / `OBJECT` / `PAGE` |
| `data.sql` | String | SQL 语句，使用 `${key}` 占位符引用 inputs 中的参数 |
| `data.inputs` | Object | 输入变量映射，Key 为 SQL 占位符名，Value 含 `extractPath` 取值路径 |

:::

::: details 查看运行时执行机制 (面向后端开发者)

**`DatabaseNodeExecutor` 的执行链路：**

1. **参数准备** — 调用 `prepareInputs()` 从 `ExecutionContext` 中按 `inputs` 配置的 `extractPath` 提取所有变量值，合并为 `Map<String, Object>`。

2. **动态 SQL 解析** — 将原始 SQL 和参数 Map 交给 `DynamicSqlParser.parseDynamicSqlToPrepared()`。该解析器基于 Druid SQL AST，会：
   - 将 `${key}` 占位符替换为预编译 `?`
   - 自动移除参数值为空的 WHERE 条件（含 LIKE、IN、BETWEEN 等）
   - 处理 `WHERE AND ...` 等语法修复

3. **SQL 执行** — 根据 `sqlType` 路由到 `SqlExecutorService` 的对应方法：
   - `INSERT` → `executeInsert()` → 返回新主键
   - `UPDATE` / `DELETE` → `executeUpdate()` → 返回影响行数
   - `SELECT` → 根据 `returnType` 分三路：
     - `OBJECT` → `executeObjectQuery()` → 单条 Map
     - `LIST` → `executeListQuery()` → List\<Map\>
     - `PAGE` → `executePageQuery(PageRequest)` → Spring Data Page 对象

4. **结果写入** — 执行结果存入上下文：

```java
context.setVar(step.getId(), {
    "out":    result,   // 端口标准输出
    "result": result    // 便捷别名
});
```

5. **端口路由** — 固定返回 `"out"` 端口，引擎据此寻址下游节点继续执行。

**分页参数约定：**
- `page`：页码（1-based），引擎内部转为 Spring Data 的 0-based
- `size`：每页条数，默认 20
- 分页参数从 `mergeParams` 中提取，即 inputs 中名为 `page` / `size` 的变量

**异常类型：**
- `CONFIG_ERROR`：`sqlType` 为空或 `SqlExecutorService` 未注入
- `INVALID_RETURN_TYPE`：SELECT 操作的 returnType 值不在 `LIST/OBJECT/PAGE` 范围
- `INVALID_SQL_TYPE`：sqlType 不在 `SELECT/INSERT/UPDATE/DELETE` 范围
- `DB_EXECUTE_ERROR`：SQL 执行期间的任何运行时异常

:::
