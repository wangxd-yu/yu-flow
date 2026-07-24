// ============================================================================
// SendMailNodeComponent.tsx — 发送邮件（in:payload + 变量行；自动增高 + 双向缩放）
// ============================================================================

import React from 'react';
import { Input } from 'antd';
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

export const SEND_MAIL_COLOR = '#ea580c';

/** to + subject + text 区最小高度（垂直拉高时该区 flex 吃掉多余高度） */
const MIN_FIELDS_HEIGHT = 108;
const FOOTER_HEIGHT = NODE_FOOTER_HEIGHT;

export const SEND_MAIL_LAYOUT = {
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
        <path d="M928 160H96c-17.7 0-32 14.3-32 32v640c0 17.7 14.3 32 32 32h832c17.7 0 32-14.3 32-32V192c0-17.7-14.3-32-32-32zm-40 110.5V792H136V270.5l384 239.1 368-239.1zM512 444.2L176.5 224h671L512 444.2z" />
    </svg>
);

export const SendMailNodeComponent: React.FC<{ node: Node }> = ({ node }) => {
    const [data, setData] = React.useState<any>(node.getData() || {});
    const theme = getNodeTheme(data?.themeColor || 'orange');
    const { selected, outlineCss } = useNodeSelection(node);
    const [size, setSize] = React.useState(node.getSize());
    const [resizing, setResizing] = React.useState(false);
    const hasPayload = hasPayloadInput(data);

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
        minWidth: SEND_MAIL_LAYOUT.width,
        cardDefaultWidth: SEND_MAIL_LAYOUT.width,
        compactWidth: COMPACT_NODE_WIDTH,
        resizing,
    });

    // 变量增减时自动撑高（不低于 minH；不压缩用户手动拉高的部分）
    React.useEffect(() => {
        if (resizing || isCompact) return;
        const s = node.getSize();
        if (s.height < minH) {
            node.resize(Math.max(s.width, SEND_MAIL_LAYOUT.width), minH);
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

        const w = size.width || SEND_MAIL_LAYOUT.width;
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
                    title={data.__label || 'Send Mail'}
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
                        <Input
                            size="small"
                            value={data.to || ''}
                            placeholder="to（可用 ${var}）"
                            onChange={(e) => patch({ to: e.target.value })}
                        />
                        <Input
                            size="small"
                            value={data.subject || ''}
                            placeholder="subject（可用 ${var}）"
                            onChange={(e) => patch({ subject: e.target.value })}
                        />
                        <Input.TextArea
                            size="small"
                            value={data.text || ''}
                            placeholder="text 正文（可用 ${var}）"
                            onChange={(e) => patch({ text: e.target.value })}
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
                    minWidth={SEND_MAIL_LAYOUT.width}
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
