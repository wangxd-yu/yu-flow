// ============================================================================
// RequestNodeComponent.tsx — Request 入口节点
// Method 跟随外层接口 Method（只读展示）；据此控制 Body 出口可见性
// ============================================================================

import React from 'react';
import { Node } from '@antv/x6';
import {
    useNodeSelection, NodeHeader, NodeWrapper, getNodeTheme, ResizeHandle, NODE_HEADER_WITH_ID_HEIGHT,
} from '../../shared/useNodeSelection';
import { commitFlowNodeIdChange } from '../../shared/nodeIdUtils';
import {
    COMPACT_ACCENT_WIDTH,
    COMPACT_NODE_WIDTH,
    CompactExitLabels,
    compactExitPortY,
    getGraphNodeViewMode,
    multiExitCompactFooterHeight,
    useCompactNodeResize,
} from '../../shared/NodeViewMode';

const ICONS = {
    request: (
        <svg viewBox="0 0 1024 1024" width="14" height="14" fill="currentColor">
            <path d="M140 160h744v72H140zM140 376h744v72H140zM140 592h744v72H140zM140 808h744v72H140z" />
        </svg>
    ),
};

const METHOD_META: Record<string, { label: string; color: string }> = {
    GET: { label: 'GET', color: '#52c41a' },
    POST: { label: 'POST', color: '#fa8c16' },
    PUT: { label: 'PUT', color: '#1677ff' },
    DELETE: { label: 'DELETE', color: '#f5222d' },
    PATCH: { label: 'PATCH', color: '#722ed1' },
};

/** 画布上挂载的外层接口 Method（由 FlowEditor 写入） */
export const GRAPH_API_METHOD_KEY = 'yfApiMethod';

/** 判断该 method 是否支持 body */
export function methodHasBody(method?: string): boolean {
    const m = (method || 'GET').toUpperCase();
    return m === 'POST' || m === 'PUT' || m === 'PATCH';
}

export function getGraphApiMethod(node: Node): string | undefined {
    try {
        const m = (node.model?.graph as any)?.get?.(GRAPH_API_METHOD_KEY);
        if (typeof m === 'string' && m.trim()) {
            return m.trim().toUpperCase();
        }
    } catch {
        /* ignore */
    }
    return undefined;
}

// ── 布局常量，与端口定位共享 ──
export const REQUEST_LAYOUT = {
    headerHeight: NODE_HEADER_WITH_ID_HEIGHT,
    paddingTop: 10,
    rowHeight: 20,
    rowGap: 2,
    paddingBottom: 10,
    width: 260,
    rowCenterY: (index: number) => NODE_HEADER_WITH_ID_HEIGHT + 10 + index * (20 + 2) + 10,
    get totalHeight2() { return NODE_HEADER_WITH_ID_HEIGHT + 10 + 2 * 22 + 10; },
    get totalHeight3() { return NODE_HEADER_WITH_ID_HEIGHT + 10 + 3 * 22 + 10; },
    totalHeight: 40 + 10 + 3 * 22 + 10,
};

/** 只读 Method 徽章（跟随外层，不可点选） */
const MethodBadge: React.FC<{ value: string }> = ({ value }) => {
    const meta = METHOD_META[value] || METHOD_META.GET;
    return (
        <div
            title="跟随接口 Method，请在顶部切换"
            style={{
                display: 'inline-flex',
                alignItems: 'center',
                padding: '1px 6px',
                fontSize: 10,
                fontWeight: 700,
                color: meta.color,
                background: '#ffffff',
                border: `1px solid ${meta.color}`,
                borderRadius: 4,
                lineHeight: '16px',
                whiteSpace: 'nowrap',
                pointerEvents: 'none',
                userSelect: 'none',
                opacity: 0.92,
            }}
        >
            {meta.label}
        </div>
    );
};

export const RequestNodeComponent = ({ node }: { node: Node }) => {
    const [data, setData] = React.useState<any>(node.getData());
    const themeObj = getNodeTheme(data?.themeColor || 'orange');
    const { outlineCss, selected } = useNodeSelection(node, {
        defaultColor: themeObj.primary,
        selectedColor: themeObj.primary,
        borderRadius: 10,
    });

    React.useEffect(() => {
        const onDataChange = () => setData({ ...node.getData() });
        node.on('change:data', onDataChange);
        return () => { node.off('change:data', onDataChange); };
    }, [node]);

    // ── 标题 ──
    const nodeLabel = data?.__label || 'Request';
    const handleTitleChange = React.useCallback((newTitle: string) => {
        node.setData({ ...node.getData(), __label: newTitle });
    }, [node]);

    // ── Method：优先外层接口，其次节点 data（兼容旧 DSL）──
    const method = (getGraphApiMethod(node) || data?.method || 'GET').toUpperCase();
    const hasBody = methodHasBody(method);
    const cardHeight = hasBody ? REQUEST_LAYOUT.totalHeight3 : REQUEST_LAYOUT.totalHeight2;
    const exitCount = hasBody ? 3 : 2;
    const compactFooterH = multiExitCompactFooterHeight(exitCount);
    const compactHeight = REQUEST_LAYOUT.headerHeight + compactFooterH;

    // 外层 method 变化时写回节点 data，保证导出 DSL / 端口与接口一致
    React.useEffect(() => {
        const apiMethod = getGraphApiMethod(node);
        if (!apiMethod) return;
        const cur = ((node.getData() || {}).method || 'GET').toUpperCase();
        if (cur !== apiMethod) {
            node.setData({ ...(node.getData() || {}), method: apiMethod });
        }
    }, [node, method]);

    const { isCompact } = useCompactNodeResize(node, {
        cardMinHeight: cardHeight,
        compactHeight,
        minWidth: REQUEST_LAYOUT.width,
        cardDefaultWidth: REQUEST_LAYOUT.width,
        compactWidth: COMPACT_NODE_WIDTH + COMPACT_ACCENT_WIDTH,
    });

    const syncRequestPorts = React.useCallback(() => {
        const w = node.getSize().width || REQUEST_LAYOUT.width;
        const h = node.getSize().height;
        const compact = getGraphNodeViewMode(node) === 'compact';
        const ports = hasBody
            ? (['headers', 'params', 'body'] as const)
            : (['headers', 'params'] as const);

        ports.forEach((id, idx) => {
            const y = compact
                ? compactExitPortY(REQUEST_LAYOUT.headerHeight, idx)
                : REQUEST_LAYOUT.rowCenterY(idx);
            if (!node.hasPort(id)) {
                node.addPort({ id, group: 'absolute-out-solid', args: { x: w, y, dx: 0 } });
            } else {
                node.setPortProp(id, 'args', { x: w, y, dx: 0 });
            }
        });

        if (!hasBody && node.hasPort('body')) {
            const graph = node.model?.graph;
            if (graph) {
                graph.getConnectedEdges(node).forEach((edge: any) => {
                    if (edge.getSourcePortId?.() === 'body' && edge.getSourceCellId?.() === node.id) {
                        graph.removeEdge(edge);
                    }
                });
            }
            node.removePort('body');
        }

        if (!compact) {
            const target = hasBody ? REQUEST_LAYOUT.totalHeight3 : REQUEST_LAYOUT.totalHeight2;
            if (Math.abs(h - target) > 0.5) {
                node.resize(Math.max(w, REQUEST_LAYOUT.width), target);
            }
        }
    }, [node, hasBody]);

    React.useEffect(() => {
        syncRequestPorts();
    }, [syncRequestPorts, isCompact]);

    const rows = hasBody ? ['Headers', 'Params', 'Body'] : ['Headers', 'Params'];

    const methodExtra = isCompact ? undefined : <MethodBadge value={method} />;

    return (
        <NodeWrapper
            node={node}
            selected={selected}
            themeColor={themeObj.primary}
            outlineCss={outlineCss}
            backgroundColor={themeObj.bodyBg}
            extraStyle={{ borderRadius: 10, flexDirection: 'row' }}
        >
            <div style={{ width: 12, height: '100%', background: themeObj.primary, pointerEvents: 'auto' }} />
            <div style={{ flex: 1, display: 'flex', flexDirection: 'column' }}>
                <NodeHeader
                    icon={ICONS.request}
                    title={nodeLabel}
                    theme={themeObj}
                    height={REQUEST_LAYOUT.headerHeight}
                    node={node}
                    nodeId={node.id}
                    onNodeIdChange={(id) => commitFlowNodeIdChange(node, id)}
                    onTitleChange={handleTitleChange}
                    extra={methodExtra}
                />

                {isCompact ? (
                    <CompactExitLabels
                        height={compactFooterH}
                        exits={rows.map((label) => ({
                            id: label.toLowerCase(),
                            label,
                        }))}
                    />
                ) : (
                    <div
                        style={{
                            paddingTop: REQUEST_LAYOUT.paddingTop,
                            paddingBottom: REQUEST_LAYOUT.paddingBottom,
                            paddingRight: 12,
                            display: 'flex',
                            flexDirection: 'column',
                            gap: REQUEST_LAYOUT.rowGap,
                            pointerEvents: 'auto',
                        }}
                    >
                        {rows.map((label) => (
                            <div
                                key={label}
                                style={{
                                    height: REQUEST_LAYOUT.rowHeight,
                                    display: 'flex',
                                    alignItems: 'center',
                                    justifyContent: 'flex-end',
                                }}
                            >
                                <span style={{ fontSize: 11, color: '#595959' }}>{label}</span>
                            </div>
                        ))}
                    </div>
                )}
            </div>

            {!isCompact && (
                <ResizeHandle
                    node={node}
                    minWidth={REQUEST_LAYOUT.width}
                    minHeight={cardHeight}
                    axes="x"
                    color={themeObj.primary}
                />
            )}
        </NodeWrapper>
    );
};

export default RequestNodeComponent;
