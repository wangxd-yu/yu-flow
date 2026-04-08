// ============================================================================
// useNodeVariables.ts — 节点变量操作通用 Hook
// 功能: 增删、拖拽排序、端口同步、连接事件 placeholder 自动升级
// 从 IfNodeComponent.tsx 中提取，供 DatabaseNode / IfNode 等复用
// ============================================================================

import React from 'react';
import { Node } from '@antv/x6';
import { createId } from '../../utils/id';

// ── 数据类型 ──
export interface NodeVariable {
    id: string;
    name: string;
    extractPath: string;
}

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

    return Object.entries(inputs).map(([key, val], i) => ({
        id: (val as any)?.id || varIds[i] || createId('var'),
        name: key,
        extractPath: typeof val === 'string' ? val : (val as any)?.extractPath || '',
    }));
}

function defaultVarsToInputs(vars: NodeVariable[]): Record<string, any> | undefined {
    const r: Record<string, any> = {};
    for (const v of vars) {
        if (v.name) r[v.name] = { id: v.id, extractPath: v.extractPath };
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
        if (saved && saved.length > 0) return saved;

        const existingPorts = node.getPorts().map((p) => p.id!);
        const fr = inputsToVars(data?.inputs, existingPorts);

        const varIds = existingPorts.filter((p) => p.startsWith('in:var:')).map((p) => p.replace('in:var:', ''));
        const placeholderId = varIds.length > fr.length ? varIds[varIds.length - 1] : createId('var');

        fr.push({ id: placeholderId, name: '', extractPath: '$' }); // placeholder
        return fr;
    });

    // ── 同步到 Node Data ──
    const syncToNodeData = React.useCallback(
        (nv: NodeVariable[], extra?: Record<string, any>) => {
            setVariables(nv);
            node.setData({ ...node.getData(), inputs: varsToInputs(nv), __variables: nv, ...extra }, { overwrite: true });
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
            node.setData({ ...d, __variables: variables, inputs: varsToInputs(variables) }, { overwrite: true });
        }
    }, []);

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

            const updated = [...curVars];
            if (!updated[updated.length - 1].name) {
                updated[updated.length - 1] = { ...last, name: `var${updated.length}` };
            }
            updated.push({ id: createId('var'), name: '', extractPath: '$' });
            syncRef.current(updated);

            setTimeout(() => {
                try {
                    edge.addTools({ name: 'button-remove', args: { distance: '50%' } });
                } catch (_) { }
                delete (edge as any).__pv;
            }, 50);
        };
        graph.on('edge:connected', onConnected);
        return () => {
            graph.off('edge:connected', onConnected);
        };
    }, [node]);

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

        // 移除多余的变量端口
        ports.forEach((p) => {
            if (p.id?.startsWith('in:var:') && !wanted.has(p.id)) node.removePort(p.id);
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
