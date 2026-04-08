---
title: HTTP 响应 (Response)
outline: deep
---

# HTTP 响应 (Response)

**Response 节点是每一条业务流程的"终点站"**。它负责将流程的最终处理结果包装成一个完整的 HTTP 响应返回给调用方——你可以精确控制返回的状态码 (Status Code)、响应头 (Headers) 和响应体 (Body)。当流程执行到 Response 节点时，整条流程即宣告结束。

---

## 🎨 画布配置指南

将 Response 节点从左侧「基础节点」分类拖入画布后，你会看到一个橙色主题的卡片，右侧带有一条醒目的橙色竖条——这是"终止节点"的视觉标识。卡片从上到下分为四个区域。

### 1. Status Code (状态码)

卡片顶部第一行显示 **Status Code** 输入框，默认值为 `200`。

| 常用状态码 | 含义 | 使用场景 |
| --- | --- | --- |
| **200** | 成功 | 正常返回（默认） |
| **201** | 已创建 | 新增数据成功后返回 |
| **400** | 请求错误 | 参数校验失败、格式错误 |
| **403** | 禁止访问 | 权限不足 |
| **404** | 未找到 | 资源不存在 |
| **500** | 服务器错误 | 系统异常 |

::: tip 操作提示
状态码支持 100~599 范围的整数。你也可以通过 `${}` 变量引用来动态设置状态码——例如上游的 If 节点判断后决定返回 200 还是 400。
:::

### 2. Variables (动态变量)

在 Status Code 下方是 **Variables** 区域。通过点击 `+` 按钮添加变量，每个变量代表一个从上游节点注入的数据源。

- 添加变量后，节点左侧会出现**输入端口**
- 将上游节点的输出端口连线到此端口即可
- 变量可以在 Body 表达式中通过 `${变量名}` 引用

### 3. Headers (响应头)

在 Variables 下方是 **Headers** 区域。点击右侧的 `+` 按钮，可以添加自定义响应头键值对。

**常用响应头示例：**

| Key | Value | 说明 |
| --- | --- | --- |
| `Content-Type` | `application/json` | 指定响应内容类型 |
| `X-Request-Id` | `${requestId}` | 动态注入请求追踪 ID |
| `Cache-Control` | `no-cache` | 禁用缓存 |

::: tip 操作提示
Header 的 Value 支持 `${变量名}` 占位符，引擎会在执行时自动替换为上游传入的实际值。
:::

### 4. Body (响应体)

卡片最底部是 **Body** 代码编辑器，用于定义 API 的返回数据。Body 支持以下几种写法：

**直接引用上游整个对象：**

```text
${result}
```

**返回固定 JSON：**

```json
{ "message": "操作成功", "code": 0 }
```

**混合模式（固定结构 + 动态值）：**

```json
{
  "success": true,
  "data": "${queryResult}",
  "timestamp": "${currentTime}"
}
```

::: warning 注意事项
Response 节点是**终止节点**——它没有输出端口，不能再连接到任何下游节点。一条流程可以有**多个** Response 节点（在不同的分支中），但每次执行只会到达其中一个。
:::

---

## 💡 业务场景示例

### 场景一：标准 CRUD 成功响应

> 用户创建订单后，返回统一的成功格式。

**流程搭建步骤：**

1. **Request 节点** (POST) → 接收订单数据
2. **Database 节点** (INSERT) → 写入订单，输出新主键 ID
3. **Response 节点** 配置：
   - Status Code：`201`
   - Variables：添加 `orderId`，连线自 Database 节点
   - Body：`{ "code": 0, "message": "创建成功", "data": { "id": "${orderId}" } }`

### 场景二：带条件的错误响应

> 查询用户不存在时返回 404。

**流程搭建步骤：**

1. **Request 节点** (GET) → 接收 userId
2. **Database 节点** (SELECT, OBJECT) → 查用户
3. **If 节点** → 条件：`user == null`
4. **True 分支** → **Response 节点**：Status `404`，Body `{ "error": "用户不存在" }`
5. **False 分支** → **Response 节点**：Status `200`，Body `${user}`

### 场景三：Webhook 透传

> 第三方回调需要返回纯文本 `success`，不需要外层包装。

**Response 节点配置：**

- Status Code：`200`
- Headers：添加 `Content-Type` = `text/plain`
- Body：`success`

::: tip 核心价值
Response 节点最大的优势是**绕过系统的全局响应包装**。普通 Controller 返回值会被 `@RestControllerAdvice` 统一包装为 `{ code, data, message }` 格式，而 Response 节点产生的 `ResponseEntity` 会直接透传给客户端，非常适合对接外部系统（如支付回调、Webhook）。
:::

---

## 🛠️ 高级指引

::: details 查看该节点对应的底层 DSL 结构 (JSON)

Response 节点在 Flow DSL 中的完整结构：

```json
{
  "id": "response_1772498001234_1",
  "type": "response",
  "label": "返回订单创建结果",
  "ports": [
    { "id": "in:var:var_abc123", "group": "absolute-in-solid" }
  ],
  "data": {
    "status": 201,
    "headers": {
      "X-Request-Id": "${requestId}"
    },
    "body": "{ \"code\": 0, \"data\": \"${orderId}\" }",
    "inputs": {
      "orderId": {
        "id": "var_abc123",
        "extractPath": "$.database_xxx.result"
      }
    }
  }
}
```

**关键字段说明：**

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `type` | String | 固定为 `"response"`，引擎路由到 `ResponseStepExecutor` |
| `data.status` | Integer/String | HTTP 状态码，支持 `${}` 动态引用 |
| `data.headers` | Object | 响应头键值对，Value 支持 `${}` 模板渲染 |
| `data.body` | String/Object | 响应体，支持完整 `${}` 引用或对象模板 |
| `ports` | Array | 仅有输入端口，**无输出端口**（终止节点） |

:::

::: details 查看运行时执行机制 (面向后端开发者)

**`ResponseStepExecutor` 的执行链路：**

1. **局部上下文** — 调用 `prepareInputs()` 提取 inputs 中配置的变量到 `localVariables`。

2. **Status 解析** — `resolveStatus()` 支持三种输入：
   - `Integer` → 直接使用
   - `"${xxx}"` → 从 localVariables 中提取
   - 纯数字字符串 → `Integer.parseInt()`
   - 解析失败 → 默认 200

3. **Headers 模板渲染** — 逐个遍历 headers Map，对每个 Value 执行 `${}` 变量替换。

4. **Body 模板渲染** — `resolveBody()` 支持：
   - `"${xxx}"` 完全匹配 → 返回 JSONPath 提取的原始对象（保留类型）
   - 含 `${}` 片段 → 执行字符串模板替换
   - `Map` 类型 → 递归解析每个 Value 中的 `${}` 引用
   - 纯字符串 → 原样返回

5. **结果封装** — 构造 `ResponseResult(status, headers, body)` 并写入上下文。

6. **流程终止** — `execute()` 返回 `null`，引擎识别为流程结束。

**全局响应包装绕过机制：**

当 `FlowEngine.execute()` 检测到最终产物是 `ResponseResult` 时，它会将结果转换为 Spring `ResponseEntity`，直接带上自定义的 Status Code 和 Headers 返回。由于 `ResponseEntity` 是 Spring MVC 原生类型，`@RestControllerAdvice` 不会对其进行二次包装。

:::
