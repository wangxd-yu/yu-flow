/**
 * schemaUtils.ts
 * JSON Schema ↔ SchemaNode[] 互转工具函数
 */
import type { SchemaNode, SchemaType } from './types';

// ─── ID 生成 ────────────────────────────────────────────────

let _cnt = 0;
const uid = (): string => `s_${Date.now()}_${++_cnt}`;

// ═══════════════════════════════════════════════════════════════
// SchemaNode[] → OpenAPI 3.0 JSON Schema
// ═══════════════════════════════════════════════════════════════

interface JsonSchema {
  type?: string;
  properties?: Record<string, JsonSchema>;
  items?: JsonSchema;
  required?: string[];
  description?: string;
  default?: any;
  format?: string;
  pattern?: string;
  minLength?: number;
  maxLength?: number;
  enum?: string[];
  // number
  minimum?: number;
  maximum?: number;
  exclusiveMinimum?: boolean;
  exclusiveMaximum?: boolean;
  multipleOf?: number;
  // array
  minItems?: number;
  maxItems?: number;
  uniqueItems?: boolean;
  [k: string]: any;
}

/** 单个 SchemaNode → JSON Schema property */
const nodeToSchema = (node: SchemaNode): JsonSchema => {
  const schema: JsonSchema = { type: node.type };

  if (node.description) schema.description = node.description;
  if (node.title) schema['x-title'] = node.title;
  if (node.defaultValue !== undefined && node.defaultValue !== '') schema.default = node.defaultValue;
  if (node.format) schema.format = node.format;
  if (node.pattern) schema.pattern = node.pattern;
  if (node.minLength !== undefined) schema.minLength = node.minLength;
  if (node.maxLength !== undefined) schema.maxLength = node.maxLength;
  if (node.enum?.length) schema.enum = node.enum;
  if (node.mock) schema['x-mock'] = node.mock;

  // number 类型校验字段
  if (node.minimum !== undefined) schema.minimum = node.minimum;
  if (node.maximum !== undefined) schema.maximum = node.maximum;
  if (node.exclusiveMinimum) schema.exclusiveMinimum = node.exclusiveMinimum;
  if (node.exclusiveMaximum) schema.exclusiveMaximum = node.exclusiveMaximum;
  if (node.multipleOf !== undefined) schema.multipleOf = node.multipleOf;

  // array 类型校验字段
  if (node.minItems !== undefined) schema.minItems = node.minItems;
  if (node.maxItems !== undefined) schema.maxItems = node.maxItems;
  if (node.uniqueItems) schema.uniqueItems = node.uniqueItems;

  if (node.type === 'object' && node.children?.length) {
    schema.properties = {};
    const req: string[] = [];
    for (const child of node.children) {
      schema.properties[child.name || `field_${child.id}`] = nodeToSchema(child);
      if (child.required) req.push(child.name || `field_${child.id}`);
    }
    if (req.length) schema.required = req;
  }

  if (node.type === 'array' && node.children?.length) {
    // array 的第一个 child 作为 items 模板
    schema.items = nodeToSchema(node.children[0]);
  }

  return schema;
};

/**
 * 将 SchemaNode[] 转换为标准 JSON Schema (Draft 7) 对象。
 * 顶层始终作为 object 处理（根节点 children 作为 properties）。
 */
export const treeToSchema = (nodes: SchemaNode[]): JsonSchema => {
  const buildBody = (): JsonSchema => {
    // 如果外层只有一个 root 节点，取其整体
    if (nodes.length === 1 && nodes[0].id === 'root') {
      return nodeToSchema(nodes[0]);
    }
    // 否则包一层 object
    const root: JsonSchema = { type: 'object', properties: {} };
    const req: string[] = [];
    for (const node of nodes) {
      const key = node.name || `field_${node.id}`;
      root.properties![key] = nodeToSchema(node);
      if (node.required) req.push(key);
    }
    if (req.length) root.required = req;
    return root;
  };

  return {
    $schema: 'http://json-schema.org/draft-07/schema#',
    ...buildBody(),
  };
};

// ═══════════════════════════════════════════════════════════════
// JSON Schema → SchemaNode[]
// ═══════════════════════════════════════════════════════════════

const VALID_TYPES = new Set(['string', 'number', 'integer', 'boolean', 'object', 'array']);

const mapType = (t: string | string[] | undefined): SchemaType => {
  if (Array.isArray(t)) return mapType(t[0]);
  switch (t) {
    case 'string': case 'number': case 'boolean':
      return t as SchemaType;
    case 'integer':
      return 'number';
    case 'object': return 'object';
    case 'array': return 'array';
    default: return 'string';
  }
};

const schemaPropertyToNode = (
  name: string,
  schema: JsonSchema,
  requiredSet: Set<string>,
  depth: number = 0,
): SchemaNode => {
  // 防止无限递归（实际业务中 10 层嵌套已足够深）
  if (depth > 20) {
    throw new Error(`嵌套层级过深（超过 20 层），字段路径包含 "${name}"，请检查 Schema 是否存在循环引用`);
  }

  const rawType = Array.isArray(schema.type) ? schema.type[0] : schema.type;
  if (rawType && !VALID_TYPES.has(rawType)) {
    // 不阻断，降级为 string 并记录
    console.warn(`[schemaToTree] 不支持的类型 "${rawType}"，字段 "${name}" 已自动降级为 string`);
  }

  const type = mapType(schema.type);
  const node: SchemaNode = {
    id: uid(),
    name,
    title: schema['x-title'],
    type,
    required: requiredSet.has(name),
    description: schema.description ?? '',
    defaultValue: schema.default,
    mock: schema['x-mock'],
    format: schema.format,
    pattern: schema.pattern,
    minLength: schema.minLength,
    maxLength: schema.maxLength,
    enum: schema.enum,
    // number
    minimum: schema.minimum,
    maximum: schema.maximum,
    exclusiveMinimum: schema.exclusiveMinimum,
    exclusiveMaximum: schema.exclusiveMaximum,
    multipleOf: schema.multipleOf,
    // array
    minItems: schema.minItems,
    maxItems: schema.maxItems,
    uniqueItems: schema.uniqueItems,
  };

  if (type === 'object' && schema.properties) {
    const childReq = new Set(schema.required ?? []);
    node.children = Object.entries(schema.properties).map(([k, v]) =>
      schemaPropertyToNode(k, v as JsonSchema, childReq, depth + 1),
    );
  }

  if (type === 'array' && schema.items) {
    node.children = [schemaPropertyToNode('items', schema.items as JsonSchema, new Set(), depth + 1)];
  }

  return node;
};

/**
 * 解析 JSON Schema 字符串，生成带 id 的 SchemaNode[]。
 * 返回以 root 节点包裹的结果。
 *
 * 内置完善的异常处理：
 * - JSON 解析失败 → 抛出明确中文提示
 * - 非 object 类型输入 → 抛出提示
 * - 子节点解析失败 → 抛出定位信息
 */
export const schemaToTree = (schemaStr: string): SchemaNode[] => {
  // ── Step 1: 空输入检测 ──
  const trimmed = schemaStr?.trim();
  if (!trimmed) {
    throw new Error('输入内容为空，请粘贴有效的 JSON Schema');
  }

  // ── Step 2: JSON 解析 ──
  let schema: JsonSchema;
  try {
    schema = JSON.parse(trimmed);
  } catch (e: any) {
    // 提取 JSON.parse 的错误位置信息
    const posMatch = e.message?.match(/position\s+(\d+)/i);
    const posHint = posMatch ? `（大约在第 ${posMatch[1]} 个字符处）` : '';
    throw new Error(`JSON 格式不合法${posHint}，请检查括号、引号和逗号等是否正确`);
  }

  // ── Step 3: 基本结构校验 ──
  if (typeof schema !== 'object' || schema === null || Array.isArray(schema)) {
    throw new Error('JSON Schema 必须是一个对象（Object），不能是数组或原始值');
  }

  // ── Step 4: 根类型推断与转换 ──
  try {
    if (schema.type === 'object' || schema.properties) {
      // 最常见路径：根节点为 object
      const reqSet = new Set(schema.required ?? []);
      const children = schema.properties
        ? Object.entries(schema.properties).map(([k, v]) =>
            schemaPropertyToNode(k, v as JsonSchema, reqSet),
          )
        : [];
      return [{
        id: 'root',
        name: '根节点',
        type: 'object',
        required: false,
        description: schema.description ?? '',
        children,
      }];
    }

    if (schema.type === 'array') {
      const root: SchemaNode = {
        id: 'root',
        name: '根节点',
        type: 'array',
        required: false,
        description: schema.description ?? '',
      };
      if (schema.items) {
        root.children = [schemaPropertyToNode('items', schema.items as JsonSchema, new Set())];
      }
      return [root];
    }

    // 其他基本类型也作为单根返回
    return [{
      id: 'root',
      name: '根节点',
      type: mapType(schema.type),
      required: false,
      description: schema.description ?? '',
    }];
  } catch (e: any) {
    // 将递归过程中抛出的异常统一包装
    throw new Error(e.message || 'Schema 结构解析失败，请检查字段定义是否正确');
  }
};
