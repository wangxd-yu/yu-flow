// ============================================================================
// NodeViewMode — 画布节点展示模式：card（完整）| compact（极简）
// 不写入 DSL；仅编辑器状态 + localStorage
//
// 重要：@antv/x6-react-shape 在独立 React 根中渲染节点，读不到 Provider Context。
// 因此用模块级 store + 订阅，供节点组件跨根同步。
// ============================================================================

import React from 'react';
import type { Graph, Node } from '@antv/x6';
import {
    CARD_WIDTH_DATA_KEY,
    COMPACT_NODE_WIDTH,
} from './CompactNodeChrome';

// 统一视觉 API 再导出，便于现有 import 路径不变
export {
    COMPACT_NODE_WIDTH,
    COMPACT_ACCENT_WIDTH,
    COMPACT_HEADER_HEIGHT,
    COMPACT_FOOTER_HEIGHT,
    COMPACT_EXIT_ROW,
    CARD_WIDTH_DATA_KEY,
    IF_COMPACT_FOOTER_HEIGHT,
    HTTP_COMPACT_FOOTER_HEIGHT,
    switchCompactFooterHeight,
    multiExitCompactFooterHeight,
    compactSingleOutPortY,
    compactExitPortY,
    CompactOutFooter,
    CompactExitLabels,
} from './CompactNodeChrome';

export type NodeViewMode = 'card' | 'compact';

const STORAGE_KEY = 'yu-flow.flowEditor.nodeViewMode';
export const GRAPH_NODE_VIEW_MODE_KEY = 'nodeViewMode';

type ModeListener = () => void;

function readStored(): NodeViewMode {
    try {
        const v = localStorage.getItem(STORAGE_KEY);
        if (v === 'compact' || v === 'card') return v;
    } catch {
        /* ignore */
    }
    return 'card';
}

let sharedMode: NodeViewMode = readStored();
const modeListeners = new Set<ModeListener>();

function publishMode(m: NodeViewMode) {
    sharedMode = m;
    modeListeners.forEach((l) => {
        try {
            l();
        } catch {
            /* ignore */
        }
    });
}

export function getSharedNodeViewMode(): NodeViewMode {
    return sharedMode;
}

function subscribeMode(listener: ModeListener): () => void {
    modeListeners.add(listener);
    return () => {
        modeListeners.delete(listener);
    };
}

const NodeViewModeContext = React.createContext<{
    mode: NodeViewMode;
    setMode: (m: NodeViewMode) => void;
}>({ mode: sharedMode, setMode: () => {} });

function graphGet(graph: Graph | null | undefined, key: string): unknown {
    return (graph as any)?.get?.(key);
}

function graphSet(graph: Graph | null | undefined, key: string, value: unknown) {
    (graph as any)?.set?.(key, value);
}

/** 非 React 回调（端口同步）：graph 属性 → 模块级共享模式 */
export function getGraphNodeViewMode(node: Node): NodeViewMode {
    try {
        const m = graphGet(node.model?.graph, GRAPH_NODE_VIEW_MODE_KEY);
        if (m === 'compact' || m === 'card') return m;
    } catch {
        /* ignore */
    }
    return sharedMode;
}

/**
 * compact：强制统一宽 + 矮高；进入前缓存 `__cardWidth`。
 * card：恢复 `__cardWidth`（或 cardDefaultWidth / minWidth），高度不低于 cardMinHeight。
 */
export function useCompactNodeResize(
    node: Node,
    opts: {
        cardMinHeight: number;
        compactHeight: number;
        /** card 模式最小宽（恢复时下限） */
        minWidth: number;
        /** card 默认宽；无缓存时用此值 */
        cardDefaultWidth?: number;
        /** compact 强制宽，默认 COMPACT_NODE_WIDTH */
        compactWidth?: number;
        resizing?: boolean;
    },
): { isCompact: boolean; mode: NodeViewMode } {
    const mode = useNodeViewMode();
    const isCompact = mode === 'compact';
    const {
        cardMinHeight,
        compactHeight,
        minWidth,
        cardDefaultWidth,
        compactWidth = COMPACT_NODE_WIDTH,
        resizing,
    } = opts;
    const prevCompactRef = React.useRef<boolean | null>(null);

    React.useEffect(() => {
        if (resizing) return;
        const s = node.getSize();
        const data = (node.getData() as any) || {};
        const wasCompact = prevCompactRef.current;
        prevCompactRef.current = isCompact;

        if (isCompact) {
            // 从 card 切入：刷新缓存宽度；首次挂载已是极简且仍偏宽时补缓存
            if (wasCompact === false) {
                node.setData(
                    { ...data, [CARD_WIDTH_DATA_KEY]: s.width },
                    { overwrite: true },
                );
            } else if (wasCompact === null && s.width > compactWidth + 0.5) {
                if (!(Number(data[CARD_WIDTH_DATA_KEY]) > 0)) {
                    node.setData(
                        { ...data, [CARD_WIDTH_DATA_KEY]: s.width },
                        { overwrite: true },
                    );
                }
            }
            if (
                Math.abs(s.width - compactWidth) > 0.5
                || Math.abs(s.height - compactHeight) > 0.5
            ) {
                node.resize(compactWidth, compactHeight);
            }
            return;
        }

        // 仅在从 compact 切回 card 时恢复宽度，避免打断用户在 card 下的手动缩放
        if (wasCompact === true) {
            const cached = Number(data[CARD_WIDTH_DATA_KEY]);
            const restoreW = cached > 0
                ? Math.max(cached, minWidth)
                : Math.max(cardDefaultWidth ?? minWidth, minWidth);
            const targetH = Math.max(s.height, cardMinHeight);
            node.resize(restoreW, targetH);
            return;
        }

        if (s.height < cardMinHeight - 0.5) {
            node.resize(Math.max(s.width, minWidth), cardMinHeight);
        }
    }, [
        isCompact,
        compactHeight,
        compactWidth,
        cardMinHeight,
        minWidth,
        cardDefaultWidth,
        node,
        resizing,
    ]);

    return { isCompact, mode };
}

function applyMode(graph: Graph | null | undefined, m: NodeViewMode) {
    publishMode(m);
    try {
        localStorage.setItem(STORAGE_KEY, m);
    } catch {
        /* ignore */
    }
    if (!graph) return;
    graphSet(graph, GRAPH_NODE_VIEW_MODE_KEY, m);
    graph.getNodes().forEach((n) => {
        n.setData({ ...n.getData(), __viewModeTick: Date.now() }, { overwrite: true });
    });
}

export function NodeViewModeProvider({
    graph,
    children,
}: {
    graph: Graph | null;
    children: React.ReactNode;
}) {
    const [mode, setModeState] = React.useState<NodeViewMode>(() => sharedMode);

    const setMode = React.useCallback(
        (m: NodeViewMode) => {
            setModeState(m);
            applyMode(graph, m);
        },
        [graph],
    );

    React.useEffect(() => {
        // graph 就绪时把当前模式写上；并订阅其它入口对 store 的变更
        if (graph) graphSet(graph, GRAPH_NODE_VIEW_MODE_KEY, sharedMode);
        return subscribeMode(() => setModeState(sharedMode));
    }, [graph]);

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

/** 跨 x6-react-shape 独立根也可订阅（读模块 store，不依赖 Context） */
export function useNodeViewMode(): NodeViewMode {
    const [mode, setMode] = React.useState<NodeViewMode>(() => sharedMode);
    React.useEffect(() => {
        setMode(sharedMode);
        return subscribeMode(() => setMode(sharedMode));
    }, []);
    return mode;
}

export function useNodeViewModeControls() {
    const ctx = React.useContext(NodeViewModeContext);
    const mode = useNodeViewMode();
    return React.useMemo(
        () => ({
            mode,
            // Provider 内用 ctx.setMode（会写 graph + tick）；否则直接写 store
            setMode: ctx.setMode,
        }),
        [mode, ctx.setMode],
    );
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

