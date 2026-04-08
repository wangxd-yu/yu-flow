---
title: 系统方法 (SystemMethod)
outline: deep
---

# 系统方法 (SystemMethod)

**SystemMethod 节点是流程中的"工具箱"**。它让你直接调用系统预置的各种工具方法——日期格式化、加密解密、ID 生成、字符串处理等等——无需编写任何代码，只需从下拉列表中选择方法、连上参数、即可得到结果。

---

## 🎨 画布配置指南

将 SystemMethod 节点从左侧「调用节点」分类拖入画布后，你会看到一个紫色主题的卡片。

### 1. 选择系统方法

点击节点卡片上的 **方法选择器**（下拉菜单），从系统预置的方法库中选取一个方法。选择后：

- 节点标题会自动更新为方法名称
- 节点左侧会根据该方法所需的**参数列表**自动生成对应的输入端口
- 每个端口对应一个参数，名称与参数名一致

::: tip 操作提示
系统方法由管理员在「系统宏管理」中维护。如果你找不到需要的方法，请联系管理员添加新的系统方法。
:::

### 2. 连接参数

选择方法后，节点左侧会出现该方法需要的所有参数端口。你需要将上游节点的输出连线到每个参数端口。

**举例：** 选择 `DATE_FORMAT` (日期格式化) 方法后，节点会出现两个输入端口：

- `date` — 需要格式化的日期值
- `format` — 格式化模式（如 `yyyy-MM-dd`）

只需将上游提供日期和格式的节点分别连线到这两个端口。

### 3. 查看方法信息

选择方法后，节点卡片上会显示：

- **方法名称** — 如"日期格式化"
- **参数列表** — 如 `date, format`
- **底层表达式** — 方法实际执行的 SpEL 表达式（仅展示，无需编辑）

### 端口连线说明

- 🟣 **参数端口** (左侧)：按方法定义动态生成，每个参数一个输入端口
- 🟢 **Out** (右侧)：方法执行完毕后，结果从此端口输出

---

## 🔌 下游节点如何取值

SystemMethod 节点执行完成后，方法返回值会存入上下文：

```text
$.result    → 方法的返回值（类型取决于方法本身）
```

::: info 💡小贴士
不同方法返回不同类型——UUID 生成方法返回字符串，数学计算方法返回数字。引用时注意目标节点对类型的要求。
:::

---

## 💡 业务场景示例

### 场景一：生成唯一订单号

> 创建订单时，需要一个唯一的订单编号。

**流程搭建步骤：**

1. **Request 节点** (POST) → 接收订单数据
2. **SystemMethod 节点** → 选择 `UUID_GENERATE`（生成 UUID）
3. **Database 节点** (INSERT) → `INSERT INTO orders (order_no, ...) VALUES (${orderNo}, ...)`，将 SystemMethod 的输出连线到 `orderNo` 变量
4. **Response 节点** → 返回新创建的订单

### 场景二：密码加密后存储

> 用户注册时，先对密码做 MD5 加密再入库。

**流程搭建步骤：**

1. **Request 节点** (POST) → 接收 `{ "username": "...", "password": "..." }`
2. **SystemMethod 节点** → 选择 `MD5_ENCODE`，参数端口 `input` 连线自 Request 的 Body（password 字段）
3. **Database 节点** (INSERT) → `INSERT INTO users (username, password) VALUES (${username}, ${encryptedPwd})`
4. **Response 节点** → 返回注册成功

---

## 🛠️ 高级指引

::: details 查看该节点对应的底层 DSL 结构 (JSON)

SystemMethod 节点在 Flow DSL 中的结构：

```json
{
  "id": "systemMethod_1772498001234_1",
  "type": "systemMethod",
  "label": "日期格式化",
  "ports": [
    { "id": "in:var:date", "group": "absolute-in-solid" },
    { "id": "in:var:format", "group": "absolute-in-solid" },
    { "id": "out", "group": "absolute-out-solid" }
  ],
  "data": {
    "methodCode": "DATE_FORMAT",
    "inputs": {
      "date":   { "extractPath": "$.request_xxx.body.createTime" },
      "format": { "extractPath": "$.request_xxx.body.dateFormat" }
    }
  }
}
```

**关键字段说明：**

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `type` | String | 固定为 `"systemMethod"` |
| `data.methodCode` | String | 方法编码，用于从系统宏注册表 (`flow_sys_macro`) 中查找预设方法 |
| `data.inputs` | Object | 参数映射，Key 为参数名，会注册为 SpEL 变量 `#key` |
| `ports` | Array | 输入端口按 `in:var:{参数名}` 命名，固定一个 `out` 输出端口 |

> **安全设计**：前端仅存储 `methodCode` 和 `inputs`，不存储真实的 SpEL `expression`。后端通过 `SysMacroCacheManager` 从数据库获取预编译的表达式执行，防止客户端注入。

:::

::: details 查看运行时执行机制 (面向后端开发者)

**`SystemMethodStepExecutor` 的执行链路：**

1. **宏查找** — 通过 `SysMacroCacheManager.getMacro(methodCode)` 获取 `CachedMacro` 实例（含预编译的 SpEL 表达式）。未找到或已停用抛出 `SYSTEM_METHOD_NOT_FOUND`。

2. **参数提取** — 调用 `prepareInputs()` 按 `inputs` 配置的 `extractPath` 从上下文提取参数值。

3. **SpEL 上下文构建** —
   - 注册按位置的变量：`#p0`, `#p1`, ...
   - 同时注册按名称的变量：`#date`, `#format`, ...（兼容两种引用方式）
   - 注入 Spring `BeanFactory`，支持表达式中 `@bean.method()` 调用

4. **表达式执行** — 使用预编译的 `Expression.getValue(spelContext)` 执行。

5. **结果写入** — `{ "out": result }` 存入上下文，并设置 `context.setOutput(result)`。

**系统宏注册表字段 (`flow_sys_macro`)：**

| 字段 | 说明 |
| --- | --- |
| `macro_code` | 唯一编码，对应 `methodCode` |
| `macro_type` | `FUNCTION`（方法）或 `VARIABLE`（变量） |
| `expression` | 真实的 SpEL 表达式 |
| `macro_params` | 逗号分隔的参数列表 |
| `status` | 1=启用, 0=停用 |

**异常类型：**

- `SYSTEM_METHOD_NOT_FOUND`：方法编码不存在或已停用
- `SYSTEM_METHOD_EXEC_ERROR`：SpEL 表达式执行失败

:::
