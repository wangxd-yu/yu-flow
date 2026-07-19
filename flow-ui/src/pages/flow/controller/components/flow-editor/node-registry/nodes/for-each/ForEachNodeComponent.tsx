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
    NODE_FOOTER_SAFE_RIGHT,
} from '../../shared/useNodeSelection';
import { commitFlowNodeIdChange } from '../../shared/nodeIdUtils';

export const FOREACH_COLOR = '#0d9488';

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

    React.useEffect(() => {
        const w = node.getSize().width || FOREACH_LAYOUT.width;
        const ports = [
            { id: 'in', group: 'absolute-in-solid', x: 0, y: FOREACH_LAYOUT.portY.in },
            { id: 'item', group: 'absolute-out-solid', x: w, y: FOREACH_LAYOUT.portY.item },
            { id: 'done', group: 'absolute-out-hollow', x: w, y: FOREACH_LAYOUT.portY.done },
        ];
        for (const p of ports) {
            if (!node.hasPort(p.id)) {
                node.addPort({ id: p.id, group: p.group, args: { x: p.x, y: p.y, dx: 0 } });
            } else {
                node.setPortProp(p.id, 'group', p.group);
                node.setPortProp(p.id, 'args', { x: p.x, y: p.y, dx: 0 });
            }
        }
    }, [node]);

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
            <div style={{ padding: '6px 12px', fontSize: 11, color: '#64748b', lineHeight: 1.4 }}>
                串行：按序处理列表每一项
            </div>
            <div
                style={{
                    marginTop: 'auto',
                    height: 36,
                    display: 'flex',
                    flexDirection: 'column',
                    justifyContent: 'center',
                    alignItems: 'flex-end',
                    paddingRight: NODE_FOOTER_SAFE_RIGHT,
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
            <ResizeHandle node={node} axes="x" minWidth={150} minHeight={FOREACH_LAYOUT.height} />
        </NodeWrapper>
    );
};
