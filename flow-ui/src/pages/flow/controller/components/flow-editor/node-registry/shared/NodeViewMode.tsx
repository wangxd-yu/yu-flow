// ============================================================================
// NodeViewMode — 画布节点展示模式：card（完整）| compact（极简）
// 不写入 DSL；仅编辑器状态 + localStorage
// ============================================================================

import React from 'react';
import type { Graph, Node } from '@antv/x6';

export type NodeViewMode = 'card' | 'compact';

const STORAGE_KEY = 'yu-flow.flowEditor.nodeViewMode';
export const GRAPH_NODE_VIEW_MODE_KEY = 'nodeViewMode';

const NodeViewModeContext = React.createContext<{
    mode: NodeViewMode;
    setMode: (m: NodeViewMode) => void;
}>({ mode: 'card', setMode: () => {} });

function readStored(): NodeViewMode {
    try {
        const v = localStorage.getItem(STORAGE_KEY);
        if (v === 'compact' || v === 'card') return v;
    } catch {
        /* ignore */
    }
    return 'card';
}

function graphGet(graph: Graph | null | undefined, key: string): unknown {
    return (graph as any)?.get?.(key);
}

function graphSet(graph: Graph | null | undefined, key: string, value: unknown) {
    (graph as any)?.set?.(key, value);
}

/** 非 React 回调（端口同步）从 graph 读当前展示模式 */
export function getGraphNodeViewMode(node: Node): NodeViewMode {
    try {
        const m = graphGet(node.model?.graph, GRAPH_NODE_VIEW_MODE_KEY);
        if (m === 'compact' || m === 'card') return m;
    } catch {
        /* ignore */
    }
    return 'card';
}

/**
 * compact 时强制矮高度；切回 card 时保证不低于 cardMinHeight。
 */
export function useCompactNodeResize(
    node: Node,
    opts: {
        cardMinHeight: number;
        compactHeight: number;
        minWidth: number;
        resizing?: boolean;
    },
): { isCompact: boolean; mode: NodeViewMode } {
    const mode = useNodeViewMode();
    const isCompact = mode === 'compact';
    const { cardMinHeight, compactHeight, minWidth, resizing } = opts;

    React.useEffect(() => {
        if (resizing) return;
        const s = node.getSize();
        const w = Math.max(s.width, minWidth);
        if (isCompact) {
            if (Math.abs(s.height - compactHeight) > 0.5) {
                node.resize(w, compactHeight);
            }
        } else if (s.height < cardMinHeight) {
            node.resize(w, cardMinHeight);
        }
    }, [isCompact, compactHeight, cardMinHeight, minWidth, node, resizing]);

    return { isCompact, mode };
}

export function NodeViewModeProvider({
    graph,
    children,
}: {
    graph: Graph | null;
    children: React.ReactNode;
}) {
    const [mode, setModeState] = React.useState<NodeViewMode>(readStored);

    const setMode = React.useCallback(
        (m: NodeViewMode) => {
            setModeState(m);
            try {
                localStorage.setItem(STORAGE_KEY, m);
            } catch {
                /* ignore */
            }
            if (graph) {
                graphSet(graph, GRAPH_NODE_VIEW_MODE_KEY, m);
                graph.getNodes().forEach((n) => {
                    n.setData({ ...n.getData(), __viewModeTick: Date.now() }, { overwrite: true });
                });
            }
        },
        [graph],
    );

    React.useEffect(() => {
        if (graph) graphSet(graph, GRAPH_NODE_VIEW_MODE_KEY, mode);
    }, [graph, mode]);

    React.useEffect(() => {
        if (!graph) return;
        syncEdgePortLabels(graph, mode);
        const refresh = () => syncEdgePortLabels(graph, mode);
        graph.on('edge:connected', refresh);
        graph.on('edge:added', refresh);
        return () => {
            graph.off('edge:connected', refresh);
            graph.off('edge:added', refresh);
        };
    }, [graph, mode]);

    const value = React.useMemo(() => ({ mode, setMode }), [mode, setMode]);
    return (
        <NodeViewModeContext.Provider value={value}>{children}</NodeViewModeContext.Provider>
    );
}

export function useNodeViewMode(): NodeViewMode {
    return React.useContext(NodeViewModeContext).mode;
}

export function useNodeViewModeControls() {
    return React.useContext(NodeViewModeContext);
}

/** 多出口端口的友好标签（用于边标签） */
export function formatPortEdgeLabel(portId: string, caseName?: string): string | null {
    if (!portId) return null;
    if (portId === 'out') return null;
    if (portId === 'true') return 'THEN';
    if (portId === 'false') return 'ELSE';
    if (portId === 'success') return 'success';
    if (portId === 'fail') return 'fail';
    if (portId === 'default') return 'Default';
    if (portId === 'item') return 'item';
    if (portId === 'done') return 'done';
    if (portId === 'list') return 'list';
    if (portId === 'finish') return 'finish';
    if (portId === 'headers') return 'headers';
    if (portId === 'params') return 'params';
    if (portId === 'body') return 'body';
    if (portId.startsWith('case_')) {
        return caseName || portId.slice(5) || 'case';
    }
    if (portId.startsWith('in:')) return null;
    return portId;
}

/**
 * compact 模式：给多出口边挂标签；card 模式清除。
 */
export function syncEdgePortLabels(graph: Graph, mode: NodeViewMode) {
    graph.getEdges().forEach((edge) => {
        if (mode === 'card') {
            edge.setLabels([]);
            return;
        }
        const src = edge.getSource() as { cell?: string; port?: string };
        const portId = src?.port || '';
        let caseName: string | undefined;
        if (portId.startsWith('case_') && src.cell) {
            const n = graph.getCellById(src.cell) as any;
            const cases = n?.getData?.()?.cases;
            if (Array.isArray(cases)) {
                const id = portId.slice(5);
                const hit = cases.find((c: any) => c?.id === id);
                if (hit?.name) caseName = String(hit.name);
            }
        }
        const text = formatPortEdgeLabel(portId, caseName);
        if (!text) {
            edge.setLabels([]);
            return;
        }
        edge.setLabels([
            {
                attrs: {
                    label: {
                        text,
                        fill: '#595959',
                        fontSize: 10,
                        fontFamily: 'ui-sans-serif, system-ui, sans-serif',
                    },
                    body: {
                        ref: 'label',
                        fill: '#ffffff',
                        stroke: '#e8e8e8',
                        strokeWidth: 1,
                        rx: 3,
                        ry: 3,
                        refWidth: '100%',
                        refHeight: '100%',
                        refX: -4,
                        refY: -2,
                        refWidth2: 8,
                        refHeight2: 4,
                    },
                },
                position: 0.55,
            },
        ]);
    });
}

export const COMPACT_HEADER_HEIGHT = 52;
/** 极简模式下单出口条高度 */
export const COMPACT_FOOTER_HEIGHT = 32;
/** 极简多出口每行高度 */
export const COMPACT_EXIT_ROW = 22;

/** Switch：cases + Default 的极简 footer 高度 */
export function switchCompactFooterHeight(caseCount: number): number {
    const n = Math.max(1, caseCount) + 1;
    return Math.max(COMPACT_FOOTER_HEIGHT, n * COMPACT_EXIT_ROW + 4);
}

/** If：THEN / ELSE */
export const IF_COMPACT_FOOTER_HEIGHT = COMPACT_EXIT_ROW * 2 + 8;

/** HttpRequest：success / fail */
export const HTTP_COMPACT_FOOTER_HEIGHT = COMPACT_EXIT_ROW * 2 + 8;

/** 极简多出口右侧标签条 */
export function CompactExitLabels({
    exits,
    height,
}: {
    exits: { id: string; label: string; color?: string }[];
    height: number;
}) {
    const rowH = exits.length > 0 ? (height - 4) / exits.length : COMPACT_EXIT_ROW;
    return (
        <div
            style={{
                height,
                flexShrink: 0,
                borderTop: '1px solid #f0f0f0',
                boxSizing: 'border-box',
                padding: '2px 10px 2px 0',
                display: 'flex',
                flexDirection: 'column',
                justifyContent: 'center',
                pointerEvents: 'none',
            }}
        >
            {exits.map((e) => (
                <div
                    key={e.id}
                    style={{
                        height: rowH,
                        display: 'flex',
                        alignItems: 'center',
                        justifyContent: 'flex-end',
                        fontSize: 10,
                        color: e.color || '#8c8c8c',
                        lineHeight: 1,
                    }}
                >
                    {e.label}
                </div>
            ))}
        </div>
    );
}
