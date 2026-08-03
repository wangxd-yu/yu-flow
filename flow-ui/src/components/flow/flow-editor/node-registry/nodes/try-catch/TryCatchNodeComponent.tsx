// ============================================================================
// TryCatchNodeComponent.tsx — 局部 try/catch 错误边界
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
import {
    COMPACT_NODE_WIDTH,
    CompactExitLabels,
    HTTP_COMPACT_FOOTER_HEIGHT,
    compactExitPortY,
    getGraphNodeViewMode,
    useCompactNodeResize,
} from '../../shared/NodeViewMode';

export const TRY_CATCH_COLOR = '#7c3aed';

const COMPACT_FOOTER = HTTP_COMPACT_FOOTER_HEIGHT;
const CARD_FOOTER = 56;

export const TRY_CATCH_LAYOUT = {
    width: 176,
    height: NODE_HEADER_WITH_ID_HEIGHT + 36 + CARD_FOOTER,
    portY: {
        in: 72,
        try: 68,
        catch: 88,
        out: 108,
    },
} as const;

const ICON = (
    <svg viewBox="0 0 1024 1024" width="14" height="14" fill="currentColor">
        <path d="M512 64l384 704H128L512 64zm0 128L246 704h532L512 192zM480 384h64v256h-64V384zm0 320h64v64h-64v-64z" />
    </svg>
);

export const TryCatchNodeComponent: React.FC<{ node: Node }> = ({ node }) => {
    const data = (node.getData() as any) || {};
    const theme = getNodeTheme(data?.themeColor || 'purple');
    const { selected, outlineCss } = useNodeSelection(node);
    const [size, setSize] = React.useState(node.getSize());
    const compactHeight = NODE_HEADER_WITH_ID_HEIGHT + COMPACT_FOOTER;

    const { isCompact } = useCompactNodeResize(node, {
        cardMinHeight: TRY_CATCH_LAYOUT.height,
        compactHeight,
        minWidth: TRY_CATCH_LAYOUT.width,
        cardDefaultWidth: TRY_CATCH_LAYOUT.width,
        compactWidth: COMPACT_NODE_WIDTH,
    });

    React.useEffect(() => {
        const onS = () => setSize({ ...node.getSize() });
        node.on('change:size', onS);
        return () => { node.off('change:size', onS); };
    }, [node]);

    React.useEffect(() => {
        const w = size.width || TRY_CATCH_LAYOUT.width;
        const isCompactMode = getGraphNodeViewMode(node) === 'compact';
        const headerH = NODE_HEADER_WITH_ID_HEIGHT;
        const inY = isCompactMode ? headerH / 2 : TRY_CATCH_LAYOUT.portY.in;
        const footerTop = isCompactMode ? headerH : headerH + 36;
        const tryY = isCompactMode ? compactExitPortY(footerTop, 0) : TRY_CATCH_LAYOUT.portY.try;
        const catchY = isCompactMode ? compactExitPortY(footerTop, 1) : TRY_CATCH_LAYOUT.portY.catch;
        const outY = isCompactMode ? compactExitPortY(footerTop, 2) : TRY_CATCH_LAYOUT.portY.out;

        const ports = [
            { id: 'in', group: 'absolute-in-solid', x: 0, y: inY },
            { id: 'try', group: 'absolute-out-solid', x: w, y: tryY },
            { id: 'catch', group: 'absolute-out-hollow', x: w, y: catchY },
            { id: 'out', group: 'absolute-out-solid', x: w, y: outY },
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
                title={data.__label || 'TryCatch'}
                theme={theme}
                height={NODE_HEADER_WITH_ID_HEIGHT}
                node={node}
                nodeId={node.id}
                onNodeIdChange={(id) => commitFlowNodeIdChange(node, id)}
                onTitleChange={(t) => node.setData({ ...node.getData(), __label: t })}
            />
            {!isCompact && (
                <div style={{ padding: '6px 12px', fontSize: 11, color: '#64748b', lineHeight: 1.4 }}>
                    try 子流异常时走 catch，不向上抛
                </div>
            )}
            {isCompact ? (
                <CompactExitLabels
                    height={COMPACT_FOOTER}
                    exits={[
                        { id: 'try', label: 'try', color: theme.primary },
                        { id: 'catch', label: 'catch', color: '#ff4d4f' },
                        { id: 'out', label: 'out', color: '#52c41a' },
                    ]}
                />
            ) : (
                <div
                    style={{
                        marginTop: 'auto',
                        height: CARD_FOOTER,
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
                    <span>try</span>
                    <span style={{ color: '#ff4d4f' }}>catch</span>
                    <span style={{ color: '#52c41a' }}>out</span>
                </div>
            )}
            {!isCompact && (
                <ResizeHandle node={node} axes="x" minWidth={TRY_CATCH_LAYOUT.width} minHeight={TRY_CATCH_LAYOUT.height} />
            )}
        </NodeWrapper>
    );
};
