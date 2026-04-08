// ============================================================================
// Flow DSL V3.1 Type Definitions
// 后端 DSL 规范的 TypeScript 映射 —— Single Source of Truth
// ============================================================================

// ── 端口定义 ──────────────────────────────────────────────────────
export type DslPortGroup = 'out-solid' | 'out-hollow' | 'in-solid' | 'in-hollow' | 'absolute-out-solid' | 'absolute-out-hollow' | 'absolute-in-solid' | 'absolute-in-hollow' | 'left' | 'right' | 'top' | 'bottom' | 'manual'
  | 'data'    // Scatter-Gather 数据流端口组（圆形）
  | 'control'
  | 'group-solid'
  | 'group-hollow';

export interface DslPort {
  id: string;
  group?: DslPortGroup;
}

// ── 数据映射 (extractPath) ────────────────────────────────────────
/** 单个输入映射 */
export interface InputMapping {
  extractPath: string;  // JSONPath, e.g. "$.start.args.name"
}

/** 节点 data.inputs 对象 */
export type InputsMap = Record<string, InputMapping | string>;

// ── 表达式语言 ──────────────────────────────────────────────────
export type ExpressionLanguage = 'aviator' | 'spel';

// ── 节点类型枚举 ────────────────────────────────────────────────
export type DslNodeType =
  | 'evaluate'
  | 'if'
  | 'switch'
  | 'serviceCall'
  | 'httpRequest'
  | 'for'          // Scatter-Gather: 分发节点（Fire-and-Forget 并发发射）
  | 'record'
  | 'response'
  | 'request'
  | 'template'
  | 'collect'      // Scatter-Gather: 汇聚屏障（线程接力并发 Barrier）
  | 'database'
  | 'systemVar'
  | 'systemMethod';

// ── 各节点 Data 定义 ──────────────────────────────────────────────



export interface EvaluateNodeData {
  inputs?: InputsMap;
  expression: string;
  language?: ExpressionLanguage;
}

export interface IfNodeData {
  inputs?: InputsMap;
  condition: string;
  language?: ExpressionLanguage;
}

export interface SwitchNodeData {
  inputs?: InputsMap;
  expression: string;
}

export interface ServiceCallNodeData {
  inputs?: InputsMap;
  service: string;
  method: string;
  args?: string[];
}

export interface HttpRequestNodeData {
  inputs?: InputsMap;
  url: string;
  method: string;
  headers?: Record<string, string>;
  params?: Record<string, string>;
  body?: string;
  timeout?: number;
}

// ForEachNodeData 已删除：旧 forEach 节点已被 ForStep(for) 替代

/** For (Scatter 分发) 节点数据契约 */
export interface ForNodeData {
  inputs?: InputsMap;
  /** 【核心配置】配对的 Collect 节点 ID，用于空数组防死锁旁路 */
  collectStepId?: string;
  /** 超时毫秒，默认 30000 */
  timeoutMs?: number;
}

export interface RecordNodeData {
  inputs?: InputsMap;
  schema: Record<string, string>;
}

export interface ResponseNodeData {
  status: number | string;
  headers?: Record<string, string>;
  body?: string | Record<string, unknown>;
}

export interface RequestNodeData {
  validations?: Record<string, string | number | boolean>;
}

export interface TemplateNodeData {
  inputs?: InputsMap;
  template: string;
}

/** Collect (Gather 汇聚屏障) 节点数据契约 */
export interface CollectNodeData {
  inputs?: InputsMap;
  /** 超时毫秒，默认 30000 */
  timeoutMs?: number;
}

export interface DatabaseNodeData {
  datasourceId?: string;
  sqlType: 'SELECT' | 'INSERT' | 'UPDATE' | 'DELETE';
  returnType?: 'LIST' | 'OBJECT' | 'PAGE';
  sql: string;
  inputs?: InputsMap;
}

export interface SystemVarNodeData {
  variableCode?: string;
  expression?: string;
}

export interface SystemMethodNodeData {
  methodCode?: string;
  expression?: string;
  inputs?: InputsMap;
}

/** Union type for all node data */
export type DslNodeData =
  | EvaluateNodeData
  | IfNodeData
  | SwitchNodeData
  | ServiceCallNodeData
  | HttpRequestNodeData
  | ForNodeData
  | RecordNodeData
  | ResponseNodeData
  | RequestNodeData
  | TemplateNodeData
  | CollectNodeData
  | DatabaseNodeData
  | SystemVarNodeData
  | SystemMethodNodeData;

// ── DSL 节点 ──────────────────────────────────────────────────────
export interface DslNode {
  id: string;
  type: DslNodeType;
  /** UI 坐标 - 在节点根级，非 data 内 */
  x?: number;
  y?: number;
  /** UI 尺寸 - 持久化用户 resize 后的大小 */
  width?: number;
  height?: number;
  /** 显式端口定义 */
  ports?: DslPort[];
  /** 节点业务数据（序列化兼容，强类型用 DslNodeData 引用各子接口） */
  data?: Record<string, any>;
  /** 显示标签 (可选，前端专用) */
  label?: string;
}

// ── DSL 边 ──────────────────────────────────────────────────────
export interface DslEdgeEndpoint {
  cell: string;
  port: string;
}

export interface DslEdge {
  source: DslEdgeEndpoint;
  target: DslEdgeEndpoint;
}

// ── DSL 根 ──────────────────────────────────────────────────────
export interface FlowDsl {
  id?: string;
  nodes: DslNode[];
  edges: DslEdge[];
}

// ============================================================================
// UI / 编辑器内部类型（非 DSL 导出契约）
// ============================================================================

export type FlowEditorProps = {
  value?: string;
  onChange?: (nextJsonScript: string) => void;
  height?: number;
};

// ── 节点类别配置 ────────────────────────────────────────────────
export interface NodeTypeConfig {
  type: DslNodeType;
  label: string;
  category: string;
  color: string;
  /** 默认端口 */
  defaultPorts: DslPort[];
  /** 是否允许多个 */
  singleton?: boolean;
}

// ── 端口布局映射 ─────────────────────────────────────────────
export const PORT_GROUP_MAP: Record<string, 'left' | 'right' | 'top' | 'bottom'> = {
  in: 'left',
  'in:headers': 'left',
  'in:body': 'left',
  // Scatter-Gather 数据流端口
  item: 'right',       // For 输出 / Collect 输入（数据流-圆形）
  list: 'right',       // Collect 输出（数据流-圆形）
  // Scatter-Gather 控制流端口（三角形语义）
  start: 'left',       // For 可选触发（控制流-三角形）
  finish: 'right',     // Collect 循环结束信号（控制流-三角形）
  out: 'right',
  true: 'right',
  false: 'right',
  done: 'right',
  default: 'right',
  headers: 'right',
  params: 'right',
  body: 'right',
};

// ── 所有节点默认端口配置 ─────────────────────────────────────
export const NODE_TYPE_CONFIGS: Record<DslNodeType, NodeTypeConfig> = {
  evaluate: {
    type: 'evaluate',
    label: '表达式 (Evaluate)',
    category: '逻辑节点',
    color: '#1677ff',
    defaultPorts: [
      { id: 'in', group: 'left' },
      { id: 'out', group: 'right' },
    ],
  },
  if: {
    type: 'if',
    label: '条件判断 (If)',
    category: '逻辑节点',
    color: '#1677ff',
    defaultPorts: [
      { id: 'in', group: 'left' },
      { id: 'true', group: 'right' },
      { id: 'false', group: 'right' },
    ],
  },
  switch: {
    type: 'switch',
    label: '多路选择 (Switch)',
    category: '逻辑节点',
    color: '#722ed1',
    defaultPorts: [
      { id: 'in', group: 'left' },
      { id: 'default', group: 'right' },
    ],
  },
  serviceCall: {
    type: 'serviceCall',
    label: '服务调用 (ServiceCall)',
    category: '调用节点',
    color: '#13c2c2',
    defaultPorts: [
      { id: 'in', group: 'left' },
      { id: 'out', group: 'right' },
    ],
  },
  httpRequest: {
    type: 'httpRequest',
    label: 'HTTP 请求',
    category: '调用节点',
    color: '#fa8c16',
    defaultPorts: [
      { id: 'in', group: 'left' },
      { id: 'out', group: 'right' },
    ],
  },
  for: {
    type: 'for',
    label: 'For (Loop)',
    category: '循环节点',
    color: '#7c3aed',
    defaultPorts: [
      { id: 'in', group: 'left' },    // list 数据流输入（圆形）
      { id: 'start', group: 'left' }, // 控制流触发（三角形，可选）
      { id: 'item', group: 'right' }, // 单元素数据流输出（圆形）
    ],
  },
  record: {
    type: 'record',
    label: '数据构造 (Record)',
    category: '数据节点',
    color: '#2f54eb',
    defaultPorts: [
      { id: 'in', group: 'left' },
      { id: 'out', group: 'right' },
    ],
  },
  response: {
    type: 'response',
    label: 'HTTP 响应 (Response)',
    category: '基础节点',
    color: '#FF6B35',
    defaultPorts: [
      { id: 'in', group: 'left' },
      { id: 'in:headers', group: 'left' },
      { id: 'in:body', group: 'left' },
    ],
  },
  request: {
    type: 'request',
    label: '请求入口 (Request)',
    category: '基础节点',
    color: '#52c41a',
    defaultPorts: [
      { id: 'headers', group: 'right' },
      { id: 'params', group: 'right' },
      { id: 'body', group: 'right' },
    ],
  },
  template: {
    type: 'template',
    label: '模板 (Template)',
    category: '数据节点',
    color: '#9254de',
    defaultPorts: [
      { id: 'in', group: 'left' },
      { id: 'out', group: 'right' },
    ],
  },
  collect: {
    type: 'collect',
    label: 'Collect (Gather)',
    category: '循环节点',
    color: '#0ea5e9',
    defaultPorts: [
      { id: 'item', group: 'left' },    // 数据流输入（圆形）—— 后端契约: item
      { id: 'list', group: 'right' },   // 聚合数组输出（数据流-圆形）
      { id: 'finish', group: 'right' }, // 收集完成信号（控制流-三角形）
    ],
  },
  database: {
    type: 'database',
    label: '数据库 (Database)',
    category: '调用节点',
    color: '#1677ff',
    defaultPorts: [
      { id: 'in', group: 'left' },
      { id: 'out', group: 'right' },
    ],
  },
  systemVar: {
    type: 'systemVar',
    label: '系统变量 (SystemVar)',
    category: '数据节点',
    color: '#34d399',
    defaultPorts: [
      { id: 'out', group: 'right' },
    ],
  },
  systemMethod: {
    type: 'systemMethod',
    label: '系统方法 (SystemMethod)',
    category: '调用节点',
    color: '#a855f7',
    defaultPorts: [
      { id: 'out', group: 'right' },
    ],
  }
};
