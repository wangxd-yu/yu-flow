// ============================================================================
// ForEachNodeComponent.tsx — 串行循环节点
// ============================================================================

import React from 'react';
import { Node } from '@antv/x6';
import {
    useNodeSelection,
    getNodeTheme,
    NodeHeader,
    NodeWrapper,
    ResizeHandle,
    NODE_HEADER_WITH_ID_HEIGHT,
} from '../../shared/useNodeSelection';
import { commitFlowNodeIdChange } from '../../shared/nodeIdUtils';
import {
    COMPACT_NODE_WIDTH,
    CompactExitLabels,
    compactExitPortY,
    getGraphNodeViewMode,
    HTTP_COMPACT_FOOTER_HEIGHT,
    useCompactNodeResize,
} from '../../shared/NodeViewMode';

export const FOREACH_COLOR = '#0d9488';

const FOREACH_COMPACT_FOOTER = HTTP_COMPACT_FOOTER_HEIGHT;

export const FOREACH_LAYOUT = {
    width: 168,
    height: 120,
    headerHeight: NODE_HEADER_WITH_ID_HEIGHT,
    portY: {
        in: 72,
        item: 72,
        done: 100,
    },
} as const;

const ICON = (
    <svg viewBox="0 0 1024 1024" width="14" height="14" fill="currentColor">
        <path d="M384 192h448v64H384zm0 192h448v64H384zm0 192h448v64H384zM192 192h128v64H192zm0 192h128v64H192zm0 192h128v64H192z" />
    </svg>
);

export const ForEachNodeComponent: React.FC<{ node: Node }> = ({ node }) => {
    const data = (node.getData() as any) || {};
    const theme = getNodeTheme(data?.themeColor || 'cyan');
    const { selected, outlineCss } = useNodeSelection(node);
    const [size, setSize] = React.useState(node.getSize());
    const compactHeight = NODE_HEADER_WITH_ID_HEIGHT + FOREACH_COMPACT_FOOTER;

    const { isCompact } = useCompactNodeResize(node, {
        cardMinHeight: FOREACH_LAYOUT.height,
        compactHeight,
        minWidth: FOREACH_LAYOUT.width,
        cardDefaultWidth: FOREACH_LAYOUT.width,
        compactWidth: COMPACT_NODE_WIDTH,
    });

    React.useEffect(() => {
        const onS = () => setSize({ ...node.getSize() });
        node.on('change:size', onS);
        return () => { node.off('change:size', onS); };
    }, [node]);

    React.useEffect(() => {
        const w = size.width || FOREACH_LAYOUT.width;
        const h = size.height;
        const isCompactMode = getGraphNodeViewMode(node) === 'compact';
        const headerH = NODE_HEADER_WITH_ID_HEIGHT;
        const footerTop = headerH;
        const inY = isCompactMode ? headerH / 2 : FOREACH_LAYOUT.portY.in;
        const itemY = isCompactMode ? compactExitPortY(footerTop, 0) : FOREACH_LAYOUT.portY.item;
        const doneY = isCompactMode ? compactExitPortY(footerTop, 1) : FOREACH_LAYOUT.portY.done;

        const ports = [
            { id: 'in', group: 'absolute-in-solid', x: 0, y: inY },
            { id: 'item', group: 'absolute-out-solid', x: w, y: itemY },
            { id: 'done', group: 'absolute-out-hollow', x: w, y: doneY },
        ];
        for (const p of ports) {
            if (!node.hasPort(p.id)) {
                node.addPort({ id: p.id, group: p.group, args: { x: p.x, y: p.y, dx: 0 } });
            } else {
                node.setPortProp(p.id, 'group', p.group);
                node.setPortProp(p.id, 'args', { x: p.x, y: p.y, dx: 0 });
            }
        }
    }, [node, size, isCompact]);

    return (
        <NodeWrapper
            node={node}
            selected={selected}
            themeColor={theme.primary}
            outlineCss={outlineCss}
            backgroundColor={theme.bodyBg}
        >
            <NodeHeader
                icon={ICON}
                title={data.__label || 'ForEach'}
                theme={theme}
                height={NODE_HEADER_WITH_ID_HEIGHT}
                node={node}
                nodeId={node.id}
                onNodeIdChange={(id) => commitFlowNodeIdChange(node, id)}
                onTitleChange={(t) => node.setData({ ...node.getData(), __label: t })}
            />
            {!isCompact && (
                <div style={{ padding: '6px 12px', fontSize: 11, color: '#64748b', lineHeight: 1.4 }}>
                    串行：按序处理列表每一项
                </div>
            )}
            {isCompact ? (
                <CompactExitLabels
                    height={FOREACH_COMPACT_FOOTER}
                    exits={[
                        { id: 'item', label: 'item', color: theme.primary },
                        { id: 'done', label: 'done', color: '#94a3b8' },
                    ]}
                />
            ) : (
                <div
                    style={{
                        marginTop: 'auto',
                        height: 36,
                        display: 'flex',
                        flexDirection: 'column',
                        justifyContent: 'center',
                        alignItems: 'flex-end',
                        paddingRight: 12,
                        fontSize: 10,
                        color: theme.primary,
                        borderTop: `1px solid ${theme.headerBorder}`,
                        gap: 2,
                        boxSizing: 'border-box',
                    }}
                >
                    <span>item</span>
                    <span style={{ color: '#94a3b8' }}>done</span>
                </div>
            )}
            {!isCompact && (
                <ResizeHandle node={node} axes="x" minWidth={FOREACH_LAYOUT.width} minHeight={FOREACH_LAYOUT.height} />
            )}
        </NodeWrapper>
    );
};
