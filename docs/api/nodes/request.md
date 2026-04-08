---
title: 请求入口 (Request)
outline: deep
---

# 请求入口 (Request)

**Request 节点是每一条业务流程的"大门"**。当外部（比如前端页面、其他系统或定时任务）对这条 API 发起调用时，Request 节点会将收到的所有参数自动拆分为三类——请求头 (Headers)、查询参数 (Params) 和请求体 (Body)——并从各自对应的端口输出，供后续节点按需取用。

简单理解：**它不做任何业务处理，它只负责"接收"和"分发"原始数据。**

---

## 🎨 画布配置指南

将 Request 节点从左侧节点面板拖入画布后，你会看到它默认显示为一个绿色的卡片，右侧有 `Headers` 和 `Params` 两个输出端口。

### 1. 选择请求方法 (Method)

点击节点右上角的 **方法标签**（默认显示 `GET`），会弹出下拉菜单，支持以下 5 种 HTTP 方法：

| 方法 | 适用场景 | 是否有 Body 端口 |
|------|---------|:---:|
| **GET** | 查询数据（最常用） | ❌ |
| **POST** | 提交/创建数据 | ✅ |
| **PUT** | 整体替换/更新数据 | ✅ |
| **DELETE** | 删除数据 | ❌ |
| **PATCH** | 局部更新数据 | ✅ |

::: tip 操作提示
当你将方法切换为 `POST`、`PUT` 或 `PATCH` 时，节点会自动增加第三个输出端口 **Body**，同时节点高度也会随之变大。切回 `GET` 或 `DELETE` 时，Body 端口会自动移除，已连接到 Body 端口的连线也会被清理。
:::

### 2. 理解三个输出端口

这是 Request 节点最核心的设计——**根据数据来源进行物理隔离**，让下游节点精准取值：

| 端口 | 含义 | 举例 |
|------|------|------|
| **Headers** | HTTP 请求头中的数据 | `Authorization: Bearer xxx`、`Content-Type: application/json` |
| **Params** | URL 上携带的查询参数 | `/api/users?name=张三&age=25` 中的 `name` 和 `age` |
| **Body** | 请求体中的 JSON 数据 | `POST /api/users` 时传的 `{ "name": "张三", "email": "..." }` |

**如何连线？** 只需将你需要的端口拖出一条线，连接到下游节点的输入端口即可。例如：

- 你想用用户传来的查询参数做条件判断 → 从 **Params** 端口连线到 **If 节点**
- 你想把 POST 请求体中的数据存入数据库 → 从 **Body** 端口连线到 **Database 节点**
- 你想根据请求头中的 Token 做鉴权 → 从 **Headers** 端口连线到 **Evaluate 节点**

::: warning 注意事项
一个流程中 **只能放置一个** Request 节点。如果你尝试拖入第二个，画布将自动阻止。此外，Request 节点必须处于流程的起始位置——不允许有任何连线指向它。
:::

### 3. 配置参数校验（可选）

选中 Request 节点后，在右侧属性面板的底部，你会看到一个 **"参数校验规则 (JSON, 可选)"** 的文本框。这是一项高级功能，允许你在数据进入流程的第一关就进行前置校验——如果参数不合规，流程直接报错返回，不会执行后续任何节点。

在文本框中填入校验策略，格式为 JSON，示例：

```json
{
  "username": {
    "required": true,
    "type": "length",
    "min": 2,
    "max": 20,
    "message": "用户名必须为 2~20 个字符"
  },
  "email": {
    "required": true,
    "type": "email",
    "message": "邮箱格式不正确"
  }
}
```

**支持的校验类型一览：**

| `type` 值 | 校验逻辑 | 需要额外填写 |
|-----------|---------|------------|
| *(不填)* | 仅检查是否必填 (`required: true`) | — |
| `phone` | 中国大陆手机号格式 (`1`开头 11 位) | — |
| `email` | 标准邮箱格式 | — |
| `regex` | 自定义正则表达式匹配 | `pattern`: 正则字符串 |
| `range` | 数值必须在指定区间内 | `min` / `max`: 数字 |
| `length` | 字符串长度必须在指定区间内 | `min` / `max`: 长度值 |

::: tip 操作提示
`message` 字段是自定义错误提示，如果你不写，系统会自动生成类似 *"username 不能为空"* 的默认提示。建议在面向终端用户的 API 场景中，填写更友好的中文错误信息。
:::

---

## 💡 业务场景示例

### 场景一：查询订单详情

> 前端通过 `GET /api/flow/order/detail?orderId=20260301001` 调用此流程。

**流程搭建步骤：**

1. 拖入 **Request 节点**，方法保持 `GET`
2. 从 **Params** 端口引出连线，连接到 **Database 节点**
3. Database 节点中编写 SQL：`SELECT * FROM orders WHERE id = #{orderId}`
4. Database 节点的结果连接到 **Response 节点**，返回查询结果

这个场景中，`orderId` 这个查询参数会通过 Params 端口流入 Database 节点，成为 SQL 查询的条件。

### 场景二：创建用户（带参数校验）

> 前端通过 `POST /api/flow/user/create` 提交用户注册表单。

**流程搭建步骤：**

1. 拖入 **Request 节点**，切换方法为 `POST`
2. 在属性面板中配置校验规则，确保 `username` 必填、`email` 格式正确
3. 从 **Body** 端口引出连线，连接到 **Database 节点**
4. Database 节点执行 `INSERT INTO users (name, email) VALUES (#{name}, #{email})`
5. 连接 **Response 节点**，返回 `{ "message": "创建成功" }`

如果用户提交的表单中 `email` 字段值不是合法邮箱，流程会在 Request 节点阶段就直接返回错误，不会执行数据库写入。

### 场景三：根据请求头做灰度路由

> 调用方在 `Headers` 中传了一个自定义头 `X-Gray-Version: v2`，需要根据这个值走不同的处理分支。

**流程搭建步骤：**

1. 拖入 **Request 节点**，方法为 `GET`
2. 从 **Headers** 端口引出连线，连接到 **If 条件节点**
3. If 节点表达式填写：`grayVersion == 'v2'`
4. `True` 分支走 V2 新逻辑，`False` 分支走原始逻辑

---

## 🔌 下游节点如何取值

当你用一根线将 Request 节点的某个端口（比如 `Params`）连接到下游节点后，下游节点就可以直接通过字段名来引用参数值。

**举例：** 如果请求 URL 是 `/api/xxx?name=张三&age=25`，下游节点中引用 `$.name` 即可得到 `"张三"`，引用 `$.age` 即可得到 `"25"`。

::: info 💡小贴士
你不需要手动编写引用路径。当你在画布上连线时，系统会自动为下游节点补全正确的取值路径。只有当你需要跨节点引用（引用非直接连线上游的数据）时，才需要手工填写完整路径，格式为 `$.{节点ID}.{端口名}.{字段名}`。
:::

---

## 🛠️ 高级指引

::: details 查看该节点对应的底层 DSL 结构 (JSON)

Request 节点在 Flow DSL 中的完整结构如下。通常您无需直接编辑此 JSON，可视化画布会自动生成。

**最小配置（GET 请求）：**

```json
{
  "id": "request_1772497969851_2",
  "type": "request",
  "label": "Request",
  "ports": [
    { "id": "headers", "group": "absolute-out-solid" },
    { "id": "params",  "group": "absolute-out-solid" }
  ],
  "data": {
    "method": "GET"
  }
}
```

**完整配置（POST 请求 + 参数校验）：**

```json
{
  "id": "request_1772497969851_2",
  "type": "request",
  "label": "创建用户",
  "ports": [
    { "id": "headers", "group": "absolute-out-solid" },
    { "id": "params",  "group": "absolute-out-solid" },
    { "id": "body",    "group": "absolute-out-solid" }
  ],
  "data": {
    "method": "POST",
    "validations": {
      "username": {
        "required": true,
        "type": "length",
        "min": 2,
        "max": 20,
        "message": "用户名必须为 2~20 个字符"
      },
      "email": {
        "required": true,
        "type": "email",
        "message": "邮箱格式不正确"
      }
    }
  }
}
```

**关键字段说明：**

| 字段 | 类型 | 说明 |
|------|------|------|
| `id` | String | 节点唯一标识，画布自动生成 |
| `type` | String | 固定为 `"request"`，引擎通过此值路由到 `RequestStepExecutor` |
| `data.method` | String | HTTP 方法，可选 `GET` / `POST` / `PUT` / `DELETE` / `PATCH` |
| `data.validations` | Object | 参数校验规则映射，Key 为参数名，Value 为校验规则对象 |
| `ports` | Array | 输出端口列表，GET/DELETE 有 2 个，POST/PUT/PATCH 有 3 个 |

:::

::: details 查看运行时执行机制 (面向后端开发者)

**`RequestStepExecutor` 的执行链路：**

1. **数据提取** — 从 `ExecutionContext` 全局变量池中提取 `headers`、`params`、`body` 三个 Map。如果某部分不存在，安全降级为空 Map。

2. **参数校验** — 将 `params` 与 `body` 合并后，交给 `ParamValidator` 按 `validations` 配置逐一校验。支持 5 种校验类型（`phone`、`email`、`regex`、`range`、`length`）。校验失败抛出 `FlowException(VALIDATION_ERROR)`。

3. **结果写入** — 将三部分数据以结构化 Map 存入上下文：

```java
context.setVar(step.getId(), {
    "headers": { ... },
    "params":  { ... },
    "body":    { ... }
});
```

4. **端口路由** — 按 `headers → params → body → out` 的优先级扫描 `next` 映射，返回第一个有连线的端口名称。未匹配时默认返回 `body`。

**下游引用路径格式：**

```
$.request_xxx.headers      → 全部请求头
$.request_xxx.params.id    → 提取 params 中的 id 字段
$.request_xxx.body.name    → 提取 body 中的 name 字段
```

在画布模式下，`FlowParser` 会根据 edge 连线自动补全 `extractPath` 的绝对路径前缀，开发者通常只需写 `$.fieldName` 的相对路径。

**单例校验（FlowParser 层）：**
- 一个流程中只允许一个 `request` 节点
- 不允许任何边指向 Request 节点（它必须是入口）
- 违反以上任一条件将抛出 `REQUEST_VALIDATION_ERROR`

:::
