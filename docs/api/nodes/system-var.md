---
title: 系统变量 (SystemVar)
outline: deep
---

# 系统变量 (SystemVar)

**SystemVar 节点是流程中的"数据泉眼"**。它不需要任何上游输入，就能自动产出系统级的数据——例如当前登录用户的 ID、系统当前时间、配置文件中的值、雪花算法生成的唯一 ID。这些数据从节点的输出端口流向下游，作为后续节点的"原料"使用。

---

## 🎨 画布配置指南

将 SystemVar 节点从左侧「数据节点」分类拖入画布后，你会看到一个绿色的胶囊形卡片，它没有输入端口（因为不需要来自上游的数据），只有右侧一个输出端口。

### 1. 选择系统变量

点击节点卡片上的 **变量选择器**（下拉菜单），从预置的系统变量列表中选取一个变量。选择后：

- 节点标签会自动更新为变量编码（例如 `{sys_user_id}`）
- 该变量对应的 SpEL 表达式会在后端执行，产出结果

**常见的系统变量示例：**

| 变量编码 | 说明 | 返回类型 |
| --- | --- | --- |
| `sys_user_id` | 当前登录用户 ID | String |
| `sys_user_name` | 当前登录用户名 | String |
| `sys_timestamp` | 当前时间戳 | Long |
| `sys_snow_id` | 雪花算法唯一 ID | String |
| `sys_uuid` | UUID | String |

::: tip 操作提示
系统变量由管理员在「系统宏管理」中维护。变量列表会根据你的系统配置动态展示，以上仅为常见示例。
:::

### 2. 理解"易变变量"

部分系统变量是 **易变的 (volatile)**——每次执行都会重新计算。例如 `sys_timestamp` 每次获取都是当前时间，`sys_snow_id` 每次生成都不同。

对于**非易变**的变量，引擎会在同一次流程执行中**缓存结果**，多次引用同一个系统变量不会重复计算。

### 端口连线说明

- **无输入端口**：SystemVar 是纯数据源节点，不依赖任何上游数据
- 🟢 **Out** (右侧)：系统变量的值从此端口输出

---

## 🔌 下游节点如何取值

SystemVar 节点执行完成后，变量值存入上下文：

```text
$.result    → 系统变量的值
```

此外，变量值会同时以 `variableCode` 为 Key 存入全局上下文。例如 `sys_user_id` 变量，在上下文中可以通过以下方式引用：

```text
$.sys_user_id    → 直接通过变量编码引用
```

::: info 💡小贴士
在 Database 节点的 SQL 中，你可以直接在 `${sys_user_id}` 中使用系统变量编码（前提是将 SystemVar 节点的输出连线到 Database 节点的变量输入端口）。
:::

---

## 💡 业务场景示例

### 场景一：自动填充创建人

> 创建订单时，自动记录当前操作人。

**流程搭建步骤：**

1. **Request 节点** (POST) → 接收订单数据
2. **SystemVar 节点** → 选择 `sys_user_id`
3. **Database 节点** (INSERT) → `INSERT INTO orders (product, amount, create_by) VALUES (${product}, ${amount}, ${userId})`
   - `product` / `amount` 连线自 Request Body
   - `userId` 连线自 SystemVar 节点
4. **Response 节点** → 返回创建结果

### 场景二：生成唯一单号

> 每条记录需要一个全局唯一的单号。

**流程搭建步骤：**

1. **SystemVar 节点** → 选择 `sys_snow_id`
2. **Database 节点** (INSERT) → `INSERT INTO records (record_no, ...) VALUES (${snowId}, ...)`
3. 将 SystemVar 的 Out 端口连线到 Database 的 `snowId` 变量输入

### 场景三：时间戳水印

> 返回数据时附带服务端时间戳。

**流程搭建步骤：**

1. **Database 节点** → 查到业务数据
2. **SystemVar 节点** → 选择 `sys_timestamp`
3. **Response 节点** → Body 中引用：`{ "data": "${queryResult}", "serverTime": "${timestamp}" }`

---

## 🛠️ 高级指引

::: details 查看该节点对应的底层 DSL 结构 (JSON)

SystemVar 节点在 Flow DSL 中的结构：

```json
{
  "id": "systemVar_1772498001234_1",
  "type": "systemVar",
  "label": "当前用户ID",
  "ports": [
    { "id": "out", "group": "absolute-out-solid" }
  ],
  "data": {
    "variableCode": "sys_user_id"
  }
}
```

**关键字段说明：**

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `type` | String | 固定为 `"systemVar"` |
| `data.variableCode` | String | 系统变量编码，对应 `flow_sys_macro` 表中的 `macro_code` |
| `ports` | Array | 仅有一个 `out` 输出端口，无输入端口 |

> **安全设计**：与 SystemMethod 相同，前端仅存储 `variableCode`，不暴露真实的 SpEL 表达式。

:::

::: details 查看运行时执行机制 (面向后端开发者)

**`SystemVarStepExecutor` 的执行链路：**

1. **宏查找** — 通过 `SysMacroCacheManager.getMacro(variableCode)` 获取 `CachedMacro` 实例。未找到或已停用抛出 `SYSTEM_MACRO_NOT_FOUND`。

2. **SpEL 上下文构建** —
   - 将当前流程上下文 `context.getVar()` 整体注入 SpEL 变量池
   - 注入 Spring `BeanFactory`，支持 `@bean.method()` 调用

3. **表达式执行** — 使用预编译的 `Expression.getValue(spelContext)` 执行。

4. **结果双重存储** —

   ```java
   // 1. 按变量编码存入（供 SQL 中 ${编码} 直接引用）
   context.setVar(macroCode, result);
   // 2. 按节点 ID 存入（供 JSONPath $.nodeId.out 提取）
   context.setVar(step.getId(), { "out": result });
   ```

5. **缓存机制** — 非易变变量（`volatileVar = false`）的结果会以 `CACHE_SYS_VAR_{macroCode}` 为 Key 缓存在上下文中，同一流程内多次引用不会重复执行 SpEL。

**异常类型：**

- `SYSTEM_MACRO_NOT_FOUND`：变量编码不存在或已停用
- `SYSTEM_VAR_EXEC_ERROR`：SpEL 表达式执行失败

:::
