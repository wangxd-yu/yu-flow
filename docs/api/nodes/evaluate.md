---
title: 表达式计算 (Evaluate)
outline: deep
---

# 表达式计算 (Evaluate)

**Evaluate 节点是流程中的"计算器"**。它接收上游传来的数据，通过你编写的表达式进行加工、转换或计算，然后将计算结果从输出端口传递给下游节点。无论是字符串拼接、数学运算，还是数据格式转换，都可以用它来完成。

---

## 🎨 画布配置指南

将 Evaluate 节点从左侧「逻辑节点」分类拖入画布后，你会看到一个蓝色卡片，内嵌一个代码编辑器。

### 1. 选择表达式语言

在右侧属性面板中，选择 **表达式语言**：

| 语言 | 适用场景 | 示例 |
| --- | --- | --- |
| **Aviator** | 高性能的表达式计算 | `string.substring(name, 0, 5)` |
| **SpEL** | 需要调用 Spring Bean 或静态方法时 | `#name.toUpperCase()` |
| **JavaScript** | 复杂逻辑、JSON 操作 | `name.toUpperCase() + '_' + id` |

### 2. 编写计算表达式

在节点卡片内的 **代码编辑器** 中（或右侧属性面板的表达式区域），编写你的计算逻辑。表达式的最终返回值就是该节点的输出结果。

**常用写法：**

```javascript
// 字符串拼接
firstName + ' ' + lastName

// 数学计算
price * quantity * (1 - discount)

// 条件赋值（三元表达式）
score >= 60 ? '及格' : '不及格'

// JSON 对象构造
({ fullName: firstName + ' ' + lastName, total: price * quantity })
```

### 3. 动态变量 (inputs)

Evaluate 节点同样支持 **inputs** 动态变量。将上游节点的输出端口连线到 Evaluate 节点，变量会自动注入到表达式环境中。

例如：上游 Database 节点查出 `{ "price": 100, "quantity": 3 }`，你在 inputs 中配置了 `price` 和 `quantity` 两个变量，表达式中就可以直接写 `price * quantity`。

### 端口连线说明

- 🟢 **Out**：位于节点右侧。表达式计算完成后，结果从此端口输出到下游节点

---

## 🔌 下游节点如何取值

Evaluate 节点计算完成后，结果会存入上下文。下游节点引用方式：

```text
$.result    → 表达式的计算结果（任意类型：字符串、数字、对象、数组等）
```

::: info 💡小贴士
Evaluate 节点的输出类型取决于你写的表达式返回什么。如果返回的是一个对象，下游节点可以用 `$.result.fieldName` 取其中的字段。
:::

---

## 💡 业务场景示例

### 场景一：计算订单总价

> 从上游拿到单价和数量，计算含税总价。

**流程搭建步骤：**

1. **Request 节点** (POST) → 接收 `{ "price": 100, "quantity": 3, "taxRate": 0.06 }`
2. **Evaluate 节点** → 表达式：`price * quantity * (1 + taxRate)`
3. **Response 节点** → 返回 `{ "totalPrice": ${result} }`

### 场景二：用户名格式化

> 将姓和名拼接为完整姓名，并转为大写。

**流程搭建步骤：**

1. **Database 节点** → 查询 `SELECT first_name, last_name FROM users WHERE id = ${userId}`
2. **Evaluate 节点** → 表达式（JavaScript）：`(firstName + ' ' + lastName).toUpperCase()`
3. **Response 节点** → 返回格式化后的结果

---

## 🛠️ 高级指引

::: details 查看该节点对应的底层 DSL 结构 (JSON)

Evaluate 节点在 Flow DSL 中的完整结构：

```json
{
  "id": "evaluate_1772498001234_1",
  "type": "evaluate",
  "label": "计算总价",
  "ports": [
    { "id": "out", "group": "absolute-out-solid" }
  ],
  "data": {
    "expression": "price * quantity * (1 + taxRate)",
    "language": "JavaScript",
    "inputs": {
      "price":    { "extractPath": "$.request_xxx.body.price" },
      "quantity": { "extractPath": "$.request_xxx.body.quantity" },
      "taxRate":  { "extractPath": "$.request_xxx.body.taxRate" }
    }
  }
}
```

**关键字段说明：**

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `type` | String | 固定为 `"evaluate"`，引擎路由到 `EvaluateStepExecutor` |
| `data.expression` | String | 计算表达式 |
| `data.language` | String | `JavaScript` / `Aviator` / `SpEL` |
| `data.inputs` | Object | 输入变量映射 |

:::

::: details 查看运行时执行机制 (面向后端开发者)

**`EvaluateStepExecutor` 的执行链路：**

1. **参数准备** — 调用 `prepareInputs()` 提取 inputs 中的变量。

2. **表达式求值** — 通过 `ExpressionEvaluatorFactory` 获取对应语言引擎，执行 `expression`。

3. **结果写入** — 将计算结果同时存入 `result` 和 `out` 两个 Key：

```java
Map<String, Object> nodeResult = new HashMap<>();
nodeResult.put("result", evalResult);
nodeResult.put("out", evalResult);
context.setVar(step.getId(), nodeResult);
```

4. **端口路由** — 固定返回 `"out"` 端口。

**下游引用路径：**

```text
$.evaluate_xxx.result  → 计算结果
$.evaluate_xxx.out     → 同上（别名）
```

**异常类型：**

- `EXPRESSION_EVAL_ERROR`：表达式语法错误或运行时异常

:::
