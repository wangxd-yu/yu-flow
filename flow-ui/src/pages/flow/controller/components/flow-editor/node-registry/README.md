# Flow Editor 节点注册系统 — 开发者文档 (V3.2)

## 目录

1. [架构概览](#1-架构概览)
2. [核心接口：NodeRegistration](#2-核心接口-noderegistration)
3. [新增节点完整步骤](#3-新增节点完整步骤)
4. [各字段详细说明](#4-各字段详细说明)
5. [修改已有节点](#5-修改已有节点)
6. [文件改动清单](#6-文件改动清单)
7. [大模型开发提示词 (Prompt)](#7-大模型开发提示词)

---

## 1. 架构概览

### V3.1 → V3.2 重构前后对比

| 关注点 | V3.1（旧） | V3.2（新） |
|--------|-----------|-----------|
| 节点定义位置 | 分散在 5 个文件 | **单一模块**（`node-registry/nodes/xxx.ts(x)`） |
| 新增节点需改动 | `types.ts` + `registerCustomNodes.ts` + `adapter.ts` + `NodePropertyDrawer.tsx` + 节点组件文件 | **只需新建 1 个文件** |
| 颜色/标签/配置 | 多处硬编码/switch-case | 集中在 `NodeRegistration` 对象 |
| 属性面板渲染 | 780 行 switch-case 巨文件 | 动态查表，无 switch |
| 面板节点列表 | 静态常量 `NODE_TYPE_CONFIGS` | 运行时从注册表读取 |

### 目录结构

```
flow-editor/
├── node-registry/                ← 🆕 节点注册系统
│   ├── types.ts                  ← NodeRegistration 接口定义
│   ├── registry.ts               ← 注册表增删查 API + X6 形状注册
│   ├── index.ts                  ← 统一入口，initNodeRegistry()
│   └── nodes/                    ← 每个文件 = 一个节点类型
│       ├── start.ts              ← Start 节点
│       ├── end.tsx               ← End 节点（含属性编辑器）
│       ├── evaluate.tsx          ← Evaluate 节点
│       ├── if-node.tsx           ← If 节点（manual port）
│       ├── switch-node.tsx       ← Switch 节点
│       ├── service-call.tsx      ← ServiceCall 节点
│       ├── http-request.ts       ← HTTP Request 节点（React 组件）
│       ├── for-each.tsx          ← ForEach 节点
│       ├── record.tsx            ← Record 节点
│       ├── response.tsx          ← Response 节点
│       ├── request.tsx           ← Request 节点（manual port + 共用编辑器）
│       ├── template.tsx          ← Template 节点
│       ├── collect.tsx           ← Collect 节点
│       └── database.ts           ← Database 节点（复杂 React 组件）
├── adapter.ts                    ← 精简为 ~280 行纯框架逻辑
├── registerCustomNodes.ts        ← 兼容层（一行委托给 initNodeRegistry）
├── components/
│   ├── NodePropertyDrawer.tsx    ← 精简为 ~220 行，动态渲染
│   └── DslPalette.tsx            ← 从注册表动态读取节点列表
└── types.ts                      ← DSL 类型（DslNodeType 等，保持不变）
```

---

## 2. 核心接口：NodeRegistration

每个节点类型导出一个 `NodeRegistration` 对象，该对象包含该节点的**所有**信息：

```typescript
interface NodeRegistration {
    // ── 元信息 ──────────────────────────────
    type: DslNodeType;        // 唯一类型 ID，与 DSL 中 node.type 一致
    label: string;            // 面板显示名称
    category: string;         // 面板分组名（'基础节点'|'逻辑节点'|'调用节点'|'数据节点'）
    color: string;            // 主色（hex），用于面板图标和节点边框
    tagColor: string;         // Ant Design Tag color，用于属性面板标签
    singleton?: boolean;      // true = 画布中只允许一个（如 Start）
    hasInputs: boolean;       // true = 属性面板显示通用 Inputs（JSONPath 映射）区块

    // ── X6 形状注册 ──────────────────────────
    shape: ShapeRegistration; // 见下方详解

    // ── 节点默认值 ───────────────────────────
    defaults: {
        ports: DslPort[];                       // 默认端口列表
        data: Record<string, any>;              // 默认业务数据
        size: { width: number; height: number }; // 默认尺寸
        dynamicSize?: (ports: DslPort[]) => { width: number; height: number };
    };

    // ── DSL 导入配置 (importDslToGraph 使用) ──
    importConfig: ImportConfig;

    // ── 标签文本构建 ──────────────────────────
    buildLabel: (data: Record<string, any>) => string;

    // ── 属性面板编辑器（可选）──────────────────
    PropertyEditor?: React.ComponentType<PropertyEditorProps>;
}
```

---

## 3. 新增节点完整步骤

以新增一个 **"邮件发送 (Email)"** 节点为例：

### 步骤 1：扩展 DSL 类型

在 `flow-editor/types.ts` 中，将新类型加入 `DslNodeType` 联合类型：

```typescript
// types.ts
export type DslNodeType =
    | 'start' | 'end' | 'evaluate' | 'if' | 'switch'
    | 'serviceCall' | 'httpRequest' | 'forEach' | 'record'
    | 'response' | 'request' | 'template' | 'collect' | 'database'
    | 'email';   // ← 新增
```

> **仅此一处改动** — 不需要动 `NODE_TYPE_CONFIGS`（该常量已废弃，可在下个版本移除）。

### 步骤 2：创建节点模块文件

新建 `flow-editor/node-registry/nodes/email.tsx`：

```typescript
// node-registry/nodes/email.tsx
import React from 'react';
import { Divider, Input, Typography } from 'antd';
import type { NodeRegistration, PropertyEditorProps } from '../types';

const { Text } = Typography;

// ── 属性面板编辑器（与节点定义同文件）──
function EmailEditor({ data, onChange }: PropertyEditorProps) {
    return (
        <div style={{ marginTop: 12 }}>
            <Divider orientation="left" style={{ fontSize: 12, margin: '8px 0' }}>
                邮件配置
            </Divider>
            <div style={{ marginBottom: 8 }}>
                <Text type="secondary" style={{ fontSize: 12 }}>收件人</Text>
                <Input
                    size="small"
                    value={data.to || ''}
                    placeholder="user@example.com"
                    style={{ marginTop: 4 }}
                    onChange={(e) => onChange({ to: e.target.value })}
                />
            </div>
            <div style={{ marginBottom: 8 }}>
                <Text type="secondary" style={{ fontSize: 12 }}>主题</Text>
                <Input
                    size="small"
                    value={data.subject || ''}
                    placeholder="邮件主题"
                    style={{ marginTop: 4 }}
                    onChange={(e) => onChange({ subject: e.target.value })}
                />
            </div>
            <div>
                <Text type="secondary" style={{ fontSize: 12 }}>正文 (支持模板变量 {{name}})</Text>
                <Input.TextArea
                    size="small"
                    value={data.body || ''}
                    autoSize={{ minRows: 3, maxRows: 8 }}
                    style={{ marginTop: 4 }}
                    onChange={(e) => onChange({ body: e.target.value })}
                />
            </div>
        </div>
    );
}

// ── 节点注册对象 ──
export const emailNodeRegistration: NodeRegistration = {
    type: 'email',
    label: '发送邮件 (Email)',
    category: '调用节点',
    color: '#17a2b8',
    tagColor: 'cyan',
    hasInputs: true,

    shape: {
        shapeName: 'flow-email',     // X6 注册形状名，必须唯一
        kind: 'svg',                 // 'svg' 用 Graph.registerNode; 'react' 用 register()
        svgConfig: {
            width: 200,
            height: 60,
            markup: [
                { tagName: 'rect', selector: 'body' },
                { tagName: 'text', selector: 'label' },
            ],
            attrs: {
                body: {
                    rx: 8, ry: 8,
                    stroke: '#17a2b8', strokeWidth: 2, fill: '#ffffff',
                    refWidth: '100%', refHeight: '100%',
                },
                label: {
                    text: 'Email',
                    fill: '#1f1f1f', fontSize: 12,
                    textAnchor: 'middle', textVerticalAnchor: 'middle',
                    refX: 0.5, refY: 0.5,
                },
            },
            ports: {
                items: [
                    { id: 'in', group: 'left' },
                    { id: 'out', group: 'right' },
                ],
            },
        },
    },

    defaults: {
        ports: [{ id: 'in' }, { id: 'out' }],
        data: { to: '', subject: '', body: '', inputs: {} },
        size: { width: 200, height: 60 },
    },

    importConfig: {
        portMode: 'standard',  // 'standard' = 通用左右端口组 + 文字标签
    },

    buildLabel: (data) =>
        data.to ? `Email → ${data.to}` : 'Email',

    PropertyEditor: EmailEditor,
};
```

### 步骤 3：注册到入口文件

在 `flow-editor/node-registry/index.ts` 中添加两行：

```typescript
// 在 import 区域添加：
import { emailNodeRegistration } from './nodes/email';

// 在 BUILTIN_NODES 数组中添加：
const BUILTIN_NODES = [
    // ... 已有节点 ...
    emailNodeRegistration,  // ← 新增
];
```

### 步骤 4：验证

1. 启动开发服务器：`npm run dev`
2. 打开 Flow Editor，在左侧面板的 **调用节点** 分组中应出现 **发送邮件 (Email)**
3. 双击或拖拽到画布
4. 点击节点，右侧面板应出现 **邮件配置** 表单
5. 保存后导出 DSL，验证 `type: "email"` 节点正确序列化

> **无需修改** `adapter.ts`、`NodePropertyDrawer.tsx`、`DslPalette.tsx`、`registerCustomNodes.ts`！

---

## 4. 各字段详细说明

### 4.1 `shape` — X6 形状注册

#### SVG 形状 (`kind: 'svg'`)

用于简单节点（矩形/圆形 + 文字标签）：

```typescript
shape: {
    shapeName: 'flow-email',      // 全局唯一
    kind: 'svg',
    svgConfig: {
        width: 200, height: 60,
        markup: [
            { tagName: 'rect', selector: 'body' },
            { tagName: 'text', selector: 'label' },
        ],
        attrs: {
            body: { /* SVG 属性 */ },
            label: { /* 文字属性 */ },
        },
        ports: {
            items: [
                { id: 'in', group: 'left' },
                { id: 'out', group: 'right' },
            ],
        },
    },
}
```

#### React 形状 (`kind: 'react'`)

用于复杂节点（需要交互式 UI）：

```typescript
shape: {
    shapeName: 'flow-email',
    kind: 'react',
    component: MyReactNodeComponent,   // React 组件，接收 { node: Node }
    reactPorts: {
        groups: {
            manual: {
                position: 'absolute',
                attrs: { circle: { r: 4, magnet: true, stroke: '#ccc', fill: '#fff' } },
            },
        },
        items: [
            // 绝对定位端口（坐标与组件内部布局对齐）
            { id: 'in', group: 'manual', args: { x: 0, y: 30 } },
            { id: 'out', group: 'manual', args: { x: 200, y: 30 } },
        ],
    },
}
```

### 4.2 `importConfig` — DSL 导入配置

控制 `importDslToGraph` 如何为该节点构建端口和样式：

```typescript
importConfig: {
    // 'standard': 通用 left/right 端口组 + 文字标签（大多数节点使用）
    // 'manual': 绝对定位，需提供 buildPortItems
    portMode: 'standard' | 'manual',

    // portMode='manual' 时必须提供
    buildPortItems?: (ports: DslPort[]) => any[],

    // 自定义 attrs（SVG 节点的视觉属性）
    // 若不提供，使用默认矩形白底圆角样式
    buildAttrs?: (color: string, label: string) => Record<string, any>,
}
```

### 4.3 `PropertyEditor` — 属性面板组件

接收三个 props：

```typescript
type PropertyEditorProps = {
    node: Node;                          // AntV X6 节点实例（用于直接操作端口等）
    data: Record<string, any>;           // 节点当前业务数据（__dslType 等内部字段已过滤）
    onChange: (changes: Record<string, any>) => void;  // 触发数据变更
};
```

> `onChange` 只需传入**变更的字段**，内部会与现有数据合并。

---

## 5. 修改已有节点

### 5.1 修改节点颜色/标签

直接在对应节点模块文件中修改 `color`、`label` 等字段，无需改其他文件。

### 5.2 修改属性面板

在对应 `nodes/xxx.tsx` 文件中修改 `PropertyEditor` 组件，或将 `PropertyEditor` 替换为新组件。

### 5.3 修改默认端口

修改 `defaults.ports`，同时可能需要同步修改：
- `shape.svgConfig.ports.items`（SVG 节点的初始端口）
- `shape.reactPorts.items`（React 节点的初始端口）
- `importConfig.buildPortItems`（DSL 导入时的端口重建）

### 5.4 修改节点尺寸

修改 `defaults.size`，React 节点还需同步修改 `shape.reactPorts` 中端口的 `args.x/y` 坐标。

---

## 6. 文件改动清单

### 本次重构（V3.1 → V3.2）新建/修改文件

| 文件 | 状态 | 说明 |
|------|------|------|
| `node-registry/types.ts` | 🆕 新建 | NodeRegistration 接口 |
| `node-registry/registry.ts` | 🆕 新建 | 注册表增删查 + X6 形状注册 |
| `node-registry/index.ts` | 🆕 新建 | 统一入口 initNodeRegistry() |
| `node-registry/nodes/start.ts` | 🆕 新建 | Start 节点模块 |
| `node-registry/nodes/end.tsx` | 🆕 新建 | End 节点模块 |
| `node-registry/nodes/evaluate.tsx` | 🆕 新建 | Evaluate 节点模块 |
| `node-registry/nodes/if-node.tsx` | 🆕 新建 | If 节点模块 |
| `node-registry/nodes/switch-node.tsx` | 🆕 新建 | Switch 节点模块 |
| `node-registry/nodes/service-call.tsx` | 🆕 新建 | ServiceCall 节点模块 |
| `node-registry/nodes/http-request.ts` | 🆕 新建 | HttpRequest 节点模块 |
| `node-registry/nodes/for-each.tsx` | 🆕 新建 | ForEach 节点模块 |
| `node-registry/nodes/record.tsx` | 🆕 新建 | Record 节点模块 |
| `node-registry/nodes/response.tsx` | 🆕 新建 | Response 节点模块 |
| `node-registry/nodes/request.tsx` | 🆕 新建 | Request 节点模块 |
| `node-registry/nodes/template.tsx` | 🆕 新建 | Template 节点模块 |
| `node-registry/nodes/collect.tsx` | 🆕 新建 | Collect 节点模块 |
| `node-registry/nodes/database.ts` | 🆕 新建 | Database 节点模块 |
| `adapter.ts` | ✏️ 重写 | 647 → 280 行，所有 switch-case 替换为注册表查询 |
| `registerCustomNodes.ts` | ✏️ 简化 | 600 → 15 行兼容层 shim |
| `components/NodePropertyDrawer.tsx` | ✏️ 重写 | 780 → 220 行，动态渲染属性编辑器 |
| `components/DslPalette.tsx` | ✏️ 修改 | 从注册表动态读取节点列表 |
| `FlowEditorV3.tsx` | ✏️ 修改 | 使用 initNodeRegistry() 替代 registerCustomNodes() |

### 未修改文件（保持完整接口兼容）

| 文件 | 说明 |
|------|------|
| `types.ts` | DslNodeType 类型（需手动新增 type 枚举值） |
| `components/nodes/*` | 所有现有节点 React 组件（DatabaseNode、IfNodeComponent 等） |
| `components/configs/*` | DatabaseNodeConfig、HttpNodeConfig（保持原位，由节点模块引用） |
| `graph.ts` | 图工具函数 |

---

## 7. 大模型开发提示词

以下提示词可直接复制给大模型（Claude/GPT/Gemini）用于快速生成新节点：

---

### 提示词 A：新增简单 SVG 节点

```
你是一名精通 React + AntV X6 的高级前端工程师，正在为一个流编排引擎开发新节点。

项目中使用"节点注册系统" (NodeRegistration)，每个节点类型是一个自包含的 .tsx 文件。

请为我创建一个 **[节点类型名称]** 节点，要求：

**节点基本信息：**
- 类型 ID（DslNodeType）：[例如 'notify']
- 显示名称：[例如 '推送通知 (Notify)']
- 节点分类：[例如 '调用节点']
- 主色：[例如 '#f4a261']
- 输入端口：[例如 'in' (左侧)]
- 输出端口：[例如 'out' (右侧)]
- 是否显示通用 Inputs 映射区：[是/否]

**节点业务数据字段：**
- [字段名]: [类型] - [说明]
- [例如 title: string - 通知标题]
- [例如 message: string - 通知内容]
- [例如 channel: 'email'|'sms'|'push' - 推送渠道]

**节点标签显示：**
- [例如：有 title 时显示 "Notify: {title}"，否则显示 "Notify"]

**属性面板包含：**
- [例如 title 文本输入框]
- [例如 message 多行文本]
- [例如 channel 下拉选择]

请按以下格式输出：

1. 创建 `node-registry/nodes/notify.tsx` 文件（包含 PropertyEditor + NodeRegistration 对象）
2. 在 `flow-editor/types.ts` 的 DslNodeType 中新增 'notify'
3. 在 `node-registry/index.ts` 的 import 和 BUILTIN_NODES 中注册

注意事项：
- 使用 @ant-design + antd 组件
- PropertyEditor 的 onChange 只传变更字段，无需全量更新
- 遵循 NodeRegistration 接口格式（参见 node-registry/types.ts）
- .tsx 文件（含 JSX），.ts 文件（不含 JSX）
```

---

### 提示词 B：新增复杂 React 节点（可交互卡片）

```
你是一名精通 React + AntV X6 的高级前端工程师。

请为流编排引擎创建一个 **[节点类型名称]** 节点，该节点使用 React 组件渲染（卡片式 UI），具有复杂交互。

**现有节点组件可参考：**
- DatabaseNode（`components/nodes/DatabaseNode.tsx`）：复杂表单 + 动态端口 + Monaco 编辑器
- HttpRequestNodeComponent（`components/nodes/HttpRequestNodeComponent.tsx`）：动态端口 + 键值列表

**新节点要求：**
- 类型 ID：[例如 'transform']
- 形状名：[例如 'flow-transform']
- 节点 UI：[描述卡片内容，例如 "顶部标题栏 + 中间字段映射列表 + 底部输出端口"]
- 动态端口：[例如 "每个映射字段对应一个左侧输入端口"]
- 端口命名规则：[例如 in:field:{id}]
- 默认尺寸：[例如 宽 280，高 初始 120，按字段数量动态调整]

**技术要求：**
1. React 组件文件：`components/nodes/TransformNode.tsx`
   - 使用 useNodeSelection（共享 Hook，提供选中/主题样式）
   - 暴露 TRANSFORM_LAYOUT 常量（width, totalHeight）
   
2. 注册模块文件：`node-registry/nodes/transform.ts`（.ts，因为不含 JSX）
   - kind: 'react'
   - component 引用 TransformNode
   - reactPorts 声明静态端口（动态端口由组件内 useEffect 管理）

3. 更新 types.ts 和 node-registry/index.ts

请完整输出上述文件的源代码。
```

---

### 提示词 C：修改已有节点属性

```
你是一名精通 React + AntV X6 的高级前端工程师。

请修改流编排引擎中的 **[节点类型名称]** 节点，所在文件：
`node-registry/nodes/[xxx].tsx`

**修改内容：**
[描述具体修改，例如：]
- 新增一个可选字段 retryCount（重试次数，默认 3）
- 在属性面板添加对应的 InputNumber 控件
- 节点标签从 "Email" 改为包含重试信息，例如 "Email (3 retries)"
- 修改默认端口，新增 error 输出端口

**注意：**
- 修改 defaults.data 加入 retryCount: 3
- 修改 defaults.ports 加入 { id: 'error' }
- 修改 buildLabel 函数
- 修改 PropertyEditor 组件

只需输出修改后的完整 `node-registry/nodes/[xxx].tsx` 文件。
其他文件（adapter.ts、NodePropertyDrawer.tsx 等）无需修改。
```
