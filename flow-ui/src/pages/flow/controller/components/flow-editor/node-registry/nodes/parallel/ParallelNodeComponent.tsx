// ============================================================================
// ParallelNodeComponent.tsx — 并行网关（图扇出）
// ============================================================================

import React from 'react';
import { Node } from '@antv/x6';
import {
    useNodeSelection,
    getNodeTheme,
    NodeHeader,
    NodeWrapper,
    ResizeHandle,
    NodeOutFooter,
    NODE_HEADER_WITH_ID_HEIGHT,
    NODE_FOOTER_HEIGHT,
    NODE_FOOTER_PORT_OFFSET_Y,
} from '../../shared/useNodeSelection';
import { commitFlowNodeIdChange } from '../../shared/nodeIdUtils';

export const PARALLEL_COLOR = '#6366f1';

export const PARALLEL_LAYOUT = {
    width: 180,
    headerHeight: NODE_HEADER_WITH_ID_HEIGHT,
    footerHeight: NODE_FOOTER_HEIGHT,
    get height() {
        return NODE_HEADER_WITH_ID_HEIGHT + 48 + NODE_FOOTER_HEIGHT;
    },
    get inPortY() {
        return NODE_HEADER_WITH_ID_HEIGHT + 24;
    },
    get outPortY() {
        return this.height - NODE_FOOTER_HEIGHT + NODE_FOOTER_PORT_OFFSET_Y;
    },
};

const ICON = (
    <svg viewBox="0 0 1024 1024" width="14" height="14" fill="currentColor">
        <path d="M160 192h128v640H160zm288 0h128v640H448zm288 0h128v640H736z" />
    </svg>
);

export const ParallelNodeComponent: React.FC<{ node: Node }> = ({ node }) => {
    const data = (node.getData() as any) || {};
    const theme = getNodeTheme(data?.themeColor || 'purple');
    const { selected, outlineCss } = useNodeSelection(node);

    React.useEffect(() => {
        const w = node.getSize().width || PARALLEL_LAYOUT.width;
        const ensure = (id: string, group: string, x: number, y: number) => {
            if (!node.hasPort(id)) {
                node.addPort({ id, group, args: { x, y, dx: 0 } });
            } else {
                node.setPortProp(id, 'group', group);
                node.setPortProp(id, 'args', { x, y, dx: 0 });
            }
        };
        ensure('in', 'absolute-in-solid', 0, PARALLEL_LAYOUT.inPortY);
        ensure('out', 'absolute-out-solid', w, PARALLEL_LAYOUT.outPortY);
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
                title={data.__label || 'Parallel'}
                theme={theme}
                height={NODE_HEADER_WITH_ID_HEIGHT}
                node={node}
                nodeId={node.id}
                onNodeIdChange={(id) => commitFlowNodeIdChange(node, id)}
                onTitleChange={(t) => node.setData({ ...node.getData(), __label: t })}
            />
            <div style={{ padding: '8px 12px', fontSize: 11, color: '#64748b', lineHeight: 1.45 }}>
                从 out 拉多条线到不同下游即并行；汇入同一节点自动 join。
            </div>
            <NodeOutFooter label="out × N" color={theme.primary} borderColor={theme.headerBorder} style={{ marginTop: 'auto' }} />
            <ResizeHandle node={node} axes="x" minWidth={160} minHeight={PARALLEL_LAYOUT.height} />
        </NodeWrapper>
    );
};
