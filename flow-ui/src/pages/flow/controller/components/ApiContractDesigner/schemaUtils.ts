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
 * 将 SchemaNode[] 转换为标准 OpenAPI 3.0 JSON Schema 对象。
 * 顶层始终作为 object 处理（根节点 children 作为 properties）。
 */
export const treeToSchema = (nodes: SchemaNode[]): JsonSchema => {
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

// ═══════════════════════════════════════════════════════════════
// JSON Schema → SchemaNode[]
// ═══════════════════════════════════════════════════════════════

const mapType = (t: string | string[] | undefined): SchemaType => {
  if (Array.isArray(t)) return (t[0] as SchemaType) || 'string';
  switch (t) {
    case 'string': case 'number': case 'boolean': case 'integer':
      return t === 'integer' ? 'number' : t as SchemaType;
    case 'object': return 'object';
    case 'array': return 'array';
    default: return 'string';
  }
};

const schemaPropertyToNode = (
  name: string,
  schema: JsonSchema,
  requiredSet: Set<string>,
): SchemaNode => {
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
  };

  if (type === 'object' && schema.properties) {
    const childReq = new Set(schema.required ?? []);
    node.children = Object.entries(schema.properties).map(([k, v]) =>
      schemaPropertyToNode(k, v as JsonSchema, childReq),
    );
  }

  if (type === 'array' && schema.items) {
    node.children = [schemaPropertyToNode('items', schema.items as JsonSchema, new Set())];
  }

  return node;
};

/**
 * 解析 JSON Schema 字符串，生成带 id 的 SchemaNode[]。
 * 返回以 root 节点包裹的结果。
 */
export const schemaToTree = (schemaStr: string): SchemaNode[] => {
  const schema: JsonSchema = JSON.parse(schemaStr);

  if (schema.type === 'object' && schema.properties) {
    const reqSet = new Set(schema.required ?? []);
    const children = Object.entries(schema.properties).map(([k, v]) =>
      schemaPropertyToNode(k, v as JsonSchema, reqSet),
    );
    return [{
      id: 'root',
      name: '根节点',
      type: 'object',
      required: false,
      description: schema.description ?? '',
      children,
    }];
  }

  // 非 object 根类型：单节点返回
  return [{
    id: 'root',
    name: '根节点',
    type: mapType(schema.type),
    required: false,
    description: schema.description ?? '',
  }];
};
