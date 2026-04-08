---
title: 条件判断 (If)
outline: deep
---

# 条件判断 (If)

**If 节点是流程中的"十字路口"**。它根据你编写的一条表达式来判断真假，然后让数据流向右侧的两条分支之一——`True` 或 `False`。这是实现"如果…就…否则…"业务逻辑的核心节点。

---

## 🎨 画布配置指南

将 If 节点从左侧「逻辑节点」分类拖入画布后，你会看到一个蓝色菱形卡片，左侧有一个输入端口，右侧有 **True** 和 **False** 两个输出端口。

### 1. 选择表达式语言

在右侧属性面板中，首先选择 **表达式语言**：

| 语言 | 适用场景 | 示例 |
| --- | --- | --- |
| **JavaScript** (默认) | 最通用，支持丰富的逻辑运算 | `age >= 18 && status === 'active'` |
| **Aviator** | 轻量、高性能的规则表达式 | `age >= 18 && status == 'active'` |
| **SpEL** | 需要调用 Spring Bean 时 | `#age >= 18 && #status == 'active'` |

::: tip 操作提示
如果你不确定选哪种语言，直接使用默认的 **JavaScript** 即可。它语法最简单，且支持所有常见的比较和逻辑运算。
:::

### 2. 编写条件表达式

在属性面板的 **「条件表达式」** 文本框中，编写一个返回 `true` 或 `false` 的表达式。表达式中可以直接引用上游传入的变量名。

**常用写法：**

```javascript
// 数值比较
amount > 1000

// 字符串判断
status === 'approved'

// 多条件组合
age >= 18 && vipLevel > 0

// 空值检测
name !== null && name !== ''
```

### 3. 理解两个输出端口

| 端口 | 含义 | 触发条件 |
| --- | --- | --- |
| ✅ **True** (实心圆) | 条件成立分支 | 表达式结果为 `true`、非零数字、非空字符串/集合 |
| ❌ **False** (空心圆) | 条件不成立分支 | 表达式结果为 `false`、`0`、`null`、空字符串/集合 |

**如何连线？** 分别从 True 和 False 端口拖出连线，连接到各自的下游节点即可。

::: info 💡小贴士
你不需要两条路都连。如果你只关心条件为 True 的情况（比如"只在订单金额大于 1000 时发通知"），只从 **True** 端口连线即可，False 端口不连线的分支会直接结束。
:::

### 4. 动态变量 (inputs)

和 Database 节点一样，If 节点也支持通过 **inputs** 配置动态变量。通过连线将上游数据接入节点后，变量会自动注入到表达式的执行环境中，你可以在表达式中直接使用变量名。

---

## 💡 业务场景示例

### 场景一：VIP 用户专属优惠

> 查询用户信息后，根据会员等级走不同的优惠策略。

**流程搭建步骤：**

1. **Request 节点** → 接收用户 ID
2. **Database 节点** → 查询用户信息 `SELECT vip_level FROM users WHERE id = ${userId}`
3. **If 节点** → 条件：`vipLevel >= 2`
4. **True 分支** → Database 节点执行 VIP 折扣 SQL
5. **False 分支** → Database 节点执行普通价格 SQL
6. 两条分支各自连接到 **Response 节点** 返回结果

### 场景二：数据完整性检查

> 提交表单前，检查关键字段是否非空。

**流程搭建步骤：**

1. **Request 节点** (POST) → 接收表单 Body
2. **If 节点** → 条件：`email !== null && email !== ''`
3. **True 分支** → 继续后续处理（Database 写入）
4. **False 分支** → Response 节点返回 `{ "error": "邮箱不能为空" }`，状态码 `400`

---

## 🔌 下游节点如何取值

If 节点本身**不产生数据输出**。它的作用是"分流"——决定数据走向哪条路径。下游节点需要引用的数据应该来自 If 节点的上游（如 Request、Database 等节点）。

::: info 💡小贴士
如果 True 分支和 False 分支最终汇合到同一个节点（例如共享的 Response），那个汇合节点可以引用 If 之前任何上游节点的数据。
:::

---

## 🛠️ 高级指引

::: details 查看该节点对应的底层 DSL 结构 (JSON)

If 节点在 Flow DSL 中的完整结构如下：

```json
{
  "id": "if_1772498001234_1",
  "type": "if",
  "label": "VIP 判断",
  "ports": [
    { "id": "in", "group": "absolute-in-solid" },
    { "id": "true", "group": "absolute-out-solid" },
    { "id": "false", "group": "absolute-out-hollow" }
  ],
  "data": {
    "condition": "vipLevel >= 2",
    "language": "JavaScript",
    "inputs": {
      "vipLevel": {
        "extractPath": "$.database_xxx.result.vip_level"
      }
    }
  }
}
```

**关键字段说明：**

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `type` | String | 固定为 `"if"`，引擎路由到 `IfStepExecutor` |
| `data.condition` | String | 条件表达式（后端通过 `@JsonAlias("condition")` 映射到 `expression` 字段） |
| `data.language` | String | 表达式语言：`JavaScript` / `Aviator` / `SpEL` |
| `data.inputs` | Object | 输入变量映射，Key 为表达式中的变量名 |
| `ports` | Array | 固定 3 个端口：`in`(输入)、`true`(成立)、`false`(不成立) |

:::

::: details 查看运行时执行机制 (面向后端开发者)

**`IfStepExecutor` 的执行链路：**

1. **参数准备** — 调用 `prepareInputs()` 从上下文提取 inputs 配置的变量，构建为 `Map<String, Object>`。

2. **表达式求值** — 通过 `ExpressionEvaluatorFactory.getEvaluator(language)` 获取对应语言的求值引擎（GraalJS / Aviator / SpEL），执行 `expression`。

3. **真值判断** — 调用 `toBoolean(evalResult)` 进行严格的真值转换：

   - `Boolean` → 直接使用
   - `Number` → 0 为 false，非 0 为 true
   - `String` → `"true"/"false"` 按字面值；其他非空为 true
   - `Collection` / `Map` / `Array` → 空集为 false
   - `null` → false

4. **端口路由** — 返回 `PortNames.TRUE` 或 `PortNames.FALSE`，引擎据此选择下游分支。

**异常类型：**

- `EXPRESSION_EVAL_ERROR`：表达式语法错误或运行时异常

:::
