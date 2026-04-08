---
title: Flow DSL 协议规范
outline: deep
---

# Flow DSL 协议规范

> **“配置即代码 (Configuration as Code)”的终极形态。**
>
> Flow DSL (Domain Specific Language) 是 Yu Flow 引擎的“宪法”与唯一基准。它打破了传统硬编码的僵凝，将复杂的业务逻辑抽象为极度灵活、与语言无关的 JSON 状态机模型。无论是通过前端可视化画布的拖拽，还是通过自然语言 (AI) 生成，最终都会被统一翻译为这段结构化的 DSL，由后端的 Spring Boot 执行引擎毫秒级解析与无缝执行。

## 🧬 核心数据结构 (The Anatomy of Flow)

Yu Flow 引擎支持两种 DSL 拓扑形态，但在实际的数据流转中，我们主要暴露对开发者最友好、对前端图形渲染最直观的 **“画布格式 (Canvas Format)”**。引擎内部的 `FlowParser` 会自动在运行时将其转化为包含连接引用的执行态结构。

一份标准的 Flow DSL 由三大核心区块构成：

1. **元数据 (Metadata)**：定义流程的整体状态（例如 `id`, `version`）、全局入参 (`args`) 和异常捕捉策略 (`errors`)。
2. **节点列表 (`nodes` / `steps`)**：代表所有的业务动作单元（数据计算、API 请求、控制流等）。
3. **边连接 (`edges` / `next`)**：决定节点执行顺序和数据流转方向的有向图关系。

::: info 💡 设计哲学：模型数据与视图解耦
在 `nodes` 集合中，您会看到诸如 `x`, `y`, `width` 和 `height` 等坐标属性。这是保留给前端图形化画布的视图基准点。而对于后端执行引擎而言，这些视图元数据会被自动忽略和剥离，确保执行过程极速运行而无多余负担。
:::

---

## 🔬 节点 (Node) 的微观解剖

为了保证极智的扩展能力，每一个节点 (Step) 都遵循高度收敛的规范约定。在执行生命周期中，前端传递包裹在 `data` 对象中的业务底层属性，会被后端引擎安全地“铺平 (flatten)”映射到对应 Step 的根级别。

一个标准节点的底层微观结构如下：

* **`id`** *(String)*：全局唯一标识符，作为寻址锚点（例如：`request_1772497969851_2`）。
* **`type`** *(String)*：节点的物理寻址和引擎调度类型。系统内置了丰富的类型，如 `request`（请求挂载）、`if`（条件分支）、`systemMethod`（内置方法）、`database`（数据库操作）和 `response`（响应终态）。
* **`ports`** *(Array)*：用于定义节点的微观出入口。`id` 中往往包含语义机制，例如 `"in:arg:date"` 意味着输入方法参数，`"out"` 意味着正常出口。
* **`data`** *(Object)*：**节点的核心执行载体**。这是每一个节点发挥作用的特征存放区。
  * **`inputs`** *(Object)*：**(极度重要)** 它定义了从关联端点取值的数据装载规则（ETL）。依托内部强大的规则生成器，经常使用 `extractPath`（JSONPath 语法，如 `$.request.headers`）从历史执行截面和上游任意节点截取数据。

### 代码解剖示例

::: tip 最佳实践：动态提取与注入
在下方的 `SystemMethod` 节点中，引擎会根据 `extractPath` 提取上游源数据。若路径以 `$.` 开头，会追踪源节点输出；若以直接量呈现，则作为常量入参传递。
:::

```json
{
  "id": "systemMethod_1772497971425_3",
  "type": "systemMethod",
  "label": "日期格式化",
  "data": {
    "methodCode": "DATE_FORMAT",
    "inputs": {
      "date": {
        // 利用 JSONPath 提取相对环境上下文变量
        "extractPath": "$.date"
      },
      "format": {
        // 或者作为常量字面量传递
        "extractPath": "yyyy-mm-dd"
      }
    }
  }
}
```

::: warning ⚠️ 安全注意
所有的动态数据提取和表达式（如 SpEL / Aviator）运算都严格限制在沙箱容器模型内。引擎会自动拦截非授权的底层反射调用和恶意代码注入，保障企业级私有化与云上使用的绝对数据安全。
:::

---

## 🌉 边与执行流转 (`edges`)

`edges` 描画了节点与节点之间无缝相连的有界连接体（Jointer）。画布所见的连线代表了代码的本质连接。

* **`source.cell`**：数据和控制流的源节点 ID。
* **`source.port`**：源节点的输出端点（常见的有逻辑分支：`true` / `false`，标准输出：`out`，参数引流：`params`）。
* **`target.cell`**：承接输入的目标节点 ID。
* **`target.port`**：目标的接收端点。如果绑定到具体的输入上下文，通常遵照 `in:var:`（普通动态变量）或 `in:arg:`（系统方法传参）的命名规范。

---

## 🚀 完整示例 (Full Example)

以下是一段极简但五脏俱全且具备完整运行能力的业务流程 JSON。

**业务场景语义**：
定义一个 HTTP 外部挂载触发器（Request） -> 将收到的 `params` 传递给内置函数系统，执行“日期格式化”（SystemMethod） -> 取决于处理后的结果格式，装载至结构体（Response）并携带正确 200 HTTP Status 渲染返回。

```json
{
  "nodes": [
    {
      "id": "request_1772497969851_2",
      "type": "request",
      "x": -600,
      "y": -250,
      "width": 260,
      "height": 104,
      "label": "request",
      "ports": [
        {
          "id": "headers",
          "group": "absolute-out-solid"
        },
        {
          "id": "params",
          "group": "absolute-out-solid"
        }
      ],
      "data": {
        "method": "GET"
      }
    },
    {
      "id": "systemMethod_1772497971425_3",
      "type": "systemMethod",
      "x": -205,
      "y": -167,
      "width": 320,
      "height": 156,
      "label": "日期格式化",
      "ports": [
        {
          "id": "out",
          "group": "absolute-out-solid"
        },
        {
          "id": "in:arg:date",
          "group": "absolute-in-solid"
        },
        {
          "id": "in:arg:format",
          "group": "absolute-in-solid"
        }
      ],
      "data": {
        "methodCode": "DATE_FORMAT",
        "inputs": {
          "date": {
            "extractPath": "$.date"
          },
          "format": {
            "extractPath": "yyyy-mm-dd"
          }
        }
      }
    },
    {
      "id": "response_1773382280010_1",
      "type": "response",
      "x": 200,
      "y": -190,
      "width": 320,
      "height": 344,
      "label": "response",
      "ports": [
        {
          "id": "in:var:var_ezqqk4",
          "group": "absolute-in-solid"
        },
        {
          "id": "in:var:var_cbfecu",
          "group": "absolute-in-solid"
        }
      ],
      "data": {
        "status": 200,
        "headers": {},
        "body": "${var1}",
        "inputs": {
          "var1": {
            "id": "var_ezqqk4",
            "extractPath": "$"
          }
        }
      }
    }
  ],
  "edges": [
    {
      "source": {
        "cell": "systemMethod_1772497971425_3",
        "port": "out"
      },
      "target": {
        "cell": "response_1773382280010_1",
        "port": "in:var:var_ezqqk4"
      }
    },
    {
      "source": {
        "cell": "request_1772497969851_2",
        "port": "params"
      },
      "target": {
        "cell": "systemMethod_1772497971425_3",
        "port": "in:arg:date"
      }
    }
  ]
}
```

::: info 🧠 架构师视角：引擎的接管脉络
当这份 JSON 画布结构发送到后端 Spring Boot 引擎时，`FlowParser` 会自动构建出 `parentMap` 和边集合映射体系。它剥离所有的视觉包袱，生成具有高度紧缩执行链列的结构体。根据 `request -> systemMethod -> response` 的底层拓扑推导，依托核心 `ThreadPoolExecutor` 的底层无锁并发屏障模型，完成并行、分支和调度的毫秒级生命周期接管，完全脱离传统的硬编码 MVC 反射链。
:::
