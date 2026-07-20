// ============================================================================
// 从内部服务 contract.inputs 提取入参，供 api 节点自动带出
// ============================================================================

import { createId } from '../../../utils/id';
import type { NodeVariable } from '../../shared/useNodeVariables';
import type { ContractParamDef } from './contractParams';
import { mergeContractParamsIntoVariables } from './contractParams';

interface SchemaNodeLike {
    id?: string;
    name?: string;
    title?: string;
    type?: string;
    required?: boolean;
    children?: SchemaNodeLike[];
}

function unwrapRoot(nodes: SchemaNodeLike[] | undefined): SchemaNodeLike[] {
    if (!nodes?.length) return [];
    if (nodes.length === 1 && (nodes[0].id === 'root' || nodes[0].name === '根节点')) {
        return nodes[0].children || [];
    }
    return nodes.filter((n) => n.id !== 'root' && n.name !== '根节点');
}

function flattenLeaves(nodes: SchemaNodeLike[], prefix = ''): ContractParamDef[] {
    const out: ContractParamDef[] = [];
    for (const n of nodes) {
        const rawName = (n.name || '').trim();
        if (!rawName) continue;
        const fullName = prefix ? `${prefix}.${rawName}` : rawName;
        const hasChildren = Array.isArray(n.children) && n.children.length > 0;
        if (n.type === 'object' && hasChildren) {
            out.push(...flattenLeaves(n.children!, fullName));
            continue;
        }
        out.push({
            name: fullName,
            source: 'body', // 服务入参统一进 $.service.input，用 body 标记以复用 UI
            required: !!n.required,
            type: n.type,
            title: n.title,
        });
    }
    return out;
}

/** 解析服务契约 JSON，提取 inputs 入参 */
export function extractServiceContractParams(
    contractJson: string | object | null | undefined,
): ContractParamDef[] {
    if (!contractJson) return [];
    let contract: any;
    try {
        contract = typeof contractJson === 'string' ? JSON.parse(contractJson) : contractJson;
    } catch {
        return [];
    }
    return flattenLeaves(unwrapRoot(contract?.inputs));
}

/**
 * 服务契约入参：有定义时锁定行（无占位「+」、不保留手动额外行）；
 * 无契约时仍可手动添加。
 */
export function mergeServiceContractIntoVariables(
    defs: ContractParamDef[],
    existing: NodeVariable[],
): NodeVariable[] {
    const merged = mergeContractParamsIntoVariables(defs, existing);
    if (defs.length === 0) return merged;
    return merged.filter((v) => v.fromContract && !!v.name?.trim());
}

/** 无契约时保留占位行 */
export function emptyServiceVarPlaceholder(): NodeVariable[] {
    return [{ id: createId('var'), name: '', extractPath: '$' }];
}
