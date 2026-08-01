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
  | 'api'          // 内部 Flow API 编排调用
  | 'httpRequest'
  | 'for'          // Scatter-Gather: 分发节点（Fire-and-Forget 并发发射）
  | 'forEach'      // 串行循环：item → done
  | 'parallel'     // 并行网关：图扇出
  | 'delay'        // 延迟等待
  | 'sendMail'     // 发送邮件
  | 'errorHandler' // 统一错误处理入口
  | 'record'
  | 'response'
  | 'request'
  | 'schedule'
  | 'mqTrigger'    // MQ 消息触发入口（MQ 任务专用）
  | 'mqSend'       // 发送 MQ 消息
  | 'service'
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

/** Switch / Condition 单条分支（出口 case_<id>） */
export interface SwitchCaseData {
  id: string;
  name: string;
  value: string;
}

export interface SwitchNodeData {
  inputs?: InputsMap;
  expression: string;
  language?: ExpressionLanguage;
  cases?: SwitchCaseData[];
}

/** 内部编排调用（Flow API 或内部服务） */
export interface ApiNodeData {
  inputs?: InputsMap;
  /** 目标类型：api（默认）| service */
  targetType?: 'api' | 'service';
  /** 目标实体 ID（Flow API 或内部服务） */
  serviceId?: string;
  /** 展示用名称 */
  __serviceName?: string;
  __serviceMethod?: string;
  __serviceUrl?: string;
  /** 可选：额外写入的上下文变量名 */
  output?: string;
}

export interface HttpRequestNodeData {
  inputs?: InputsMap;
  url: string;
  method: string;
  headers?: Record<string, string>;
  params?: Record<string, string>;
  body?: string;
  timeout?: number;
  /** none | bearer | basic | apiKey */
  authType?: string;
  authToken?: string;
  authUsername?: string;
  authPassword?: string;
  /** header | query */
  authApiKeyIn?: string;
  authApiKeyName?: string;
  authApiKeyValue?: string;
}

/** 串行 ForEach 节点数据契约 */
export interface ForEachNodeData {
  inputs?: InputsMap;
}

/** Delay 节点数据契约 */
export interface DelayNodeData {
  inputs?: InputsMap;
  /** 等待毫秒 */
  delayMs?: number;
}

/** 发送邮件节点 */
export interface SendMailNodeData {
  inputs?: InputsMap;
  to?: string;
  cc?: string;
  bcc?: string;
  subject?: string;
  text?: string;
  html?: string;
}

/** Parallel 并行网关 */
export interface ParallelNodeData {
  errorMode?: 'FAST_FAIL' | 'CONTINUE';
}

/** For (Scatter 分发) 节点数据契约 */
export interface ForNodeData {
  inputs?: InputsMap;
  /** 【核心配置】配对的 Collect 节点 ID，用于空数组防死锁旁路 */
  collectStepId?: string;
  /** 超时毫秒，默认 30000 */
  timeoutMs?: number;
}

export type RecordValueType = 'string' | 'number' | 'boolean' | 'null';
export type RecordFieldSource = 'wire' | 'literal' | 'placeholder';

export interface RecordFieldItem {
  id: string;
  key: string;
  value: string;
  /** wire=传入连线；literal=固定值；placeholder=底部可连线占位 */
  source: RecordFieldSource;
  valueType?: RecordValueType;
}

export interface RecordNodeData {
  inputs?: InputsMap;
  /** 输出对象 schema：key → 字面量(含 number/bool/null) / JsonPath / ${ref} */
  schema: Record<string, string | number | boolean | null | { id?: string; extractPath?: string }>;
  /** UI 有序列表（与 schema 同步） */
  __fields?: RecordFieldItem[];
  themeColor?: string;
  __label?: string;
}

export interface ResponseNodeData {
  status: number | string;
  headers?: Record<string, string>;
  body?: string | Record<string, unknown>;
}

/** 对齐后端 ValidationRule */
export interface ValidationRuleData {
  required?: boolean;
  /** phone | email | regex | range | length */
  type?: string;
  pattern?: string;
  min?: number;
  max?: number;
  message?: string;
}

export interface RequestNodeData {
  method?: string;
  validations?: Record<string, ValidationRuleData>;
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
    label: 'Switch',
    category: '逻辑节点',
    color: '#722ed1',
    defaultPorts: [
      { id: 'in:payload', group: 'absolute-in-solid' },
      { id: 'default', group: 'absolute-out-solid' },
    ],
  },
  httpRequest: {
    type: 'httpRequest',
    label: 'HTTP 请求',
    category: '调用节点',
    color: '#fa8c16',
    defaultPorts: [
      { id: 'in', group: 'absolute-in-solid' },
      { id: 'success', group: 'absolute-out-solid' },
      { id: 'fail', group: 'absolute-out-hollow' },
    ],
  },
  api: {
    type: 'api',
    label: 'API 调用 (API Call)',
    category: '调用节点',
    color: '#2f54eb',
    defaultPorts: [
      { id: 'in:payload', group: 'absolute-in-solid' },
      { id: 'out', group: 'absolute-out-solid' },
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
  forEach: {
    type: 'forEach',
    label: 'ForEach (串行)',
    category: '循环节点',
    color: '#0d9488',
    defaultPorts: [
      { id: 'in', group: 'left' },
      { id: 'item', group: 'right' },
      { id: 'done', group: 'right' },
    ],
  },
  parallel: {
    type: 'parallel',
    label: '并行 (Parallel)',
    category: '循环节点',
    color: '#6366f1',
    defaultPorts: [
      { id: 'in', group: 'left' },
      { id: 'out', group: 'right' },
    ],
  },
  delay: {
    type: 'delay',
    label: '延迟 (Delay)',
    category: '逻辑节点',
    color: '#0891b2',
    defaultPorts: [
      { id: 'in', group: 'left' },
      { id: 'out', group: 'right' },
    ],
  },
  sendMail: {
    type: 'sendMail',
    label: '发送邮件 (Send Mail)',
    category: '调用节点',
    color: '#ea580c',
    defaultPorts: [
      { id: 'in:payload', group: 'absolute-in-solid' },
      { id: 'out', group: 'absolute-out-solid' },
    ],
  },
  errorHandler: {
    type: 'errorHandler',
    label: '错误处理 (ErrorHandler)',
    category: '逻辑节点',
    color: '#dc2626',
    defaultPorts: [
      { id: 'out', group: 'right' },
    ],
  },
  record: {
    type: 'record',
    label: '数据构造 (Record)',
    category: '数据节点',
    color: '#1677ff',
    defaultPorts: [
      { id: 'in:payload', group: 'absolute-in-solid' },
      { id: 'out', group: 'absolute-out-solid' },
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
  schedule: {
    type: 'schedule',
    label: '调度入口 (Schedule)',
    category: '基础节点',
    color: '#722ed1',
    defaultPorts: [
      { id: 'out', group: 'right' },
    ],
  },
  mqTrigger: {
    type: 'mqTrigger',
    label: '消息触发 (MQ Trigger)',
    category: '基础节点',
    color: '#0958d9',
    defaultPorts: [
      { id: 'out', group: 'right' },
    ],
    singleton: true,
  },
  mqSend: {
    type: 'mqSend',
    label: '发送消息 (MQ Send)',
    category: '调用节点',
    color: '#13c2c2',
    defaultPorts: [
      { id: 'in:payload', group: 'absolute-in-solid' },
      { id: 'out', group: 'absolute-out-solid' },
    ],
  },
  service: {
    type: 'service',
    label: '服务入口 (Service)',
    category: '基础节点',
    color: '#1677ff',
    defaultPorts: [
      { id: 'out', group: 'right' },
    ],
  },
  template: {
    type: 'template',
    label: '模板 (Template)',
    category: '数据节点',
    color: '#9254de',
    defaultPorts: [
      { id: 'in:payload', group: 'absolute-in-solid' },
      { id: 'out', group: 'absolute-out-solid' },
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
