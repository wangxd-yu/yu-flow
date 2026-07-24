---
title: Flow DSL 协议规范（生成指南）
outline: deep
---

# Flow DSL 协议规范（生成指南）

> **用途**：在不依赖完整源码仓库的前提下，人工或大模型可据此生成可运行的流程编排 JSON。  
> **权威格式**：画布格式（`nodes` + `edges`）。引擎运行时会经 `FlowParser` 转为内部 `steps` + `next`。

---

## 1. 根结构

```json
{
  "id": "optional-flow-id",
  "version": "1.0",
  "args": {},
  "nodes": [ /* 见 §3 */ ],
  "edges": [ /* 见 §2 */ ]
}
```

| 字段 | 必填 | 说明 |
| --- | --- | --- |
| `nodes` | ✅ | 节点列表 |
| `edges` | ✅ | 连线列表（控制流 + 数据流） |
| `id` / `version` | | 可选元数据 |
| `args` | | 流程级默认入参（少用；优先用 Request 端口） |
| `errors` | | 可选错误码表 `{ "CODE": { "code": 400, "message": "..." } }` |

**生成建议**：只写画布格式；`x`/`y`/`width`/`height`/`label`/`ports` 可省略（导入编辑器时会补全）。引擎忽略视图坐标。

---

## 2. 边（edges）

```json
{
  "source": { "cell": "<源节点id>", "port": "<源端口id>" },
  "target": { "cell": "<目标节点id>", "port": "<目标端口id>" }
}
```

| 规则 | 说明 |
| --- | --- |
| 省略 `source.port` | 默认为 `out` |
| 省略 `target.port` | 默认为 `in` |
| 同一源端口多条边 | 并行扇出（`next[port]` 变为目标 id 数组） |
| 入口节点 | `request` / `schedule` / `service` **不能**作为任何边的 `target` |

### 2.1 常用端口

| 方向 | 端口 ID | 含义 |
| --- | --- | --- |
| 出 | `out` | 通用成功出口 |
| 出 | `true` / `false` | If 分支 |
| 出 | `case_<id>` / `default` | Switch 分支 |
| 出 | `success` / `fail` | HttpRequest |
| 出 | `headers` / `params` / `body` | Request 拆分出口 |
| 出 | `item` / `done` | ForEach |
| 出 | `item` | For（并发分发） |
| 出 | `list` / `finish` | Collect |
| 入 | `in:payload` | 数据总入口（多数节点） |
| 入 | `in` | 控制流/列表入口（If、Delay、Parallel、For/ForEach） |
| 入 | `in:var:<varId>` | 绑定到 `data.inputs` 里带相同 `id` 的变量 |
| 入 | `in:arg:<paramName>` | SystemMethod 参数口（键名 = 参数名） |

### 2.2 连线如何写入 `inputs`（解析期）

画到特殊入端口时，解析器会自动补 `extractPath`：

| 目标端口 | 行为 |
| --- | --- |
| `in:payload` | → `inputs.payload.extractPath = $.源节点.源端口` |
| `in:var:<id>` | → 匹配 `inputs[*].id == id` 的项，写 `extractPath` |
| `in:arg:<name>` | → `inputs[name].extractPath` |
| `in` → For / ForEach | → `inputs.list` |

也可**不连线**，直接在 `data.inputs` 里写死路径（见 §4）。

---

## 3. 节点（nodes）通用字段

```json
{
  "id": "eval_user_age",
  "type": "evaluate",
  "data": {
    "/* 类型专属字段 */": "...",
    "inputs": {},
    "language": "JavaScript"
  }
}
```

| 字段 | 说明 |
| --- | --- |
| `id` | **全局唯一**。下游用 `$.该id.out` 引用。建议语义化：`query_user`、`check_vip` |
| `type` | 见节点目录；大小写敏感 |
| `data` | 业务配置；引擎会 flatten 到 Step 根级 |
| `data.inputs` | 变量装载（§4） |
| `data.language` | 表达式语言（If / Switch / Evaluate） |

**启动节点**：引擎取第一个 `request` | `schedule` | `service`。  
三种入口**互斥**（同一流程只能有一种）。  
**API 流程推荐**：`request` → … → `response`。  
**内部服务编排**：`service` → …（无网关/定时；由其他流程的 `api` 节点以 `targetType=service` 调用）。

---

## 4. `inputs` 与数据引用

### 4.1 `inputs` 写法

```json
"inputs": {
  "age": "$.request_1.params.age",
  "name": { "id": "var_abc", "extractPath": "$.query_user.out.name" },
  "fixed": "hello"
}
```

| 值形态 | 含义 |
| --- | --- |
| `"$.a.b"` | JSONPath，从执行上下文取值 |
| `{ "id": "...", "extractPath": "$...." }` | 完整形态；`id` 供 `in:var:` 连线 |
| 非 `$` 开头的字符串 | **字面量** |
| 其他对象 | 原样放入 |

解析后，键名成为表达式里的局部变量（如 Aviator/JS 里的 `age`）。

### 4.2 上下文路径约定（重要）

| 写法 | 含义 |
| --- | --- |
| `$.<nodeId>.out` | **首选**：多数节点输出 |
| `$.<requestId>.headers` / `.params` / `.body` | Request 三出口对应数据 |
| `$.<httpId>.out` 或结果字段 | HttpRequest：`status`/`body`/`headers`/`timeMs`（以执行器写入为准，常用挂在节点 id 下） |
| `$.error` | ErrorHandler 场景下的异常信息 |
| `$.schedule.*` | Schedule：`taskName`/`cron`/`triggerTime` |
| `$.service.*` | Service 入口：`serviceName`/`serviceId`/`input`/`triggerTime` |

遗留路径 `$.nodeId.result`：仅部分旧节点双写；**新生成请一律用 `.out`**。

### 4.3 表达式语言 `language`

| 值 | 引擎 | 备注 |
| --- | --- | --- |
| `JavaScript` / `javascript` / `js` | GraalJS | 前端默认 |
| `Aviator` / `aviator` | Aviator | **引擎默认**（未指定时） |
| `SpEL` / `spel` | SpEL | 变量多用 `#name` |
| `Python` / `python` | GraalPy | |
| `Groovy` / `groovy` | Groovy | |

### 4.4 模板占位（按节点）

| 节点 | 语法 |
| --- | --- |
| Template | `{{varName}}`（对应 `inputs` 键） |
| Response / HttpRequest URL 等 | `${varName}` 或 `${nodeId.path}` |

---

## 5. 节点目录（生成用）

下列为 **V3 画布主推类型**。字段均写在 `data` 下。

### 5.1 `request` — HTTP API 入口

| | |
| --- | --- |
| 入端口 | 无 |
| 出端口 | `headers`, `params`；方法为 POST/PUT/PATCH 时还有 `body` |
| 约束 | 流程内建议唯一；不可作为边的 target |

```json
{
  "id": "req",
  "type": "request",
  "data": {
    "method": "POST",
    "validations": {
      "userId": { "required": true, "type": "regex", "pattern": "^\\d+$", "message": "userId invalid" }
    }
  }
}
```

`validations` 值字段：`required`, `type`(`phone`|`email`|`regex`|`range`), `pattern`, `min`, `max`, `message`。

---

### 5.2 `schedule` — 定时入口

| | |
| --- | --- |
| 出端口 | `out` |
| data | `taskName?`, `cron?`（调度侧也可能外部注入） |

```json
{ "id": "sched", "type": "schedule", "data": {} }
```

---

### 5.2.1 `service` — 内部服务编排入口

| | |
| --- | --- |
| 入端口 | 无 |
| 出端口 | `out` |
| 约束 | 流程内唯一；不可作为边的 target；不可与 `request`/`schedule` 并存 |
| 运行时 | 写入 `$.service.serviceName` / `serviceId` / `input` / `triggerTime` |

```json
{
  "id": "service_1",
  "type": "service",
  "data": {
    "__label": "Service",
    "__contractInputs": [],
    "__contractOutputs": []
  }
}
```

契约在「服务契约」Tab 编辑，持久化为资产字段 `contract`；保存时写入入口节点 `__contractInputs` / `__contractOutputs` 供画布卡片摘要。  
画布仅一个出口 `out`，返回值可为对象（不必为字段拆端口）。  
调用方入参经校验后注入为 `$.service.input`。  
**CALL 执行走已发布快照**；调试/手动可跑草稿。

---

### 5.3 `response` — HTTP 终态

| | |
| --- | --- |
| 入端口 | `in:var:*`（按需） |
| 出端口 | 无 |

```json
{
  "id": "resp",
  "type": "response",
  "data": {
    "status": 200,
    "headers": { "Content-Type": "application/json" },
    "body": "${result}",
    "inputs": {
      "result": { "id": "v1", "extractPath": "$.eval.out" }
    }
  }
}
```

---

### 5.4 `evaluate` — 表达式求值

| | |
| --- | --- |
| 入 | `in:payload` + 动态 `in:var:*` |
| 出 | `out` |

```json
{
  "id": "eval",
  "type": "evaluate",
  "data": {
    "language": "JavaScript",
    "expression": "a + b",
    "inputs": {
      "a": { "id": "va", "extractPath": "$.req.params.x" },
      "b": { "id": "vb", "extractPath": "$.req.params.y" }
    }
  }
}
```

---

### 5.5 `if` — 二分支

| | |
| --- | --- |
| 入 | `in`（及变量口） |
| 出 | `true`, `false` |
| 字段 | `condition` 或 `expression`（同义），`language` |

```json
{
  "id": "gate",
  "type": "if",
  "data": {
    "language": "JavaScript",
    "condition": "age >= 18",
    "inputs": {
      "age": { "extractPath": "$.req.params.age" }
    }
  }
}
```

---

### 5.6 `switch` — 多路值匹配

| | |
| --- | --- |
| 入 | `in:payload` |
| 出 | `case_<id>`…, `default` |
| 语义 | 对 `expression` **求值一次**，与 `cases[].value` **相等**则走对应口；否则 `default` |

```json
{
  "id": "sw",
  "type": "switch",
  "data": {
    "language": "JavaScript",
    "expression": "role",
    "cases": [
      { "id": "c_admin", "name": "Admin", "value": "ADMIN" },
      { "id": "c_user", "name": "User", "value": "USER" }
    ],
    "inputs": {
      "role": { "extractPath": "$.req.params.role" }
    }
  }
}
```

对应边源端口：`case_c_admin`、`case_c_user`、`default`。  
`cases` **必须**为对象数组；引擎不再接受字符串数组。

---

### 5.7 `httpRequest` — 外部 HTTP

| | |
| --- | --- |
| 入 | `in:payload` |
| 出 | `success`, `fail` |
| 结果字段 | `status`, `body`, `headers`, `timeMs`（失败时可能有 `error`） |

```json
{
  "id": "http1",
  "type": "httpRequest",
  "data": {
    "url": "https://api.example.com/users/${userId}",
    "method": "GET",
    "timeout": 30000,
    "retryCount": 0,
    "retryIntervalMs": 1000,
    "successCondition": "status == 200",
    "logEnabled": true,
    "ignoreSsl": true,
    "authType": "bearer",
    "authToken": "${token}",
    "headers": { "Accept": "application/json" },
    "params": {},
    "body": null,
    "inputs": {
      "userId": { "extractPath": "$.req.params.id" },
      "token": { "extractPath": "$.req.headers.Authorization" }
    }
  }
}
```

`authType`：`none` | `bearer` | `basic` | `apiKey`。  
API Key：`authApiKeyIn`=`header`|`query`，`authApiKeyName`，`authApiKeyValue`。  
`successCondition` 为空时按 HTTP 2xx。

---

### 5.8 `api` — 调用内部 Flow API 或内部服务

| | |
| --- | --- |
| 入 | `in:payload` |
| 出 | `out` |
| 必填 | `serviceId`（目标实体 id） |
| 可选 | `targetType`：`api`（默认，Flow API）\| `service`（内部服务编排） |

调用 **Flow API**：

```json
{
  "id": "call_inner",
  "type": "api",
  "data": {
    "targetType": "api",
    "serviceId": "<target-flow-api-id>",
    "inputs": {
      "q": { "extractPath": "$.req.params.q", "paramSource": "query" }
    }
  }
}
```

调用 **内部服务**（须已发布且启用）：

```json
{
  "id": "call_svc",
  "type": "api",
  "data": {
    "targetType": "service",
    "serviceId": "<target-service-flow-id>",
    "inputs": {
      "userId": { "extractPath": "$.req.params.userId" }
    }
  }
}
```

| `targetType` | 行为 |
| --- | --- |
| `api`（默认） | 调另一条 Flow API；`paramSource`=`query`/`path`/`body`/`header` 映射到被调侧 `@QP/@PP/@BP` |
| `service` | 调服务编排；入参写入被调侧 `$.service.input`；按服务已发布契约校验/类型转换 |

省略 `targetType` 时按 `api` 处理。

---

### 5.9 `database` — SQL

| | |
| --- | --- |
| 入 | `in:payload` |
| 出 | `out` |

```json
{
  "id": "db1",
  "type": "database",
  "data": {
    "datasourceId": "<ds-id>",
    "sqlType": "SELECT",
    "returnType": "LIST",
    "sql": "SELECT * FROM user WHERE id = #{id}",
    "inputs": {
      "id": { "extractPath": "$.req.params.id" }
    }
  }
}
```

`sqlType`：`SELECT`|`INSERT`|`UPDATE`|`DELETE`。  
`returnType`（SELECT）：`LIST`|`OBJECT`|`PAGE`。  
SQL 参数占位以项目数据源引擎为准（常见 `#{name}` / 命名参数与 `inputs` 键对应）。

---

### 5.10 `record` — 拼装对象

| | |
| --- | --- |
| 入 | `in:payload`（及字段变量口） |
| 出 | `out` |
| 核心 | `schema`：字段 → 字面量 / `$.path` / `${ref}` |

```json
{
  "id": "rec",
  "type": "record",
  "data": {
    "schema": {
      "userId": "$.req.params.id",
      "ok": true,
      "count": 1,
      "tag": "manual"
    }
  }
}
```

下游引用：`$.rec.out.userId`。

---

### 5.11 `template` — 文本模板

| | |
| --- | --- |
| 入 | `in:payload` + `in:var:*` |
| 出 | `out` |
| 占位 | `{{key}}` |

```json
{
  "id": "tpl",
  "type": "template",
  "data": {
    "template": "Hello {{name}}, id={{id}}",
    "inputs": {
      "name": { "extractPath": "$.req.params.name" },
      "id": { "extractPath": "$.req.params.id" }
    }
  }
}
```

---

### 5.12 `systemVar` / `systemMethod`

**systemVar**

```json
{ "id": "sv", "type": "systemVar", "data": { "variableCode": "<注册表中的变量编码>" } }
```

出：`out`。无入。

**systemMethod**

```json
{
  "id": "sm",
  "type": "systemMethod",
  "data": {
    "methodCode": "DATE_FORMAT",
    "inputs": {
      "date": { "extractPath": "$.req.params.d" },
      "format": "yyyy-MM-dd"
    }
  }
}
```

入端口习惯：`in:arg:date`。`methodCode` / `variableCode` 必须是环境已注册的宏编码（无法从本 DSL 推断具体清单时，用占位并注明需替换）。

---

### 5.13 `forEach` — 串行循环

| | |
| --- | --- |
| 入 | `in`（列表，解析为 `inputs.list`） |
| 出 | `item`（每轮子流入口）、`done`（全部结束） |
| 循环内上下文 | 当前项常在 `$.forEachId.item`（及 `index`） |

```json
{ "id": "loop", "type": "forEach", "data": { "inputs": {} } }
```

典型边：`listSrc.out → loop.in`；`loop.item → 子节点.in`；子节点处理完需回到循环语义由引擎驱动；全部完成后从 `loop.done` 连下游。

---

### 5.14 `for` + `collect` — 并发 Scatter-Gather

**for**

| | |
| --- | --- |
| 入 | `in`（list）、`start` |
| 出 | `item` |
| 必填 | `collectStepId`（对应 Collect 节点 id） |
| 可选 | `timeoutMs`（默认 30000） |

**collect**

| | |
| --- | --- |
| 入 | `item` |
| 出 | `list`, `finish` |
| 可选 | `timeoutMs` |

```json
{
  "nodes": [
    { "id": "scatter", "type": "for", "data": { "collectStepId": "gather", "timeoutMs": 30000 } },
    { "id": "gather", "type": "collect", "data": { "timeoutMs": 30000 } }
  ],
  "edges": [
    { "source": { "cell": "listSrc", "port": "out" }, "target": { "cell": "scatter", "port": "in" } },
    { "source": { "cell": "scatter", "port": "item" }, "target": { "cell": "worker", "port": "in:payload" } },
    { "source": { "cell": "worker", "port": "out" }, "target": { "cell": "gather", "port": "item" } },
    { "source": { "cell": "gather", "port": "list" }, "target": { "cell": "resp", "port": "in:var:v1" } }
  ]
}
```

---

### 5.15 `parallel` — 并行网关

| | |
| --- | --- |
| 入 | `in` |
| 出 | `out`（**多条边**即并行扇出） |
| data | `errorMode`: `FAST_FAIL` \| `CONTINUE` |

```json
{
  "id": "par",
  "type": "parallel",
  "data": { "errorMode": "FAST_FAIL" }
}
```

用图上从 `out` 出发的多条边表示并行（无嵌套子步骤字段）。

---

### 5.16 `delay` — 等待

```json
{
  "id": "wait",
  "type": "delay",
  "data": {
    "delayMs": 1000,
    "inputs": {}
  }
}
```

入 `in`，出 `out`。`delayMs` 也可被 `inputs.delayMs` 覆盖。演示模式可能有上限。

---

### 5.17 `sendMail` — 发送邮件

依赖系统配置 `MAIL_*` / `yu.flow.mail` SMTP。字段支持 `${var}`（来自 `inputs`）；也可用 `inputs.to` / `subject` / `text` / `html` 覆盖。

```json
{
  "id": "mail1",
  "type": "sendMail",
  "data": {
    "to": "ops@example.com",
    "subject": "告警 ${title}",
    "text": "详情：${detail}",
    "html": "",
    "inputs": {}
  }
}
```

入 `in`，出 `out`（`{ success, to, subject }`）。

---

### 5.18 `errorHandler` — 异常汇聚（单例）

| | |
| --- | --- |
| 入 | **无**（引擎异常跳转，不挂在主路径） |
| 出 | `out` |
| 数据 | `$.error` |

```json
{ "id": "eh", "type": "errorHandler", "data": {} }
```

从 `out` 连到日志 / Response 即可。

---

## 6. 完整示例

### 6.1 Request → Evaluate → If → Response

```json
{
  "nodes": [
    {
      "id": "req",
      "type": "request",
      "data": { "method": "GET" }
    },
    {
      "id": "age_num",
      "type": "evaluate",
      "data": {
        "language": "JavaScript",
        "expression": "Number(age)",
        "inputs": {
          "age": { "id": "v_age", "extractPath": "$.req.params.age" }
        }
      }
    },
    {
      "id": "check",
      "type": "if",
      "data": {
        "language": "JavaScript",
        "condition": "n >= 18",
        "inputs": {
          "n": { "id": "v_n", "extractPath": "$.age_num.out" }
        }
      }
    },
    {
      "id": "ok",
      "type": "response",
      "data": {
        "status": 200,
        "body": { "pass": true },
        "inputs": {}
      }
    },
    {
      "id": "deny",
      "type": "response",
      "data": {
        "status": 403,
        "body": { "pass": false },
        "inputs": {}
      }
    }
  ],
  "edges": [
    {
      "source": { "cell": "req", "port": "params" },
      "target": { "cell": "age_num", "port": "in:var:v_age" }
    },
    {
      "source": { "cell": "age_num", "port": "out" },
      "target": { "cell": "check", "port": "in:var:v_n" }
    },
    {
      "source": { "cell": "check", "port": "true" },
      "target": { "cell": "ok", "port": "in" }
    },
    {
      "source": { "cell": "check", "port": "false" },
      "target": { "cell": "deny", "port": "in" }
    }
  ]
}
```

### 6.2 Switch 多路

```json
{
  "nodes": [
    { "id": "req", "type": "request", "data": { "method": "GET" } },
    {
      "id": "sw",
      "type": "switch",
      "data": {
        "language": "JavaScript",
        "expression": "code",
        "cases": [
          { "id": "c200", "name": "OK", "value": "200" },
          { "id": "c404", "name": "Missing", "value": "404" }
        ],
        "inputs": {
          "code": { "extractPath": "$.req.params.code" }
        }
      }
    },
    {
      "id": "r_ok",
      "type": "response",
      "data": { "status": 200, "body": "ok", "inputs": {} }
    },
    {
      "id": "r_miss",
      "type": "response",
      "data": { "status": 404, "body": "missing", "inputs": {} }
    },
    {
      "id": "r_def",
      "type": "response",
      "data": { "status": 400, "body": "other", "inputs": {} }
    }
  ],
  "edges": [
    {
      "source": { "cell": "req", "port": "params" },
      "target": { "cell": "sw", "port": "in:payload" }
    },
    {
      "source": { "cell": "sw", "port": "case_c200" },
      "target": { "cell": "r_ok", "port": "in" }
    },
    {
      "source": { "cell": "sw", "port": "case_c404" },
      "target": { "cell": "r_miss", "port": "in" }
    },
    {
      "source": { "cell": "sw", "port": "default" },
      "target": { "cell": "r_def", "port": "in" }
    }
  ]
}
```

---

## 7. 给大模型的生成检查清单

1. **根对象**只有 `nodes` + `edges`（可加 `id`/`version`）。
2. **恰好一个入口**：`request` / `schedule` / `service` 三选一；API 场景再配 **至少一个** `response`；服务编排用 `service`，无 `response` 要求。
3. 每个节点：`id` 唯一、`type` 合法、业务字段在 `data`。
4. 每条控制流边的 `source.port` 必须是该类型**真实出口**（If 用 `true`/`false`，Switch 用 `case_<id>`/`default`，HttpRequest 用 `success`/`fail`）。
5. 数据引用统一 `$.节点id.out`（Request 用 `.headers/.params/.body`；服务编排用 `$.service.input`）。
6. `inputs` 键名 = 表达式变量名；需要连线时带稳定 `id`，边指向 `in:var:<id>`。
7. Switch：`cases[].id` 稳定且与边端口 `case_<id>` 一致；匹配看 `value`。
8. For 必须声明 `collectStepId` 且图上存在对应 `collect`。
9. **禁止生成**（引擎不再识别）：`condition`、`start`、`end`、`set`、`return`、`serviceCall`、`call`。If 的字段名 `condition` 可以，那是表达式字段不是节点类型。
10. `systemVar` / `systemMethod` / `api.serviceId` / `database.datasourceId` 用明确占位符；`api` 调服务时写 `targetType: "service"`。
11. 坐标与 `ports` 数组可省略；若写出 `ports`，须与所用边端口一致。
12. Parallel：只用多条从 `out` 出发的边表示并行，**不要**写 `tasks`。
13. Switch：`cases` 必须是 `[{id,name,value}]`，禁止字符串数组。

---

## 8. 已移除类型（引擎不再识别）

下列 `type` 已从引擎与编辑器彻底清除，生成或导入含这些类型的 JSON 将失败：

`condition` · `start` · `end` · `set` · `return` · `serviceCall` · `call`

替换约定：入口/出口用 `request`/`response`（或 `schedule` / 内部复用用 `service`）；外部调用用 `httpRequest`；内部编排用 `api`（`targetType`=`api`|`service`）；赋值/计算用 `evaluate` 或 `record`。

---

## 9. 引擎格式对照（可选阅读）

画布经解析后大致变为：

```json
{
  "startStepId": "req",
  "steps": [
    {
      "id": "req",
      "type": "request",
      "method": "GET",
      "next": { "params": "eval1" }
    }
  ]
}
```

- `data.*` 铺平到 Step 根。
- `edges` → 各节点 `next[sourcePort] = targetId | targetId[]`。
- **生成时不必手写 `steps`/`next`**，交给引擎即可。

---

## 10. 相关文档

| 文档 | 内容 |
| --- | --- |
| [编排画布概念](/api/) | 人机操作与端口直觉 |
| [Request](/api/nodes/request) | 入口细节 |
| [If](/api/nodes/if) | 条件表达式 |
| [Evaluate](/api/nodes/evaluate) | 多语言脚本 |
| [Database](/api/nodes/database) | SQL 节点 |

本页为 **生成 JSON 的单一完整规格**；节点专页偏产品说明，若与本页冲突，**以本页 + 引擎 Step 模型为准**。
