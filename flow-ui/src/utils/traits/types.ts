import type { DataModel } from '../amisGenerator';

// ─── 基础枚举类型 ───────────────────────────────────────────────

/** 交互方式：dialog 弹窗 | drawer 抽屉 | ajax 异步请求 | local 本地操作 | page 跳转页面 */
export type InteractionType = 'dialog' | 'drawer' | 'ajax' | 'local' | 'page';

/** 插件可放置的物理位置 */
export type PositionType = 'toolbar' | 'row' | 'batch' | 'inline';

/**
 * 插件所属分组，用于前端按物理区块渲染配置项。
 * - query:         视图与查询
 * - toolbar:       顶部工具栏
 * - row-action:    行级数据操作
 * - column-action: 列级操作（如列内按钮）
 * - advanced:      高级配置
 */
export type TraitGroup = 'query' | 'toolbar' | 'row-action' | 'column-action' | 'advanced';

// ─── 配置表单项 ─────────────────────────────────────────────────

/** 配置表单项类型 */
export type TraitConfigItemType =
  | 'input'
  | 'field-select'
  | 'field-select-multiple'
  | 'select'
  | 'switch';

/** 配置表单项 —— 描述插件配置弹窗中的单个表单控件 */
export interface TraitConfigItem {
  /** 字段名（对应配置对象的 key） */
  name: string;
  /** 显示标签 */
  label: string;
  /** 控件类型 */
  type: TraitConfigItemType;
  /** 默认值 */
  defaultValue: any;
  /** 是否必填 */
  required?: boolean;
  /** 提示文案（hover 时显示） */
  tooltip?: string;
  /**
   * 可见性联动表达式（字符串）。
   * 当表达式值为 true 时该项可见，用于表单项之间的动态联动。
   * 示例：'${isComposite === true}'
   */
  visibleOn?: string;
  /** 下拉选项（仅 type 为 'select' 时使用） */
  options?: { label: string; value: string | number }[];
}

// ─── API 端点声明 ───────────────────────────────────────────────

export interface TraitApiItem {
  action: string;
  name: string;
  method: 'GET' | 'POST' | 'PUT' | 'DELETE';
  url: string;
}

// ─── 向导能力行模型 (Capability Row) ───────────────────────────

export interface CapabilityRow {
  id: string;
  name: string;
  position: PositionType;
  interaction: InteractionType;
  dataStrategy: 'generate' | 'bind' | 'localData';
  apiMethod: 'GET' | 'POST' | 'PUT' | 'DELETE';
  apiPath: string;
  conditionType: 'always' | 'conditional';
  conditionField: string;
  conditionOperator: string;
  conditionValue: string;
  sourceTrait?: string;
  traitConfig?: Record<string, any>;
}

// ─── 功能插件协议 ───────────────────────────────────────────────

export interface FeatureTrait {
  code: string;
  name: string;

  /** 插件所属分组 */
  group: TraitGroup;

  /** 是否默认启用 */
  isDefault?: boolean;

  /** 插件配置表单结构（元数据驱动 UI） */
  configSchema?: TraitConfigItem[];

  // ── UI 约束元数据 ──────────────────────────────────────────

  /**
   * 允许的交互方式列表。
   * UI 层据此渲染"交互方式"下拉框的可选项。
   * 例如：['dialog', 'drawer'] 表示仅支持弹窗和抽屉。
   */
  allowedInteractions?: InteractionType[];

  /**
   * 允许的放置位置列表。
   * UI 层据此约束该插件可以出现在页面的哪些区域。
   * 例如：['toolbar'] 表示仅能放在顶部工具栏。
   */
  allowedPositions?: PositionType[];

  /**
   * 是否为复合动作。
   * 复合动作通常包含正/反两种操作（如：上架/下架、启用/停用），
   * UI 层可据此渲染不同的配置界面。
   */
  isComposite?: boolean;

  /**
   * 是否隐藏"数据获取策略"配置区域。
   * 某些仅做写操作的插件无需配置数据获取策略，可将此项设为 true。
   */
  hideDataStrategy?: boolean;

  // ── 核心能力方法 ──────────────────────────────────────────

  /** 1. API 注入能力：声明该插件需要的 API 端点 */
  injectApis: (moduleCode: string, config: Record<string, any>) => TraitApiItem[];

  /** 2. 后端生成能力：生成该插件对应的低代码 API 配置 (含 SQL) */
  generateBackendApi?: (model: DataModel, commonProps: any, baseUrl: string, cap?: CapabilityRow) => any;

  /** 3. 前端生成能力：注入 Amis Schema 的特定片段 */
  getAmisNode?: (cap: CapabilityRow, context: any) => any;

  /** 4. AST 突变逻辑：允许插件直接对组装完成的 AST 进行改写 */
  mutateAmisSchema?: (schema: any, cap: CapabilityRow, context: any) => void;
}
