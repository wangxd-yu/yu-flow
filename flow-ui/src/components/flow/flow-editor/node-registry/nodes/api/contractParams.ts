// ============================================================================
// 从目标 Flow API 的 contract.request 提取入参列表，供 API 节点自动带出
// ============================================================================

import { createId } from '../../../utils/id';
import type { NodeVariable } from '../../shared/useNodeVariables';

export type ApiParamSource = 'query' | 'path' | 'body' | 'header';

export interface ContractParamDef {
    name: string;
    source: ApiParamSource;
    required: boolean;
    type?: string;
    title?: string;
}

interface SchemaNodeLike {
    id?: string;
    name?: string;
    title?: string;
    type?: string;
    required?: boolean;
    children?: SchemaNodeLike[];
}

const SOURCE_LABEL: Record<ApiParamSource, string> = {
    query: 'query',
    path: 'path',
    body: 'body',
    header: 'header',
};

export function paramSourceLabel(source?: ApiParamSource): string {
    return source ? SOURCE_LABEL[source] : '';
}

function unwrapRoot(nodes: SchemaNodeLike[] | undefined): SchemaNodeLike[] {
    if (!nodes?.length) return [];
    if (nodes.length === 1 && (nodes[0].id === 'root' || nodes[0].name === '根节点')) {
        return nodes[0].children || [];
    }
    return nodes.filter((n) => n.id !== 'root' && n.name !== '根节点');
}

function flattenLeaves(
    nodes: SchemaNodeLike[],
    source: ApiParamSource,
    prefix = '',
): ContractParamDef[] {
    const out: ContractParamDef[] = [];
    for (const n of nodes) {
        const rawName = (n.name || '').trim();
        if (!rawName) continue;
        const fullName = prefix ? `${prefix}.${rawName}` : rawName;
        const hasChildren = Array.isArray(n.children) && n.children.length > 0;
        if ((n.type === 'object' || n.type === 'array') && hasChildren && n.type === 'object') {
            out.push(...flattenLeaves(n.children!, source, fullName));
            continue;
        }
        // object 无 children / array / 标量：作为单个入参
        out.push({
            name: fullName,
            source,
            required: !!n.required,
            type: n.type,
            title: n.title,
        });
    }
    return out;
}

/**
 * 解析 API contract JSON，提取 query / path / body 入参（不含 headers）。
 */
export function extractContractParams(contractJson: string | object | null | undefined): ContractParamDef[] {
    if (!contractJson) return [];
    let contract: any;
    try {
        contract = typeof contractJson === 'string' ? JSON.parse(contractJson) : contractJson;
    } catch {
        return [];
    }
    const req = contract?.request;
    if (!req || typeof req !== 'object') return [];

    const list: ContractParamDef[] = [];
    list.push(...flattenLeaves(unwrapRoot(req.query), 'query'));
    list.push(...flattenLeaves(unwrapRoot(req.pathParams), 'path'));
    if (req.bodyType && req.bodyType !== 'none') {
        list.push(...flattenLeaves(unwrapRoot(req.body), 'body'));
    } else if (!req.bodyType) {
        // 旧数据可能无 bodyType，有 body 节点则仍带出
        const bodyNodes = unwrapRoot(req.body);
        if (bodyNodes.length) list.push(...flattenLeaves(bodyNodes, 'body'));
    }
    return list;
}

/**
 * 用契约入参生成变量行；按 name+source 保留已有 extractPath / 端口 id。
 */
export function mergeContractParamsIntoVariables(
    defs: ContractParamDef[],
    existing: NodeVariable[],
): NodeVariable[] {
    const prevByKey = new Map<string, NodeVariable>();
    for (const v of existing) {
        if (!v.name?.trim()) continue;
        const key = `${v.paramSource || ''}:${v.name.trim()}`;
        prevByKey.set(key, v);
        // 兼容旧数据无 paramSource：仅按 name 回退
        if (!prevByKey.has(`:${v.name.trim()}`)) {
            prevByKey.set(`:${v.name.trim()}`, v);
        }
    }

    const rows: NodeVariable[] = defs.map((d) => {
        const hit =
            prevByKey.get(`${d.source}:${d.name}`) ||
            prevByKey.get(`:${d.name}`);
        return {
            id: hit?.id || createId('var'),
            name: d.name,
            extractPath: hit?.extractPath?.trim() ? hit.extractPath : '',
            paramSource: d.source,
            required: d.required,
            fromContract: true,
        };
    });

    // 保留用户手动添加、且不在契约中的行
    const defKeys = new Set(defs.map((d) => `${d.source}:${d.name}`));
    const defNames = new Set(defs.map((d) => d.name));
    for (const v of existing) {
        if (!v.name?.trim() || v.fromContract) continue;
        const key = `${v.paramSource || ''}:${v.name.trim()}`;
        if (defKeys.has(key) || (!v.paramSource && defNames.has(v.name.trim()))) continue;
        rows.push({ ...v, fromContract: false });
    }

    rows.push({ id: createId('var'), name: '', extractPath: '$' });
    return rows;
}
