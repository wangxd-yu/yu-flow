// ============================================================================
// OssNodeComponent.tsx — OSS 对象存储节点（in:payload + 变量行；success/fail 双出口）
// 结构与 MqSendNodeComponent 保持一致：自动增高 + 双向缩放 + 紧凑视图
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
    NODE_FOOTER_SAFE_RIGHT,
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
    COMPACT_NODE_WIDTH,
    CompactExitLabels,
    HTTP_COMPACT_FOOTER_HEIGHT,
    compactExitPortY,
    getGraphNodeViewMode,
    useCompactNodeResize,
} from '../../shared/NodeViewMode';
import { queryOssConnectionOptions } from '@/services/flow/ossConnection';

export const OSS_COLOR = '#722ed1';

const FOOTER_HEIGHT = 56;
/** 连接 + bucket + objectKey 区最小高度（垂直拉高时该区 flex 吃掉多余高度） */
const MIN_FIELDS_HEIGHT = 96;

const OP_OPTIONS = [
    { label: 'put', value: 'put' },
    { label: 'get', value: 'get' },
    { label: 'delete', value: 'delete' },
    { label: 'list', value: 'list' },
    { label: 'presignGet', value: 'presignGet' },
];

export const OSS_LAYOUT = {
    width: Math.max(MIN_WIDTH, 280),
    headerHeight: HEADER_HEIGHT,
    footerHeight: FOOTER_HEIGHT,
    payloadPortY: PAYLOAD_PORT_Y,
    /** 默认高度：1 行变量占位 + 字段区 */
    get height() {
        return HEADER_HEIGHT + ROW_HEIGHT + VAR_PADDING + MIN_FIELDS_HEIGHT + COND_PADDING + FOOTER_HEIGHT;
    },
    successPortY(h: number) {
        return h - FOOTER_HEIGHT + 28;
    },
    failPortY(h: number) {
        return h - FOOTER_HEIGHT + 48;
    },
};

const varPortY = (idx: number) => HEADER_HEIGHT + VAR_PADDING / 2 + idx * ROW_HEIGHT + ROW_HEIGHT / 2;

const ICON = (
    <svg viewBox="0 0 1024 1024" width="14" height="14" fill="currentColor">
        <path d="M832 64H192c-17.7 0-32 14.3-32 32v832c0 17.7 14.3 32 32 32h640c17.7 0 32-14.3 32-32V96c0-17.7-14.3-32-32-32zM224 896V128h576v768H224zm96-608h384v64H320v-64zm0 160h384v64H320v-64zm0 160h256v64H320v-64z" />
    </svg>
);

export const OssNodeComponent: React.FC<{ node: Node }> = ({ node }) => {
    const [data, setData] = React.useState<any>(node.getData() || {});
    const theme = getNodeTheme(data?.themeColor || 'purple');
    const { selected, outlineCss } = useNodeSelection(node);
    const [size, setSize] = React.useState(node.getSize());
    const [resizing, setResizing] = React.useState(false);
    const hasPayload = hasPayloadInput(data);
    const operation: string = data.operation || 'put';

    const [connOptions, setConnOptions] = React.useState<{ label: string; value: string }[]>([]);
    React.useEffect(() => {
        queryOssConnectionOptions()
            .then((list: any[]) => {
                setConnOptions(
                    (list || []).map((c: any) => ({
                        label: `${c.name} (${c.code})`,
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
    const contentH = HEADER_HEIGHT + varCount * ROW_HEIGHT + VAR_PADDING + COND_PADDING + FOOTER_HEIGHT;
    const minH = contentH + MIN_FIELDS_HEIGHT;
    const compactHeight = HEADER_HEIGHT + HTTP_COMPACT_FOOTER_HEIGHT;

    const { isCompact } = useCompactNodeResize(node, {
        cardMinHeight: minH,
        compactHeight,
        minWidth: OSS_LAYOUT.width,
        cardDefaultWidth: OSS_LAYOUT.width,
        compactWidth: COMPACT_NODE_WIDTH,
        resizing,
    });

    // 变量增减时自动撑高（不低于 minH；不压缩用户手动拉高的部分）
    React.useEffect(() => {
        if (resizing || isCompact) return;
        const s = node.getSize();
        if (s.height < minH) {
            node.resize(Math.max(s.width, OSS_LAYOUT.width), minH);
        }
    }, [minH, node, resizing, isCompact]);

    const patch = (partial: Record<string, any>) => {
        node.setData({ ...node.getData(), ...partial });
    };

    const syncOutPort = React.useCallback(
        (nw: number, nh: number) => {
            const isCompactMode = getGraphNodeViewMode(node) === 'compact';
            const successY = isCompactMode
                ? compactExitPortY(nh - HTTP_COMPACT_FOOTER_HEIGHT, 0)
                : OSS_LAYOUT.successPortY(nh);
            const failY = isCompactMode
                ? compactExitPortY(nh - HTTP_COMPACT_FOOTER_HEIGHT, 1)
                : OSS_LAYOUT.failPortY(nh);
            try {
                if (node.hasPort('out')) node.removePort('out');
                for (const [id, y, group] of [
                    ['success', successY, 'absolute-out-solid'],
                    ['fail', failY, 'absolute-out-hollow'],
                ] as const) {
                    if (!node.hasPort(id)) {
                        node.addPort({ id, group, args: { x: nw, y, dx: 0 }, zIndex: 1 });
                    } else {
                        node.setPortProp(id, 'group', group);
                        node.setPortProp(id, 'args', { x: nw, y, dx: 0 });
                    }
                    updateEdges(id);
                }
            } catch {
                /* ignore */
            }
        },
        [node, updateEdges],
    );

    // 端口：in:payload + in:var:* + success/fail；去掉历史控制流 in
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

        const w = size.width || OSS_LAYOUT.width;
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
                    title={data.__label || 'OSS 存储'}
                    theme={theme}
                    height={HEADER_HEIGHT}
                    node={node}
                    nodeId={node.id}
                    onNodeIdChange={(id) => commitFlowNodeIdChange(node, id)}
                    onTitleChange={(t) => patch({ __label: t })}
                    extra={
                        <Select
                            size="small"
                            value={operation}
                            options={OP_OPTIONS}
                            style={{ width: 96 }}
                            getPopupContainer={() => document.body}
                            onChange={(v) => patch({ operation: v })}
                        />
                    }
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
                            placeholder="选择 OSS 连接..."
                            value={data.connectionCode || undefined}
                            options={connOptions}
                            allowClear
                            showSearch
                            optionFilterProp="label"
                            getPopupContainer={() => document.body}
                            onChange={(v) => patch({ connectionCode: v || '' })}
                        />
                        <Input
                            size="small"
                            placeholder="bucket（空则用连接默认桶）"
                            value={data.bucket || ''}
                            onChange={(e) => patch({ bucket: e.target.value })}
                        />
                        {operation === 'list' ? (
                            <Input
                                size="small"
                                placeholder="listPrefix（可用 ${var}）"
                                value={data.listPrefix || ''}
                                onChange={(e) => patch({ listPrefix: e.target.value })}
                            />
                        ) : (
                            <Input
                                size="small"
                                placeholder="objectKey（可用 ${var}）"
                                value={data.objectKey || ''}
                                onChange={(e) => patch({ objectKey: e.target.value })}
                            />
                        )}
                    </div>
                </>
            )}

            {isCompact ? (
                <CompactExitLabels
                    height={HTTP_COMPACT_FOOTER_HEIGHT}
                    exits={[
                        { id: 'success', label: 'success', color: '#52c41a' },
                        { id: 'fail', label: 'fail', color: '#ff4d4f' },
                    ]}
                />
            ) : (
                <div
                    style={{
                        marginTop: 'auto',
                        height: FOOTER_HEIGHT,
                        display: 'flex',
                        flexDirection: 'column',
                        justifyContent: 'center',
                        alignItems: 'flex-end',
                        paddingRight: NODE_FOOTER_SAFE_RIGHT,
                        fontSize: 10,
                        borderTop: `1px solid ${theme.headerBorder}`,
                        gap: 2,
                        boxSizing: 'border-box',
                    }}
                >
                    <span style={{ color: '#52c41a' }}>success</span>
                    <span style={{ color: '#ff4d4f' }}>fail</span>
                </div>
            )}
            {!isCompact && (
                <ResizeHandle
                    node={node}
                    minWidth={OSS_LAYOUT.width}
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

export default OssNodeComponent;
