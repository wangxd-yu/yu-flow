// ============================================================================
// JsonMapNodeComponent.tsx — 声明式 JSON 字段映射
// ============================================================================

import React from 'react';
import { Button, Input } from 'antd';
import { PlusOutlined, DeleteOutlined } from '@ant-design/icons';
import { Node } from '@antv/x6';
import {
    NodeHeader,
    NodeWrapper,
    useNodeSelection,
    getNodeTheme,
    ResizeHandle,
} from '../../shared/useNodeSelection';
import { commitFlowNodeIdChange } from '../../shared/nodeIdUtils';
import {
    PAYLOAD_PORT_Y,
    ensurePayloadPort,
    PayloadEntryChrome,
    hasPayloadInput,
} from '../../shared/usePayloadEntryPort';
import {
    HEADER_HEIGHT,
    MIN_WIDTH,
    ROW_HEIGHT,
    VAR_PADDING,
} from '../../shared/BaseExpressionNode';
import {
    COMPACT_FOOTER_HEIGHT,
    COMPACT_NODE_WIDTH,
    getGraphNodeViewMode,
    useCompactNodeResize,
} from '../../shared/NodeViewMode';
import {
    NODE_FOOTER_HEIGHT,
    NodeResultFooter,
    singleOutPortY,
} from '../../shared/NodeFooter';

export const JSON_MAP_COLOR = '#531dab';

type MapRow = { id: string; target: string; source: string };

export const JSON_MAP_LAYOUT = {
    width: Math.max(MIN_WIDTH, 280),
    headerHeight: HEADER_HEIGHT,
    footerHeight: NODE_FOOTER_HEIGHT,
    payloadPortY: PAYLOAD_PORT_Y,
    get totalHeight() {
        return HEADER_HEIGHT + ROW_HEIGHT + VAR_PADDING + NODE_FOOTER_HEIGHT;
    },
    get outPortY() {
        return singleOutPortY(this.totalHeight);
    },
};

const ICON = (
    <svg viewBox="0 0 1024 1024" width="14" height="14" fill="currentColor">
        <path d="M128 256h768v64H128V256zm0 192h512v64H128V448zm0 192h640v64H128V640z" />
    </svg>
);

function normalizeRows(data: any): MapRow[] {
    if (Array.isArray(data.mappings) && data.mappings.length > 0) {
        return data.mappings.map((m: any, i: number) => ({
            id: m.id || `m_${i}`,
            target: m.target || '',
            source: m.source || '',
        }));
    }
    return [{ id: 'm_0', target: '', source: '' }];
}

export const JsonMapNodeComponent: React.FC<{ node: Node }> = ({ node }) => {
    const [data, setData] = React.useState<any>(node.getData() || {});
    const theme = getNodeTheme(data?.themeColor || 'purple');
    const { selected, outlineCss } = useNodeSelection(node);
    const rows = normalizeRows(data);
    const hasPayload = hasPayloadInput(data);
    const [size, setSize] = React.useState(node.getSize());

    const sync = (partial: Record<string, unknown>) => {
        const next = { ...node.getData(), ...partial };
        node.setData(next);
        setData(next);
    };

    const syncMappings = (nextRows: MapRow[]) => {
        sync({
            mappings: nextRows.map(({ target, source }) => ({ target, source })),
            __mapRows: nextRows,
        });
    };

    React.useEffect(() => {
        const onD = () => setData({ ...(node.getData() || {}) });
        const onS = () => setSize({ ...node.getSize() });
        node.on('change:data', onD);
        node.on('change:size', onS);
        return () => {
            node.off('change:data', onD);
            node.off('change:size', onS);
        };
    }, [node]);

    const minH =
        HEADER_HEIGHT + rows.length * ROW_HEIGHT + VAR_PADDING + NODE_FOOTER_HEIGHT;
    const compactHeight = HEADER_HEIGHT + COMPACT_FOOTER_HEIGHT;
    const { isCompact } = useCompactNodeResize(node, {
        cardMinHeight: minH,
        compactHeight,
        minWidth: JSON_MAP_LAYOUT.width,
        cardDefaultWidth: JSON_MAP_LAYOUT.width,
        compactWidth: COMPACT_NODE_WIDTH,
    });

    React.useEffect(() => {
        ensurePayloadPort(node, PAYLOAD_PORT_Y);
        const s = node.getSize();
        const isCompactMode = getGraphNodeViewMode(node) === 'compact';
        const outY = singleOutPortY(s.height, isCompactMode);
        if (!node.hasPort('out')) {
            node.addPort({
                id: 'out',
                group: 'absolute-out-solid',
                args: { x: s.width, y: outY, dx: 0 },
            });
        } else {
            node.setPortProp('out', 'args', { x: s.width, y: outY, dx: 0 });
        }
    }, [node, size, rows.length, isCompact]);

    React.useEffect(() => {
        if (!isCompact && Math.abs(node.getSize().height - minH) > 2) {
            node.resize(Math.max(node.getSize().width, JSON_MAP_LAYOUT.width), minH);
        }
    }, [minH, node, isCompact]);

    return (
        <NodeWrapper
            node={node}
            selected={selected}
            themeColor={theme.primary}
            outlineCss={outlineCss}
            backgroundColor={theme.bodyBg}
        >
            <PayloadEntryChrome node={node} hasPayload={hasPayload} />
            <NodeHeader
                icon={ICON}
                title={data.__label || 'JsonMap'}
                theme={theme}
                height={HEADER_HEIGHT}
                node={node}
                nodeId={node.id}
                onNodeIdChange={(id) => commitFlowNodeIdChange(node, id)}
                onTitleChange={(t) => sync({ __label: t })}
            />
            {!isCompact && (
                <div style={{ padding: `0 8px ${VAR_PADDING / 2}px`, flex: 1 }} onMouseDown={(e) => e.stopPropagation()}>
                    {rows.map((row, idx) => (
                        <div key={row.id} style={{ display: 'flex', gap: 4, marginBottom: 4, alignItems: 'center' }}>
                            <Input
                                size="small"
                                placeholder="target"
                                value={row.target}
                                style={{ flex: 1 }}
                                onChange={(e) => {
                                    const next = [...rows];
                                    next[idx] = { ...row, target: e.target.value };
                                    syncMappings(next);
                                }}
                            />
                            <Input
                                size="small"
                                placeholder="$.path 或 user.id"
                                value={row.source}
                                style={{ flex: 2 }}
                                onChange={(e) => {
                                    const next = [...rows];
                                    next[idx] = { ...row, source: e.target.value };
                                    syncMappings(next);
                                }}
                            />
                            <Button
                                type="text"
                                size="small"
                                icon={<DeleteOutlined />}
                                disabled={rows.length <= 1}
                                onClick={() => syncMappings(rows.filter((_, i) => i !== idx))}
                            />
                        </div>
                    ))}
                    <Button
                        type="dashed"
                        size="small"
                        block
                        icon={<PlusOutlined />}
                        onClick={() =>
                            syncMappings([...rows, { id: `m_${Date.now()}`, target: '', source: '' }])
                        }
                    >
                        添加映射
                    </Button>
                </div>
            )}
            <NodeResultFooter label="out" isCompact={isCompact} />
            {!isCompact && (
                <ResizeHandle node={node} axes="x" minWidth={JSON_MAP_LAYOUT.width} minHeight={minH} />
            )}
        </NodeWrapper>
    );
};
