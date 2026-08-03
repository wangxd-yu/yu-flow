// ============================================================================
// RedisNodeComponent.tsx — Redis GET/SET/DEL/INCR
// ============================================================================

import React from 'react';
import { Input, InputNumber, Select } from 'antd';
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
    COMPACT_NODE_WIDTH,
    CompactOutFooter,
    compactSingleOutPortY,
    getGraphNodeViewMode,
    useCompactNodeResize,
} from '../../shared/NodeViewMode';

export const REDIS_COLOR = '#cf1322';

const OP_OPTIONS = [
    { value: 'get', label: 'GET' },
    { value: 'set', label: 'SET' },
    { value: 'del', label: 'DEL' },
    { value: 'incr', label: 'INCR' },
];

export const REDIS_LAYOUT = {
    width: 200,
    get height() {
        return NODE_HEADER_WITH_ID_HEIGHT + 72 + NODE_FOOTER_HEIGHT;
    },
    get inPortY() {
        return NODE_HEADER_WITH_ID_HEIGHT + 36;
    },
    get outPortY() {
        return this.height - NODE_FOOTER_HEIGHT + NODE_FOOTER_PORT_OFFSET_Y;
    },
};

const ICON = (
    <svg viewBox="0 0 1024 1024" width="14" height="14" fill="currentColor">
        <path d="M512 128L128 320v384l384 192 384-192V320L512 128zm0 89l256 128v318L512 831 256 663V345l256-128z" />
    </svg>
);

export const RedisNodeComponent: React.FC<{ node: Node }> = ({ node }) => {
    const data = (node.getData() as any) || {};
    const theme = getNodeTheme(data?.themeColor || 'red');
    const { selected, outlineCss } = useNodeSelection(node);
    const op = data.operation || 'get';
    const [size, setSize] = React.useState(node.getSize());
    const compactHeight = NODE_HEADER_WITH_ID_HEIGHT + COMPACT_FOOTER_HEIGHT;

    const { isCompact } = useCompactNodeResize(node, {
        cardMinHeight: REDIS_LAYOUT.height,
        compactHeight,
        minWidth: REDIS_LAYOUT.width,
        cardDefaultWidth: REDIS_LAYOUT.width,
        compactWidth: COMPACT_NODE_WIDTH,
    });

    const patch = (partial: Record<string, unknown>) => {
        node.setData({ ...node.getData(), ...partial });
    };

    React.useEffect(() => {
        const onS = () => setSize({ ...node.getSize() });
        node.on('change:size', onS);
        return () => { node.off('change:size', onS); };
    }, [node]);

    React.useEffect(() => {
        const w = size.width || REDIS_LAYOUT.width;
        const h = size.height;
        const isCompactMode = getGraphNodeViewMode(node) === 'compact';
        const inY = isCompactMode ? NODE_HEADER_WITH_ID_HEIGHT / 2 : REDIS_LAYOUT.inPortY;
        const outY = isCompactMode ? compactSingleOutPortY(h) : h - NODE_FOOTER_HEIGHT + NODE_FOOTER_PORT_OFFSET_Y;
        const ensure = (id: string, group: string, x: number, y: number) => {
            if (!node.hasPort(id)) node.addPort({ id, group, args: { x, y, dx: 0 } });
            else {
                node.setPortProp(id, 'group', group);
                node.setPortProp(id, 'args', { x, y, dx: 0 });
            }
        };
        ensure('in', 'absolute-in-solid', 0, inY);
        ensure('out', 'absolute-out-solid', w, outY);
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
                title={data.__label || 'Redis'}
                theme={theme}
                height={NODE_HEADER_WITH_ID_HEIGHT}
                node={node}
                nodeId={node.id}
                onNodeIdChange={(id) => commitFlowNodeIdChange(node, id)}
                onTitleChange={(t) => node.setData({ ...node.getData(), __label: t })}
                extra={
                    <Select
                        size="small"
                        value={op}
                        options={OP_OPTIONS}
                        style={{ width: 72 }}
                        getPopupContainer={() => document.body}
                        onChange={(v) => patch({ operation: v })}
                    />
                }
            />
            {!isCompact && (
                <div
                    style={{ padding: '4px 12px', display: 'flex', flexDirection: 'column', gap: 6 }}
                    onMouseDown={(e) => e.stopPropagation()}
                >
                    <Input
                        size="small"
                        placeholder="key，支持 ${var}"
                        value={data.key || ''}
                        onChange={(e) => patch({ key: e.target.value })}
                    />
                    {(op === 'set' || op === 'incr') && (
                        <Input
                            size="small"
                            placeholder={op === 'incr' ? '增量，默认 1' : 'value，支持 ${var}'}
                            value={data.value || ''}
                            onChange={(e) => patch({ value: e.target.value })}
                        />
                    )}
                    {op === 'set' && (
                        <InputNumber
                            size="small"
                            min={0}
                            placeholder="TTL 秒（可选）"
                            value={data.ttlSeconds}
                            style={{ width: '100%' }}
                            onChange={(v) => patch({ ttlSeconds: v ?? undefined })}
                        />
                    )}
                </div>
            )}
            {isCompact ? (
                <CompactOutFooter label="out" />
            ) : (
                <NodeOutFooter label="out" color={theme.primary} borderColor={theme.headerBorder} />
            )}
            {!isCompact && (
                <ResizeHandle node={node} axes="x" minWidth={REDIS_LAYOUT.width} minHeight={REDIS_LAYOUT.height} />
            )}
        </NodeWrapper>
    );
};
