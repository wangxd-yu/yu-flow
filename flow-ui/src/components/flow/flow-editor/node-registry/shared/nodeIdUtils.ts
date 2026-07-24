// ============================================================================
// 节点 ID：唯一校验、重命名、重写 JsonPath / 业务字段引用
// ============================================================================

import { message } from 'antd';
import type { Graph, Node } from '@antv/x6';

/** 合法节点 ID：字母/下划线开头，字母数字下划线 */
export const NODE_ID_PATTERN = /^[a-zA-Z_][a-zA-Z0-9_]*$/;

export function isValidNodeId(id: string): boolean {
    return NODE_ID_PATTERN.test((id || '').trim());
}

/** 图内是否已占用该 cell id（可排除当前节点） */
export function isNodeIdTaken(graph: Graph, id: string, exceptCellId?: string): boolean {
    const cell = graph.getCellById(id);
    if (!cell) return false;
    if (exceptCellId && cell.id === exceptCellId) return false;
    return true;
}

/** 在已有 ID 上追加后缀直到图内唯一 */
export function ensureUniqueNodeId(graph: Graph | null | undefined, desiredId: string): string {
    const base = (desiredId || 'node').trim() || 'node';
    if (!graph) return base;
    if (!isNodeIdTaken(graph, base)) return base;
    let n = 2;
    while (isNodeIdTaken(graph, `${base}_${n}`)) n += 1;
    return `${base}_${n}`;
}

export function rewriteNodeIdInPath(
    path: string | undefined | null,
    oldId: string,
    newId: string,
): string {
    const p = (path ?? '').trim();
    if (!p || !oldId || oldId === newId) return p;
    const prefix = `$.${oldId}`;
    if (p === prefix) return `$.${newId}`;
    if (p.startsWith(`${prefix}.`) || p.startsWith(`${prefix}[`)) {
        return `$.${newId}${p.slice(prefix.length)}`;
    }
    return p;
}

function rewriteIdInText(text: string | undefined | null, oldId: string, newId: string): string {
    if (text == null || typeof text !== 'string' || !text.includes(`$.${oldId}`)) {
        return text as string;
    }
    return text.split(`$.${oldId}`).join(`$.${newId}`);
}

function rewriteValueDeep(val: any, oldId: string, newId: string): any {
    if (val == null) return val;
    if (typeof val === 'string') {
        if (val === oldId) return newId;
        return rewriteIdInText(val, oldId, newId);
    }
    if (Array.isArray(val)) {
        return val.map((item) => rewriteValueDeep(item, oldId, newId));
    }
    if (typeof val === 'object') {
        const out: Record<string, any> = {};
        for (const [k, v] of Object.entries(val)) {
            if (k === 'extractPath' && typeof v === 'string') {
                out[k] = rewriteNodeIdInPath(v, oldId, newId);
            } else {
                out[k] = rewriteValueDeep(v, oldId, newId);
            }
        }
        return out;
    }
    return val;
}

/** 遍历图中所有节点 data，重写对 oldId 的 JsonPath / collectStepId 等引用 */
export function rewriteNodeIdReferences(graph: Graph, oldId: string, newId: string) {
    if (!oldId || oldId === newId) return;
    graph.getNodes().forEach((n) => {
        const data = n.getData() as any;
        if (!data || typeof data !== 'object') return;
        const next = rewriteValueDeep(data, oldId, newId);
        if (next.collectStepId === oldId) next.collectStepId = newId;
        n.setData(next, { overwrite: true });
    });
}

export type RenameNodeIdResult =
    | { ok: true; cell: Node }
    | { ok: false; error: string };

/**
 * 重命名节点 ID：先重写图内引用，再 updateCellId（会 clone 节点并更新边终端）。
 */
export function renameFlowNodeId(node: Node, newIdRaw: string): RenameNodeIdResult {
    const graph = node.model?.graph as Graph | undefined;
    if (!graph) return { ok: false, error: '画布未就绪' };

    const newId = (newIdRaw || '').trim();
    if (!newId) return { ok: false, error: '节点 ID 不能为空' };
    if (!isValidNodeId(newId)) {
        return { ok: false, error: 'ID 需以字母或下划线开头，仅含字母/数字/下划线' };
    }
    if (newId === node.id) return { ok: true, cell: node };

    if (isNodeIdTaken(graph, newId, node.id)) {
        return { ok: false, error: `ID「${newId}」已被占用` };
    }

    const oldId = node.id;
    rewriteNodeIdReferences(graph, oldId, newId);
    const cell = graph.updateCellId(node, newId) as Node;
    try {
        graph.trigger('node:id-renamed', { oldId, newId, cell });
    } catch {
        /* ignore */
    }
    return { ok: true, cell };
}

/** Header 双击改 ID */
export function commitFlowNodeIdChange(node: Node, newId: string): boolean {
    const result = renameFlowNodeId(node, newId);
    if (!result.ok) {
        message.error(result.error);
        return false;
    }
    message.success(`节点 ID → ${newId}`);
    return true;
}
