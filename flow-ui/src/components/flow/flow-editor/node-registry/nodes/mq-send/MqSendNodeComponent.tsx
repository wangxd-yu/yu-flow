// ============================================================================
// MqSendNodeComponent.tsx — 发送 MQ 消息（in:payload + 变量行；自动增高 + 双向缩放）
// ============================================================================

import React from 'react';
import { Input, Select } from 'antd';
import { Node } from '@antv/x6';
import {
    useNodeSelection,
    getNodeTheme,
    NodeHeader,
    NodeWrapper,
    ResizeHandle,
} from '../../shared/useNodeSelection';
import { useNodeVariables } from '../../shared/useNodeVariables';
import { DynamicVariableList } from '../../shared/DynamicVariableList';
import { commitFlowNodeIdChange } from '../../shared/nodeIdUtils';
import {
    HEADER_HEIGHT,
    ROW_HEIGHT,
    VAR_PADDING,
    COND_PADDING,
    MIN_WIDTH,
} from '../../shared/BaseExpressionNode';
import {
    PAYLOAD_PORT_Y,
    ensurePayloadPort,
    usePayloadEntryConnection,
    hasPayloadInput,
    PayloadEntryChrome,
} from '../../shared/usePayloadEntryPort';
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
import { queryMqConnectionOptions } from '@/services/flow/mqConnection';

export const MQ_SEND_COLOR = '#13c2c2';

/** 连接 + topic + message 区最小高度（垂直拉高时该区 flex 吃掉多余高度） */
const MIN_FIELDS_HEIGHT = 108;
const FOOTER_HEIGHT = NODE_FOOTER_HEIGHT;

export const MQ_SEND_LAYOUT = {
    width: Math.max(MIN_WIDTH, 280),
    headerHeight: HEADER_HEIGHT,
    footerHeight: FOOTER_HEIGHT,
    payloadPortY: PAYLOAD_PORT_Y,
    /** 默认高度：1 行变量占位 + 字段区 */
    get height() {
        return (
            HEADER_HEIGHT +
            ROW_HEIGHT +
            VAR_PADDING +
            MIN_FIELDS_HEIGHT +
            COND_PADDING +
            FOOTER_HEIGHT
        );
    },
    get outPortY() {
        return singleOutPortY(this.height);
    },
};

const varPortY = (idx: number) =>
    HEADER_HEIGHT + VAR_PADDING / 2 + idx * ROW_HEIGHT + ROW_HEIGHT / 2;

const ICON = (
    <svg viewBox="0 0 1024 1024" width="14" height="14" fill="currentColor">
        <path d="M931.4 498.9L94.9 79.5c-3.4-1.7-7.3-2.1-11-1.2-8.5 2.1-13.8 10.7-11.7 19.3l86.2 352.2c1.3 5.3 5.2 9.6 10.4 11.3l147.7 50.7-147.6 50.7c-5.2 1.8-9.1 6-10.3 11.3L72.2 926.5c-.9 3.7-.5 7.6 1.2 10.9 3.9 7.9 13.5 11.1 21.5 7.2l836.5-417c3.1-1.5 5.6-4.1 7.2-7.1 3.9-8 .7-17.6-7.2-21.6zM170.8 826.3l50.3-205.6 295.2-101.3c2.3-.8 4.2-2.6 5-5 1.4-4.2-.8-8.7-5-10.2L221.1 403 171 198.2l628 314.9-628.2 313.2z" />
    </svg>
);

export const MqSendNodeComponent: React.FC<{ node: Node }> = ({ node }) => {
    const [data, setData] = React.useState<any>(node.getData() || {});
    const theme = getNodeTheme(data?.themeColor || 'cyan');
    const { selected, outlineCss } = useNodeSelection(node);
    const [size, setSize] = React.useState(node.getSize());
    const [resizing, setResizing] = React.useState(false);
    const hasPayload = hasPayloadInput(data);

    // ── MQ 连接下拉选项 ──
    const [connOptions, setConnOptions] = React.useState<{ label: string; value: string }[]>([]);
    React.useEffect(() => {
        queryMqConnectionOptions()
            .then((list: any[]) => {
                setConnOptions(
                    (list || []).map((c: any) => ({
                        label: `${c.name} (${c.mqType})`,
                        value: c.code,
                    })),
                );
            })
            .catch(() => setConnOptions([]));
    }, []);

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

    const {
        variables,
        dragState,
        hoverRowIndex,
        setHoverRowIndex,
        updateEdges,
        onAddVar,
        onUpdateVar,
        onRemoveVar,
        handleDragStart,
    } = useNodeVariables(node, { varPortY, rowHeight: ROW_HEIGHT });

    usePayloadEntryConnection(node);

    const varCount = Math.max(variables.length, 1);
    const contentH =
        HEADER_HEIGHT +
        varCount * ROW_HEIGHT +
        VAR_PADDING +
        COND_PADDING +
        FOOTER_HEIGHT;
    const minH = contentH + MIN_FIELDS_HEIGHT;
    const compactHeight = HEADER_HEIGHT + COMPACT_FOOTER_HEIGHT;

    const { isCompact } = useCompactNodeResize(node, {
        cardMinHeight: minH,
        compactHeight,
        minWidth: MQ_SEND_LAYOUT.width,
        cardDefaultWidth: MQ_SEND_LAYOUT.width,
        compactWidth: COMPACT_NODE_WIDTH,
        resizing,
    });

    // 变量增减时自动撑高（不低于 minH；不压缩用户手动拉高的部分）
    React.useEffect(() => {
        if (resizing || isCompact) return;
        const s = node.getSize();
        if (s.height < minH) {
            node.resize(Math.max(s.width, MQ_SEND_LAYOUT.width), minH);
        }
    }, [minH, node, resizing, isCompact]);

    const patch = (partial: Record<string, any>) => {
        node.setData({ ...node.getData(), ...partial });
    };

    const syncOutPort = React.useCallback(
        (nw: number, nh: number) => {
            const isCompactMode = getGraphNodeViewMode(node) === 'compact';
            const outY = singleOutPortY(nh, isCompactMode);
            try {
                if (!node.hasPort('out')) {
                    node.addPort({
                        id: 'out',
                        group: 'absolute-out-solid',
                        args: { x: nw, y: outY, dx: 0 },
                        zIndex: 1,
                    });
                } else {
                    if (node.getPort('out')?.group !== 'absolute-out-solid') {
                        node.setPortProp('out', 'group', 'absolute-out-solid');
                    }
                    node.setPortProp('out', 'args', { x: nw, y: outY, dx: 0 });
                }
                updateEdges('out');
            } catch {
                /* ignore */
            }
        },
        [node, updateEdges],
    );

    // 端口：in:payload + in:var:* + out；去掉历史控制流 in
    React.useEffect(() => {
        if (resizing) return;
        const ports = node.getPorts();
        const existing = new Set(ports.map((p) => p.id));
        if (existing.has('in')) {
            try {
                node.removePort('in');
            } catch {
                /* ignore */
            }
        }
        ensurePayloadPort(node, PAYLOAD_PORT_Y);

        const w = size.width || MQ_SEND_LAYOUT.width;
        const h = size.height || minH;
        const isCompactMode = getGraphNodeViewMode(node) === 'compact';

        if (isCompactMode) {
            variables.forEach((v) => {
                const pid = `in:var:${v.id}`;
                if (node.hasPort(pid)) {
                    try {
                        node.setPortProp(pid, 'args', { x: 0, y: HEADER_HEIGHT / 2, dx: 0 });
                    } catch {
                        /* ignore */
                    }
                }
            });
        }

        syncOutPort(w, h);
    }, [node, size, isCompact, variables, updateEdges, resizing, minH, syncOutPort]);

    const handleResize = React.useCallback(
        (nw: number, nh: number) => {
            syncOutPort(nw, nh);
        },
        [syncOutPort],
    );

    return (
        <NodeWrapper
            node={node}
            selected={selected}
            themeColor={theme.primary}
            outlineCss={outlineCss}
            backgroundColor={theme.bodyBg}
        >
            <PayloadEntryChrome hasPayload={hasPayload} primaryColor={theme.primary}>
                <NodeHeader
                    icon={ICON}
                    title={data.__label || 'MQ Send'}
                    theme={theme}
                    height={HEADER_HEIGHT}
                    node={node}
                    nodeId={node.id}
                    onNodeIdChange={(id) => commitFlowNodeIdChange(node, id)}
                    onTitleChange={(t) => patch({ __label: t })}
                />
            </PayloadEntryChrome>

            {!isCompact && (
                <>
                    <DynamicVariableList
                        variables={variables}
                        rowHeight={ROW_HEIGHT}
                        dragState={dragState}
                        hoverRowIndex={hoverRowIndex}
                        onHoverChange={setHoverRowIndex}
                        onDragStart={handleDragStart}
                        onAddVar={onAddVar}
                        onUpdateVar={onUpdateVar}
                        onRemoveVar={onRemoveVar}
                    />
                    <div
                        style={{
                            padding: '6px 12px 4px',
                            display: 'flex',
                            flexDirection: 'column',
                            gap: 4,
                            flex: 1,
                            minHeight: MIN_FIELDS_HEIGHT,
                            borderTop: '1px solid #f0f0f0',
                        }}
                        onMouseDown={(e) => e.stopPropagation()}
                    >
                        <Select
                            size="small"
                            value={data.connectionCode || undefined}
                            placeholder="选择 MQ 连接..."
                            options={connOptions}
                            allowClear
                            getPopupContainer={() => document.body}
                            onChange={(v) => patch({ connectionCode: v || '' })}
                        />
                        <Input
                            size="small"
                            value={data.topic || ''}
                            placeholder="topic / routingKey（可用 ${var}）"
                            onChange={(e) => patch({ topic: e.target.value })}
                        />
                        <Input.TextArea
                            size="small"
                            value={data.message || ''}
                            placeholder='消息体模板，如 {"orderId":"${orderId}"}'
                            onChange={(e) => patch({ message: e.target.value })}
                            style={{ flex: 1, minHeight: 48, resize: 'none' }}
                            autoSize={false}
                        />
                    </div>
                </>
            )}

            <NodeResultFooter
                label="out"
                isCompact={isCompact}
                color={theme.primary}
                borderColor={theme.headerBorder}
            />
            {!isCompact && (
                <ResizeHandle
                    node={node}
                    minWidth={MQ_SEND_LAYOUT.width}
                    minHeight={minH}
                    color={theme.primary}
                    onResize={handleResize}
                    onResizeStart={() => setResizing(true)}
                    onResizeEnd={() => setResizing(false)}
                />
            )}
        </NodeWrapper>
    );
};
