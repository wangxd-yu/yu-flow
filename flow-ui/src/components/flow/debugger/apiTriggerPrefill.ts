import type { SchemaNode, BodyType } from '@/components/flow/ApiContractDesigner/types';

/** 由 SchemaNode 树生成样例对象（默认值优先，否则按类型占位） */
export function buildSampleObjectFromNodes(nodes: SchemaNode[]): Record<string, unknown> {
  const result: Record<string, unknown> = {};
  for (const n of nodes || []) {
    const raw = (n.name || '').trim();
    if (!raw || raw === '根节点') {
      if (n.children?.length) {
        Object.assign(result, buildSampleObjectFromNodes(n.children));
      }
      continue;
    }
    result[raw] = sampleValueForNode(n);
  }
  return result;
}

export function sampleValueForNode(node: SchemaNode): unknown {
  if (node.defaultValue !== undefined && node.defaultValue !== '') {
    return coerceDefault(node.defaultValue, node.type);
  }
  switch (node.type) {
    case 'integer':
      return 0;
    case 'number':
      return 0;
    case 'boolean':
      return false;
    case 'null':
      return null;
    case 'array':
      return [];
    case 'object':
      return node.children?.length ? buildSampleObjectFromNodes(node.children) : {};
    default:
      return '';
  }
}

function coerceDefault(value: string | number, type: SchemaNode['type']): unknown {
  if (type === 'integer' || type === 'number') {
    const n = typeof value === 'number' ? value : Number(value);
    return Number.isFinite(n) ? n : value;
  }
  if (type === 'boolean') {
    if (typeof value === 'boolean') return value;
    return String(value).toLowerCase() === 'true';
  }
  return value;
}

/** 扁平区（query/headers/path）→ 字符串 Record，供 KV 编辑器预填 */
export function flattenNodesToStringRecord(nodes: SchemaNode[]): Record<string, string> {
  const obj = buildSampleObjectFromNodes(nodes || []);
  const out: Record<string, string> = {};
  Object.entries(obj).forEach(([k, v]) => {
    if (v == null) {
      out[k] = '';
    } else if (typeof v === 'object') {
      out[k] = JSON.stringify(v);
    } else {
      out[k] = String(v);
    }
  });
  return out;
}

export type ApiRequestContractSlice = {
  query?: SchemaNode[];
  pathParams?: SchemaNode[];
  headers?: SchemaNode[];
  body?: SchemaNode[];
  bodyType?: BodyType | string;
  rawBody?: string;
};

export type ApiTriggerPrefill = {
  headers: Record<string, string>;
  /** Query + Path 样例合并（调试无独立 Path 面板） */
  queryParams: Record<string, string>;
  body: string;
};

/**
 * API 契约 request → 调试触发器预填
 */
export function buildApiTriggerPrefillFromContract(
  request?: ApiRequestContractSlice | null,
): ApiTriggerPrefill {
  const headers = flattenNodesToStringRecord(request?.headers || []);
  const queryParams = {
    ...flattenNodesToStringRecord(request?.pathParams || []),
    ...flattenNodesToStringRecord(request?.query || []),
  };

  const bodyType = (request?.bodyType || 'none').toLowerCase();
  let body = '{\n  \n}';
  if (bodyType === 'json') {
    const sample = buildSampleObjectFromNodes(request?.body || []);
    body = JSON.stringify(sample, null, 2);
  } else if (bodyType === 'raw' || bodyType === 'xml') {
    body = request?.rawBody?.trim() ? request.rawBody : '';
  } else if (bodyType === 'form-data' || bodyType === 'x-www-form-urlencoded') {
    body = JSON.stringify(buildSampleObjectFromNodes(request?.body || []), null, 2);
  } else if (bodyType === 'none') {
    body = '';
  }

  return { headers, queryParams, body };
}

export function recordToKvEntries(
  record: Record<string, string> | undefined,
  createId: () => string,
): Array<{ key: string; value: string; enabled: boolean; id: string }> {
  const entries = Object.entries(record || {})
    .filter(([k]) => !!k.trim())
    .map(([key, value]) => ({
      key,
      value: value ?? '',
      enabled: true,
      id: createId(),
    }));
  if (entries.length === 0) {
    entries.push({ key: '', value: '', enabled: true, id: createId() });
  }
  return entries;
}
