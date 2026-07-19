// ============================================================================
// ErrorHandlerNodeComponent.tsx — 统一错误处理入口
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
import {
    COMPACT_FOOTER_HEIGHT,
    getGraphNodeViewMode,
    useCompactNodeResize,
} from '../../shared/NodeViewMode';

export const ERROR_HANDLER_COLOR = '#dc2626';

export const ERROR_HANDLER_LAYOUT = {
    width: 180,
    headerHeight: NODE_HEADER_WITH_ID_HEIGHT,
    footerHeight: NODE_FOOTER_HEIGHT,
    get height() {
        return NODE_HEADER_WITH_ID_HEIGHT + 40 + NODE_FOOTER_HEIGHT;
    },
    get outPortY() {
        return this.height - NODE_FOOTER_HEIGHT + NODE_FOOTER_PORT_OFFSET_Y;
    },
};

const ICON = (
    <svg viewBox="0 0 1024 1024" width="14" height="14" fill="currentColor">
        <path d="M512 64L64 896h896L512 64zm0 192l288 512H224l288-512zm-32 192h64v192h-64V448zm0 256h64v64h-64v-64z" />
    </svg>
);

export const ErrorHandlerNodeComponent: React.FC<{ node: Node }> = ({ node }) => {
    const data = (node.getData() as any) || {};
    const theme = getNodeTheme(data?.themeColor || 'red');
    const { selected, outlineCss } = useNodeSelection(node);
    const [size, setSize] = React.useState(node.getSize());
    const compactHeight = NODE_HEADER_WITH_ID_HEIGHT + COMPACT_FOOTER_HEIGHT;

    const { isCompact } = useCompactNodeResize(node, {
        cardMinHeight: ERROR_HANDLER_LAYOUT.height,
        compactHeight,
        minWidth: 160,
    });

    React.useEffect(() => {
        const onS = () => setSize({ ...node.getSize() });
        node.on('change:size', onS);
        return () => { node.off('change:size', onS); };
    }, [node]);

    React.useEffect(() => {
        const w = size.width || ERROR_HANDLER_LAYOUT.width;
        const h = size.height;
        const isCompactMode = getGraphNodeViewMode(node) === 'compact';
        const fh = isCompactMode ? COMPACT_FOOTER_HEIGHT : NODE_FOOTER_HEIGHT;
        const outY = isCompactMode ? h - fh + fh / 2 : ERROR_HANDLER_LAYOUT.outPortY;

        if (!node.hasPort('out')) {
            node.addPort({
                id: 'out',
                group: 'absolute-out-solid',
                args: { x: w, y: outY, dx: 0 },
            });
        } else {
            node.setPortProp('out', 'group', 'absolute-out-solid');
            node.setPortProp('out', 'args', { x: w, y: outY, dx: 0 });
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
                title={data.__label || 'ErrorHandler'}
                theme={theme}
                height={NODE_HEADER_WITH_ID_HEIGHT}
                node={node}
                nodeId={node.id}
                onNodeIdChange={(id) => commitFlowNodeIdChange(node, id)}
                onTitleChange={(t) => node.setData({ ...node.getData(), __label: t })}
            />
            {!isCompact && (
                <div style={{ padding: '8px 12px', fontSize: 11, color: '#64748b', lineHeight: 1.45 }}>
                    引擎异常时跳转至此；用 $.error 读取详情。
                </div>
            )}
            <NodeOutFooter
                label="out"
                color={theme.primary}
                borderColor={theme.headerBorder}
                style={{ marginTop: isCompact ? 0 : 'auto', height: isCompact ? COMPACT_FOOTER_HEIGHT : undefined }}
            />
            {!isCompact && (
                <ResizeHandle node={node} axes="x" minWidth={160} minHeight={ERROR_HANDLER_LAYOUT.height} />
            )}
        </NodeWrapper>
    );
};
