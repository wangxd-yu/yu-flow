/**
 * API Contract Designer — 核心类型定义
 * 参考精简版 OpenAPI 规范，用于描述一个 API 接口的完整契约数据结构。
 */

/** HTTP 请求方法枚举 */
export type HttpMethod = 'GET' | 'POST' | 'PUT' | 'DELETE' | 'PATCH';

/** 字段数据类型枚举 */
export type SchemaType = 'string' | 'number' | 'boolean' | 'object' | 'array';

/** Body 内容类型枚举 */
export type BodyType = 'none' | 'form-data' | 'x-www-form-urlencoded' | 'json' | 'xml' | 'raw';

// ─── 核心树形节点 ─────────────────────────────────────────────

/**
 * SchemaNode — 描述参数层级的树形节点
 * 当 type 为 object 或 array 时，children 存放子字段定义。
 * 包含 JSON Schema 高级校验字段用于 Apifox 风格的弹层配置。
 */
export interface SchemaNode {
  /** 唯一标识（用于树形表格的 key） */
  id: string;
  /** 字段名 */
  name: string;
  /** 中文名 / 显示名 */
  title?: string;
  /** 数据类型 */
  type: SchemaType;
  /** 是否必填 */
  required: boolean;
  /** 字段说明 */
  description: string;
  /** 默认值 */
  defaultValue?: string | number;
  /** 子节点（type 为 object 或 array 时使用） */
  children?: SchemaNode[];

  // ─── JSON Schema 高级校验字段 ──────────────────────────────
  /** Mock 占位符 (如 @name, @email) */
  mock?: string;
  /** 格式约束 (如 email, uri, date-time) */
  format?: string;
  /** 最小长度 (字符串) */
  minLength?: number;
  /** 最大长度 (字符串) */
  maxLength?: number;
  /** 正则表达式 */
  pattern?: string;
  /** 枚举值列表 */
  enum?: string[];
}

// ─── 基础信息 ────────────────────────────────────────────────

/** BaseInfo — 接口基础信息 */
export interface BaseInfo {
  /** 路由路径 */
  path: string;
  /** HTTP 方法 */
  method: HttpMethod;
  /** 接口名称 */
  summary: string;
  /** 所属分类标签 */
  tags: string[];
}

// ─── 请求配置 ────────────────────────────────────────────────

/** RequestConfig — 请求参数配置 */
export interface RequestConfig {
  /** 请求头参数 */
  headers: SchemaNode[];
  /** 查询参数 */
  query: SchemaNode[];
  /** 请求体参数 */
  body: SchemaNode[];
  /** Body 内容类型 */
  bodyType: BodyType;
  /** raw / xml 模式下的纯文本内容 */
  rawBody?: string;
}

// ─── 响应配置 ────────────────────────────────────────────────

/** ResponseConfig — 单条响应配置 */
export interface ResponseConfig {
  /** HTTP 状态码 (如 200, 400, 500) */
  statusCode: number;
  /** 响应描述 */
  description: string;
  /** 响应体参数 */
  body: SchemaNode[];
}

// ─── 根对象 ──────────────────────────────────────────────────

/** ApiContract — 接口契约根对象 */
export interface ApiContract {
  /** 基础信息 */
  baseInfo: BaseInfo;
  /** 请求参数 */
  request: RequestConfig;
  /** 出参字典：key 为状态码字符串，value 为对应的响应配置 */
  responses: Record<string, ResponseConfig>;
}
