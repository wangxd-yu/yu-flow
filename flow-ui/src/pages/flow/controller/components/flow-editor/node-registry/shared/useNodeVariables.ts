// ============================================================================
// useNodeVariables.ts — 节点变量操作通用 Hook
// 功能: 增删、拖拽排序、端口同步、连接事件 placeholder 自动升级
// 从 IfNodeComponent.tsx 中提取，供 DatabaseNode / IfNode 等复用
// ============================================================================

import React from 'react';
import { Node } from '@antv/x6';
import { createId } from '../../utils/id';
import { relativizeExtractPath } from './extractPathUtils';

// ── 数据类型 ──
export interface NodeVariable {
    id: string;
    name: string;
    extractPath: string;
    /** API 节点：入参来源（query/path/body/header） */
    paramSource?: 'query' | 'path' | 'body' | 'header';
    /** API 节点：契约必填 */
    required?: boolean;
    /** API 节点：由目标 API contract 自动带出 */
    fromContract?: boolean;
}

/** 总入口 in:payload 写入的 key，不占变量行 */
const PAYLOAD_INPUT_KEY = 'payload';

// ── 配置接口 ──
export interface UseNodeVariablesOptions {
    /** 计算变量端口 Y 坐标的函数 */
    varPortY: (idx: number) => number;
    /** 每行高度，用于拖拽阈值判断 */
    rowHeight: number;
    /** `inputs` → `NodeVariable[]` 转换 */
    inputsToVars?: (inputs?: Record<string, any>, existingPorts?: string[]) => NodeVariable[];
    /** `NodeVariable[]` → `inputs` 转换 */
    varsToInputs?: (vars: NodeVariable[]) => Record<string, any> | undefined;
}

// ── 默认转换函数 ──
function defaultInputsToVars(inputs?: Record<string, any>, existingPorts?: string[]): NodeVariable[] {
    if (!inputs || typeof inputs !== 'object') return [];

    const varIds = (existingPorts || [])
        .filter((p) => p.startsWith('in:var:'))
        .map((p) => p.replace('in:var:', ''));

    return Object.entries(inputs)
        .filter(([key]) => key !== PAYLOAD_INPUT_KEY)
        .map(([key, val], i) => {
            const obj = typeof val === 'string' ? null : (val as any);
            return {
                id: obj?.id || varIds[i] || createId('var'),
                name: key,
                extractPath: typeof val === 'string' ? val : obj?.extractPath || '',
                paramSource: obj?.paramSource,
                required: obj?.required,
                fromContract: obj?.fromContract,
            };
        });
}

function defaultVarsToInputs(vars: NodeVariable[]): Record<string, any> | undefined {
    const r: Record<string, any> = {};
    for (const v of vars) {
        if (!v.name) continue;
        const entry: Record<string, any> = { id: v.id, extractPath: v.extractPath };
        if (v.paramSource) entry.paramSource = v.paramSource;
        if (v.required != null) entry.required = v.required;
        if (v.fromContract) entry.fromContract = true;
        r[v.name] = entry;
    }
    return Object.keys(r).length > 0 ? r : undefined;
}

// ── Hook ──
export function useNodeVariables(node: Node, options: UseNodeVariablesOptions) {
    const {
        varPortY,
        rowHeight,
        inputsToVars = defaultInputsToVars,
        varsToInputs = defaultVarsToInputs,
    } = options;

    // ── 变量状态 ──
    const [variables, setVariables] = React.useState<NodeVariable[]>(() => {
        const data = node.getData() as any;
        const saved = data?.__variables;
        // payload 为总入口保留字，不占变量行（历史脏数据一并过滤）
        if (saved && saved.length > 0) {
            return saved.filter((v: NodeVariable) => v.name !== PAYLOAD_INPUT_KEY);
        }

        const existingPorts = node.getPorts().map((p) => p.id!);
        const fr = inputsToVars(data?.inputs, existingPorts);

        const varIds = existingPorts.filter((p) => p.startsWith('in:var:')).map((p) => p.replace('in:var:', ''));
        const placeholderId = varIds.length > fr.length ? varIds[varIds.length - 1] : createId('var');

        fr.push({ id: placeholderId, name: '', extractPath: '$' }); // placeholder
        return fr;
    });

    // ── 同步到 Node Data（保留 inputs.payload 总入口，不被变量行覆盖）──
    const syncToNodeData = React.useCallback(
        (nv: NodeVariable[], extra?: Record<string, any>) => {
            setVariables(nv);
            const prev = node.getData() as any;
            const nextInputs = { ...(varsToInputs(nv) || {}) } as Record<string, any>;
            if (prev?.inputs?.[PAYLOAD_INPUT_KEY] != null) {
                nextInputs[PAYLOAD_INPUT_KEY] = prev.inputs[PAYLOAD_INPUT_KEY];
            }
            node.setData(
                { ...prev, inputs: Object.keys(nextInputs).length ? nextInputs : undefined, __variables: nv, ...extra },
                { overwrite: true },
            );
        },
        [node, varsToInputs],
    );

    // Refs — 保持最新引用，避免闭包过期
    const variablesRef = React.useRef(variables);
    variablesRef.current = variables;
    const syncRef = React.useRef(syncToNodeData);
    syncRef.current = syncToNodeData;

    // 首次挂载: 确保 __variables 写入 node data
    React.useEffect(() => {
        const d = node.getData() as any;
        if (!d?.__variables) {
            const nextInputs = { ...(varsToInputs(variables) || {}) } as Record<string, any>;
            if (d?.inputs?.[PAYLOAD_INPUT_KEY] != null) {
                nextInputs[PAYLOAD_INPUT_KEY] = d.inputs[PAYLOAD_INPUT_KEY];
            }
            node.setData(
                { ...d, __variables: variables, inputs: Object.keys(nextInputs).length ? nextInputs : undefined },
                { overwrite: true },
            );
        }
    }, []);

    /** 按入边源节点把绝对路径压回 $ / $.field（加载已保存 DSL 时） */
    const relativizeWiredVars = React.useCallback(() => {
        const graph = node.model?.graph;
        if (!graph) return;
        const curVars: NodeVariable[] =
            (node.getData() as any)?.__variables || variablesRef.current;
        let changed = false;
        const updated = curVars.map((v) => {
            const portId = `in:var:${v.id}`;
            const edge = graph.getConnectedEdges(node).find((e: any) => {
                if (e.getTargetCellId?.() !== node.id) return false;
                return String(e.getTargetPortId?.()) === portId;
            });
            if (!edge) return v;
            const srcId = edge.getSourceCellId?.();
            const srcPort = edge.getSourcePortId?.() || 'out';
            if (!srcId) return v;
            const nextPath = relativizeExtractPath(v.extractPath, srcId, srcPort);
            if (nextPath === v.extractPath) return v;
            changed = true;
            return { ...v, extractPath: nextPath };
        });
        if (changed) syncRef.current(updated);
    }, [node]);

    // 挂载后 / 图就绪后：压回简写
    React.useEffect(() => {
        let cancelled = false;
        const tryRel = () => {
            if (cancelled) return;
            if (!node.model?.graph) {
                requestAnimationFrame(tryRel);
                return;
            }
            relativizeWiredVars();
        };
        tryRel();
        return () => { cancelled = true; };
    }, [node, relativizeWiredVars]);

    // ── edge:connected 事件: placeholder 连接后升级 ──
    React.useEffect(() => {
        const graph = node.model?.graph;
        if (!graph) return;

        const onConnected = ({ edge, ...evtArgs }: any) => {
            const target = edge.getTarget() as any;
            const targetCellId =
                evtArgs?.currentCell?.id ||
                (typeof target?.cell === 'string' ? target.cell : target?.cell?.id);
            const targetPort = evtArgs?.currentPort || target?.port;

            if (targetCellId !== node.id) return;
            if (!targetPort || !targetPort.startsWith('in:var:')) return;

            const curVars: NodeVariable[] =
                (node.getData() as any)?.__variables || variablesRef.current;
            const last = curVars[curVars.length - 1];
            if (!last) return;

            const lastPortId = `in:var:${last.id}`;
            if (targetPort !== lastPortId) return;
            if ((edge as any).__pv) return;
            (edge as any).__pv = true;

            // 仅「末行占位」连线才升级并追加新占位；契约固定行连线不得增高
            const isPlaceholderRow = !last.name?.trim() && !last.fromContract;
            if (isPlaceholderRow) {
                const updated = [...curVars];
                updated[updated.length - 1] = {
                    ...last,
                    name: `var${updated.length}`,
                    extractPath: last.extractPath?.trim() ? last.extractPath : '$',
                };
                updated.push({ id: createId('var'), name: '', extractPath: '$' });
                syncRef.current(updated);
            } else if (!last.extractPath?.trim()) {
                syncRef.current(
                    curVars.map((v, i) =>
                        i === curVars.length - 1 ? { ...v, extractPath: '$' } : v,
                    ),
                );
            }

            setTimeout(() => {
                try {
                    edge.addTools({ name: 'button-remove', args: { distance: '50%' } });
                } catch (_) { }
                delete (edge as any).__pv;
                relativizeWiredVars();
            }, 50);
        };
        graph.on('edge:connected', onConnected);
        return () => {
            graph.off('edge:connected', onConnected);
        };
    }, [node, relativizeWiredVars]);

    // ── 已有连线加删除按钮 ──
    React.useEffect(() => {
        const graph = node.model?.graph;
        if (!graph) return;
        try {
            graph.getConnectedEdges(node).forEach((edge) => {
                const t = edge.getTarget() as any;
                const tCellId = typeof t?.cell === 'string' ? t.cell : t?.cell?.id;
                if (
                    tCellId === node.id &&
                    t?.port?.startsWith('in:var:') &&
                    !edge.hasTool('button-remove')
                )
                    edge.addTools({ name: 'button-remove', args: { distance: '50%' } });
            });
        } catch (_) { }
    }, [node, variables]);

    // ── 端口辅助: 刷新关联连线 ──
    const updateEdges = React.useCallback(
        (pid: string) => {
            const graph = node.model?.graph;
            if (!graph) return;
            graph.getConnectedEdges(node).forEach((edge) => {
                const t = edge.getTarget() as any;
                if (t?.port === pid) (graph.findViewByCell(edge) as any)?.update();
            });
        },
        [node],
    );

    // ── 拖拽状态 ──
    const [dragState, setDragState] = React.useState<{
        index: number;
        startY: number;
        currentY: number;
    } | null>(null);
    const [hoverRowIndex, setHoverRowIndex] = React.useState<number | null>(null);

    // ── 端口同步 (仅变量端口) ──
    React.useEffect(() => {
        const ports = node.getPorts();
        const existing = new Set(ports.map((p) => p.id));
        const wanted = new Set(variables.map((v) => `in:var:${v.id}`));

        variables.forEach((v, idx) => {
            const pid = `in:var:${v.id}`;
            const y = varPortY(idx);
            if (!existing.has(pid)) {
                node.addPort({
                    id: pid,
                    group: 'absolute-in-solid',
                    args: { x: 0, y, dx: 0 },
                    zIndex: 1,
                });
            } else if (!dragState) {
                node.setPortProp(pid, 'args', { x: 0, y, dx: 0 });
                updateEdges(pid);
            }
        });

        // 移除多余的变量端口（含误生成的 in:var:payload）
        ports.forEach((p) => {
            if (p.id?.startsWith('in:var:') && !wanted.has(p.id)) {
                try { node.removePort(p.id); } catch { /* ignore */ }
            }
        });
    }, [variables, node, dragState]);

    // ── 变量操作 ──
    const onAddVar = React.useCallback(
        (e?: React.MouseEvent) => {
            e?.stopPropagation();
            const nv = [...variables];
            const li = nv.length - 1;
            if (li >= 0 && !nv[li].name) nv[li] = { ...nv[li], name: `var${nv.length}` };
            nv.push({ id: createId('var'), name: '', extractPath: '$' });
            syncToNodeData(nv);
        },
        [variables, syncToNodeData],
    );

    const onUpdateVar = React.useCallback(
        (id: string, patch: Partial<NodeVariable>) => {
            syncToNodeData(variables.map((v) => (v.id === id ? { ...v, ...patch } : v)));
        },
        [variables, syncToNodeData],
    );

    const onRemoveVar = React.useCallback(
        (index: number) => {
            const graph = node.model?.graph;
            const vr = variables[index];
            if (graph && vr) {
                const pid = `in:var:${vr.id}`;
                graph.getConnectedEdges(node).forEach((edge) => {
                    const t = edge.getTarget();
                    if (t && typeof t === 'object' && 'port' in t && t.port === pid)
                        graph.removeEdge(edge);
                });
            }
            const nv = [...variables];
            nv.splice(index, 1);
            syncToNodeData(nv);
        },
        [variables, node, syncToNodeData],
    );

    // ── 拖拽排序 ──
    const handleDragStart = React.useCallback(
        (index: number) => (e: React.MouseEvent) => {
            e.stopPropagation();
            e.preventDefault();
            setDragState({ index, startY: e.clientY, currentY: e.clientY });
        },
        [],
    );

    React.useEffect(() => {
        if (!dragState) return;
        const { startY } = dragState;

        const onMove = (e: MouseEvent) => {
            e.preventDefault();
            setDragState((p) => (p ? { ...p, currentY: e.clientY } : null));
            const delta = e.clientY - startY;
            const dv = variables[dragState.index];
            if (dv) {
                node.setPortProp(`in:var:${dv.id}`, 'args', {
                    y: varPortY(dragState.index) + delta,
                    x: 0,
                    dx: 0,
                });
                updateEdges(`in:var:${dv.id}`);
            }
            if (delta > rowHeight / 2 && dragState.index < variables.length - 1) {
                const nv = [...variables];
                [nv[dragState.index], nv[dragState.index + 1]] = [
                    nv[dragState.index + 1],
                    nv[dragState.index],
                ];
                syncToNodeData(nv);
                setDragState({
                    index: dragState.index + 1,
                    startY: startY + rowHeight,
                    currentY: e.clientY,
                });
            } else if (delta < -rowHeight / 2 && dragState.index > 0) {
                const nv = [...variables];
                [nv[dragState.index], nv[dragState.index - 1]] = [
                    nv[dragState.index - 1],
                    nv[dragState.index],
                ];
                syncToNodeData(nv);
                setDragState({
                    index: dragState.index - 1,
                    startY: startY - rowHeight,
                    currentY: e.clientY,
                });
            }
        };

        const onUp = () => {
            variables.forEach((v, i) => {
                node.setPortProp(`in:var:${v.id}`, 'args', { y: varPortY(i), x: 0, dx: 0 });
                updateEdges(`in:var:${v.id}`);
            });
            setDragState(null);
        };

        document.addEventListener('mousemove', onMove);
        document.addEventListener('mouseup', onUp);
        return () => {
            document.removeEventListener('mousemove', onMove);
            document.removeEventListener('mouseup', onUp);
        };
    }, [dragState, variables, node]);

    return {
        variables,
        dragState,
        hoverRowIndex,
        setHoverRowIndex,
        updateEdges,
        syncToNodeData,
        onAddVar,
        onUpdateVar,
        onRemoveVar,
        handleDragStart,
    };
}
