// ============================================================================
// OssNodeComponent.tsx — OSS 对象存储节点（success/fail 双出口）
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
    usePayloadEntryPort,
    hasPayloadInput,
    PayloadEntryChrome,
} from '../../shared/usePayloadEntryPort';
import {
    COMPACT_FOOTER_HEIGHT,
    COMPACT_NODE_WIDTH,
    CompactExitLabels,
    compactExitPortY,
    getGraphNodeViewMode,
    useCompactNodeResize,
} from '../../shared/NodeViewMode';
import { NODE_FOOTER_SAFE_RIGHT } from '../../shared/useNodeSelection';
import { queryOssConnectionOptions } from '@/services/flow/ossConnection';

export const OSS_COLOR = '#722ed1';
const FOOTER_HEIGHT = 56;
const MIN_FIELDS_HEIGHT = 120;

export const OSS_LAYOUT = {
    width: Math.max(MIN_WIDTH, 280),
    headerHeight: HEADER_HEIGHT,
    footerHeight: FOOTER_HEIGHT,
    payloadPortY: PAYLOAD_PORT_Y,
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

const varPortY = (idx: number) =>
    HEADER_HEIGHT + VAR_PADDING / 2 + idx * ROW_HEIGHT + ROW_HEIGHT / 2;

export const OssNodeComponent: React.FC<{ node: Node }> = ({ node }) => {
    const { selected, graph, data, onChange } = useNodeSelection(node);
    const viewMode = getGraphNodeViewMode(graph);
    const isCompact = viewMode === 'compact';
    const { variables, addVariable, removeVariable, updateVariable } = useNodeVariables(node, data);
    usePayloadEntryPort(node);
    const { width, height, onResizeMouseDown } = useCompactNodeResize(node, OSS_LAYOUT.width, OSS_LAYOUT.height);

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

    const operation = data.operation || 'put';
    const theme = getNodeTheme(OSS_COLOR, selected);

    if (isCompact) {
        return (
            <NodeWrapper selected={selected} color={OSS_COLOR} width={COMPACT_NODE_WIDTH} height={COMPACT_FOOTER_HEIGHT + 24}>
                <NodeHeader title="OSS" color={OSS_COLOR} subtitle={operation} />
                <CompactExitLabels />
            </NodeWrapper>
        );
    }

    return (
        <NodeWrapper selected={selected} color={OSS_COLOR} width={width} height={height}>
            <PayloadEntryChrome node={node} />
            <NodeHeader
                title="OSS 存储"
                color={OSS_COLOR}
                subtitle={data.objectKey ? String(data.objectKey).slice(0, 20) : operation}
                nodeId={node.id}
                onNodeIdChange={(id) => commitFlowNodeIdChange(graph, node, id)}
            />
            <div style={{ padding: '4px 8px', flex: 1, display: 'flex', flexDirection: 'column', gap: 4, minHeight: MIN_FIELDS_HEIGHT }}>
                <Select
                    size="small"
                    style={{ width: '100%' }}
                    value={operation}
                    options={[
                        { label: 'put', value: 'put' },
                        { label: 'get', value: 'get' },
                        { label: 'delete', value: 'delete' },
                        { label: 'list', value: 'list' },
                        { label: 'presignGet', value: 'presignGet' },
                    ]}
                    onChange={(v) => onChange({ operation: v })}
                />
                <Select
                    size="small"
                    style={{ width: '100%' }}
                    placeholder="OSS 连接"
                    value={data.connectionCode || undefined}
                    options={connOptions}
                    allowClear
                    showSearch
                    optionFilterProp="label"
                    getPopupContainer={() => document.body}
                    onChange={(v) => onChange({ connectionCode: v || '' })}
                />
                <Input
                    size="small"
                    placeholder="bucket（可选）"
                    value={data.bucket || ''}
                    onChange={(e) => onChange({ bucket: e.target.value })}
                />
                {operation !== 'list' && (
                    <Input
                        size="small"
                        placeholder="objectKey ${var}"
                        value={data.objectKey || ''}
                        onChange={(e) => onChange({ objectKey: e.target.value })}
                    />
                )}
                {operation === 'list' && (
                    <Input
                        size="small"
                        placeholder="listPrefix"
                        value={data.listPrefix || ''}
                        onChange={(e) => onChange({ listPrefix: e.target.value })}
                    />
                )}
            </div>
            {hasPayloadInput(data) && (
                <DynamicVariableList
                    variables={variables}
                    onAdd={addVariable}
                    onRemove={removeVariable}
                    onUpdate={updateVariable}
                    varPortY={varPortY}
                />
            )}
            <div
                style={{
                    position: 'absolute',
                    bottom: 0,
                    left: 0,
                    right: NODE_FOOTER_SAFE_RIGHT,
                    height: FOOTER_HEIGHT,
                    borderTop: `1px solid ${theme.border}`,
                    display: 'flex',
                    alignItems: 'center',
                    justifyContent: 'space-around',
                    fontSize: 11,
                    color: theme.muted,
                }}
            >
                <span>success</span>
                <span>fail</span>
            </div>
            <ResizeHandle onMouseDown={onResizeMouseDown} />
        </NodeWrapper>
    );
};

export default OssNodeComponent;
