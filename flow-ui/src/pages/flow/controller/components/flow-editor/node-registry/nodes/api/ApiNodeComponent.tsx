// ============================================================================
// ApiNodeComponent.tsx
// 内部 Flow API 编排调用（对齐 Database 骨架）：
//   NodeWrapper + NodeHeader + 目标 API 选择 + DynamicVariableList + Footer Result
//   端口：in:payload + in:var:* + out
// ============================================================================

import React from 'react';
import { Typography, Select, Tooltip, Button, message } from 'antd';
import { ReloadOutlined } from '@ant-design/icons';
import { Node } from '@antv/x6';
import {
    NodeHeader,
    NodeWrapper,
    useNodeSelection,
    getNodeTheme,
    ResizeHandle,
} from '../../shared/useNodeSelection';
import { useNodeVariables, type NodeVariable } from '../../shared/useNodeVariables';
import { DynamicVariableList } from '../../shared/DynamicVariableList';
import {
    HEADER_HEIGHT,
    ROW_HEIGHT,
    VAR_PADDING,
    MIN_WIDTH,
} from '../../shared/BaseExpressionNode';
import { commitFlowNodeIdChange } from '../../shared/nodeIdUtils';
import {
    PAYLOAD_PORT_Y,
    ensurePayloadPort,
    usePayloadEntryConnection,
    hasPayloadInput,
    PayloadEntryChrome,
} from '../../shared/usePayloadEntryPort';
import {
    queryAutoApiConfigList,
    queryAutoApiConfigDetail,
} from '@/pages/flow/controller/services/flowController';
import {
    extractContractParams,
    mergeContractParamsIntoVariables,
} from './contractParams';
import {
    COMPACT_FOOTER_HEIGHT,
    getGraphNodeViewMode,
    useCompactNodeResize,
} from '../../shared/NodeViewMode';

const { Text } = Typography;

const SERVICE_ROW_HEIGHT = 36;
/** 目标 API 的 method/url 只读说明行 */
const SERVICE_META_HEIGHT = 20;
const SERVICE_BLOCK_HEIGHT = SERVICE_ROW_HEIGHT + SERVICE_META_HEIGHT;
const FOOTER_HEIGHT = 44;
const FT_RESULT_Y = 12;

export const API_LAYOUT = {
    headerHeight: HEADER_HEIGHT,
    footerHeight: FOOTER_HEIGHT,
    width: MIN_WIDTH,
    payloadPortY: PAYLOAD_PORT_Y,
    get totalHeight() {
        return (
            HEADER_HEIGHT +
            SERVICE_BLOCK_HEIGHT +
            ROW_HEIGHT +
            VAR_PADDING +
            FOOTER_HEIGHT
        );
    },
    get footerTop() {
        return this.totalHeight - FOOTER_HEIGHT;
    },
    get outPortY() {
        return this.footerTop + FT_RESULT_Y;
    },
};

const varPortY = (idx: number) =>
    HEADER_HEIGHT + SERVICE_BLOCK_HEIGHT + VAR_PADDING / 2 + idx * ROW_HEIGHT + ROW_HEIGHT / 2;

type ApiOption = {
    label: string;
    value: string;
    method?: string;
    url?: string;
    name?: string;
};

const ICONS = {
    api: (
        <svg viewBox="0 0 1024 1024" width="14" height="14" fill="currentColor">
            <path d="M880 112H144c-17.7 0-32 14.3-32 32v736c0 17.7 14.3 32 32 32h736c17.7 0 32-14.3 32-32V144c0-17.7-14.3-32-32-32zM292 808H184V216h108v592zm268 0H452V216h108v592zm268 0H720V216h108v592z" />
        </svg>
    ),
};

export interface ApiNodeData {
    serviceId?: string;
    /** 展示用名称，随选择写入 */
    __serviceName?: string;
    /** 目标 API 自身配置的 HTTP Method（只读展示，不可改） */
    __serviceMethod?: string;
    /** 目标 API 自身配置的 URL（只读展示，不可改） */
    __serviceUrl?: string;
    /** 可选：额外写入的上下文变量名（主结果仍在 nodeId.out） */
    output?: string;
    inputs?: Record<string, { extractPath: string; id?: string } | string>;
    themeColor?: string;
    __label?: string;
    __variables?: { id: string; name: string; extractPath: string }[];
}

export const ApiNodeComponent = ({ node }: { node: Node }) => {
    const [data, setData] = React.useState<ApiNodeData>(node.getData() as ApiNodeData);
    const themeObj = getNodeTheme(data?.themeColor || 'blue');
    const { outlineCss, borderColor, selected } = useNodeSelection(node, {
        defaultColor: themeObj.primary,
        selectedColor: themeObj.primary,
    });
    const [size, setSize] = React.useState(node.getSize());

    React.useEffect(() => {
        const onD = () => setData({ ...node.getData() } as ApiNodeData);
        const onS = () => setSize({ ...node.getSize() });
        node.on('change:data', onD);
        node.on('change:size', onS);
        return () => {
            node.off('change:data', onD);
            node.off('change:size', onS);
        };
    }, [node]);

    const serviceId = data?.serviceId;
    const serviceName = data?.__serviceName;
    const serviceMethod = data?.__serviceMethod;
    const serviceUrl = data?.__serviceUrl;

    const [apiOptions, setApiOptions] = React.useState<ApiOption[]>([]);
    const [loadingApis, setLoadingApis] = React.useState(false);

    const loadApis = React.useCallback(async (keyword?: string) => {
        setLoadingApis(true);
        try {
            const res: any = await queryAutoApiConfigList({
                page: 0,
                size: 50,
                name: keyword || undefined,
            });
            const items = res?.items || res?.data?.items || [];
            setApiOptions(
                items.map((item: any) => ({
                    label: item.name
                        ? `${item.name}${item.url ? `  (${item.method || ''} ${item.url})` : ''}`
                        : item.id,
                    value: item.id,
                    name: item.name,
                    method: item.method,
                    url: item.url,
                })),
            );
        } catch {
            setApiOptions([]);
        } finally {
            setLoadingApis(false);
        }
    }, []);

    React.useEffect(() => {
        loadApis();
    }, [loadApis]);

    // 已选 API 不在当前页时，补一条 option 避免 Select 只显示 id
    const selectOptions = React.useMemo(() => {
        if (!serviceId) return apiOptions;
        if (apiOptions.some((o) => o.value === serviceId)) return apiOptions;
        return [
            {
                value: serviceId,
                label: serviceName || serviceId,
                method: serviceMethod,
                url: serviceUrl,
                name: serviceName,
            },
            ...apiOptions,
        ];
    }, [apiOptions, serviceId, serviceName, serviceMethod, serviceUrl]);

    const {
        variables,
        dragState,
        hoverRowIndex,
        setHoverRowIndex,
        updateEdges,
        syncToNodeData,
        onAddVar,
        onUpdateVar,
        onRemoveVar,
        handleDragStart,
    } = useNodeVariables(node, { varPortY, rowHeight: ROW_HEIGHT });

    usePayloadEntryConnection(node);
    const hasPayload = hasPayloadInput(data);

    const [syncingContract, setSyncingContract] = React.useState(false);

    /** 拉取目标 API contract，自动带出入参行（保留已填路径） */
    const syncParamsFromContract = React.useCallback(
        async (targetId: string, existingVars?: NodeVariable[]) => {
            if (!targetId) return;
            setSyncingContract(true);
            try {
                const detail: any = await queryAutoApiConfigDetail(targetId);
                const defs = extractContractParams(detail?.contract);
                const cur =
                    existingVars ||
                    (node.getData() as ApiNodeData)?.__variables ||
                    variables;
                const merged = mergeContractParamsIntoVariables(defs, cur);
                syncToNodeData(merged, {
                    __serviceMethod: detail?.method || (node.getData() as any)?.__serviceMethod,
                    __serviceUrl: detail?.url || (node.getData() as any)?.__serviceUrl,
                    __serviceName:
                        detail?.name || (node.getData() as any)?.__serviceName,
                });
                if (defs.length === 0) {
                    message.info('目标 API 未定义请求入参（contract），可手动添加');
                }
            } catch {
                message.warning('读取目标 API 契约失败，请手动配置入参');
            } finally {
                setSyncingContract(false);
            }
        },
        [node, syncToNodeData, variables],
    );

    React.useEffect(() => {
        const ports = node.getPorts();
        const existing = new Set(ports.map((p) => p.id));

        if (existing.has('in')) {
            node.removePort('in');
        }
        ensurePayloadPort(node, PAYLOAD_PORT_Y);

        const s = node.getSize();
        const isCompactMode = getGraphNodeViewMode(node) === 'compact';
        const fh = isCompactMode ? COMPACT_FOOTER_HEIGHT : FOOTER_HEIGHT;
        const ft = s.height - fh;
        const outY = isCompactMode ? ft + fh / 2 : ft + FT_RESULT_Y;
        const outX = s.width;

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

        if (!existing.has('out')) {
            node.addPort({
                id: 'out',
                group: 'absolute-out-solid',
                args: { x: outX, y: outY, dx: 0 },
                zIndex: 1,
            });
        } else {
            const p = ports.find((port) => port.id === 'out');
            if (p?.attrs?.text?.text !== '') {
                node.setPortProp('out', 'attrs/text/text', '');
            }
            if (p?.group !== 'absolute-out-solid') {
                node.setPortProp('out', 'group', 'absolute-out-solid');
            }
            node.setPortProp('out', 'args', { x: outX, y: outY, dx: 0 });
        }
    }, [variables, node, size]);

    const [resizing, setResizing] = React.useState(false);
    const minH =
        HEADER_HEIGHT +
        SERVICE_BLOCK_HEIGHT +
        variables.length * ROW_HEIGHT +
        VAR_PADDING +
        FOOTER_HEIGHT;

    const compactHeight = HEADER_HEIGHT + COMPACT_FOOTER_HEIGHT;
    const { isCompact } = useCompactNodeResize(node, {
        cardMinHeight: minH,
        compactHeight,
        minWidth: MIN_WIDTH,
        resizing,
    });

    React.useEffect(() => {
        if (!resizing && !isCompact) {
            const s = node.getSize();
            if (s.height < minH) node.resize(Math.max(s.width, MIN_WIDTH), minH);
        }
    }, [minH, node, resizing, isCompact]);

    const handleResize = React.useCallback(
        (nw: number, nh: number) => {
            const isCompactMode = getGraphNodeViewMode(node) === 'compact';
            const fh = isCompactMode ? COMPACT_FOOTER_HEIGHT : FOOTER_HEIGHT;
            const ft = nh - fh;
            const outY = isCompactMode ? ft + fh / 2 : ft + FT_RESULT_Y;
            node.setPortProp('out', 'args', { x: nw, y: outY, dx: 0 });
            updateEdges('out');
        },
        [node, updateEdges],
    );

    React.useEffect(() => {
        if (resizing) return;
        const s = node.getSize();
        const isCompactMode = getGraphNodeViewMode(node) === 'compact';
        const fh = isCompactMode ? COMPACT_FOOTER_HEIGHT : FOOTER_HEIGHT;
        const ft = s.height - fh;
        const outY = isCompactMode ? ft + fh / 2 : ft + FT_RESULT_Y;
        try {
            node.setPortProp('out', 'args', { x: s.width, y: outY, dx: 0 });
            updateEdges('out');
        } catch {
            /* ignore */
        }
    }, [size, variables.length, resizing, node, updateEdges, isCompact]);

    const nodeLabel = data?.__label || 'API Call';
    const handleTitleChange = React.useCallback(
        (newTitle: string) => {
            node.setData({ ...node.getData(), __label: newTitle });
        },
        [node],
    );

    const handleServiceChange = React.useCallback(
        (val: string | undefined, option: any) => {
            const opt: ApiOption | undefined = Array.isArray(option) ? option[0] : option;
            const matched = apiOptions.find((o) => o.value === val);
            const src = matched || opt;
            const prev = node.getData() as ApiNodeData;
            node.setData({
                ...prev,
                serviceId: val || '',
                __serviceName: src?.name || (typeof src?.label === 'string' ? src.label : '') || '',
                __serviceMethod: src?.method || '',
                __serviceUrl: src?.url || '',
            });
            if (val) {
                // 切换目标 API 时带出契约入参
                void syncParamsFromContract(val, prev.__variables || variables);
            }
        },
        [node, apiOptions, syncParamsFromContract, variables],
    );

    return (
        <NodeWrapper
            node={node}
            selected={selected}
            themeColor={borderColor}
            outlineCss={outlineCss}
            backgroundColor={themeObj.bodyBg}
        >
            <PayloadEntryChrome hasPayload={hasPayload} primaryColor={themeObj.primary}>
                <NodeHeader
                    icon={ICONS.api}
                    title={nodeLabel}
                    theme={themeObj}
                    height={HEADER_HEIGHT}
                    node={node}
                    nodeId={node.id}
                    onNodeIdChange={(id) => commitFlowNodeIdChange(node, id)}
                    onTitleChange={handleTitleChange}
                />
            </PayloadEntryChrome>

            {!isCompact && (
            <div
                style={{
                    height: SERVICE_BLOCK_HEIGHT,
                    padding: '4px 12px 2px',
                    display: 'flex',
                    flexDirection: 'column',
                    justifyContent: 'center',
                    gap: 2,
                    borderBottom: '1px solid #f0f0f0',
                    pointerEvents: 'auto',
                    flexShrink: 0,
                }}
            >
                <div style={{ display: 'flex', alignItems: 'center', gap: 8, height: SERVICE_ROW_HEIGHT - 6 }}>
                    <Text style={{ fontSize: 11, color: '#8c8c8c', flexShrink: 0 }}>目标 API</Text>
                    <Select
                        size="small"
                        showSearch
                        allowClear
                        loading={loadingApis || syncingContract}
                        value={serviceId || undefined}
                        placeholder="选择内部 Flow API..."
                        options={selectOptions}
                        filterOption={false}
                        onSearch={(kw) => loadApis(kw)}
                        onChange={handleServiceChange}
                        onMouseDown={(e) => e.stopPropagation()}
                        onClick={(e) => e.stopPropagation()}
                        style={{ flex: 1, fontSize: 11 }}
                        // 挂到 body，避免被 NodeWrapper overflow:hidden 裁切
                        getPopupContainer={() => document.body}
                        dropdownStyle={{ zIndex: 10000 }}
                    />
                    <Tooltip title="从目标 API 契约重新同步入参（保留已填路径）">
                        <Button
                            type="text"
                            size="small"
                            icon={<ReloadOutlined />}
                            loading={syncingContract}
                            disabled={!serviceId}
                            onMouseDown={(e) => e.stopPropagation()}
                            onClick={(e) => {
                                e.stopPropagation();
                                if (serviceId) void syncParamsFromContract(serviceId);
                            }}
                            style={{ width: 24, height: 24, padding: 0, color: themeObj.primary }}
                        />
                    </Tooltip>
                </div>
                <Text
                    style={{
                        fontSize: 10,
                        color: '#8c8c8c',
                        lineHeight: `${SERVICE_META_HEIGHT - 2}px`,
                        overflow: 'hidden',
                        textOverflow: 'ellipsis',
                        whiteSpace: 'nowrap',
                        paddingLeft: 52,
                    }}
                    title={
                        serviceId
                            ? `${serviceMethod || ''} ${serviceUrl || ''}`.trim() || 'Method/URL 随目标 API 配置，此处不可改'
                            : '无需选 Method：沿用目标 API 已配置的 Method/URL'
                    }
                >
                    {serviceId
                        ? `${serviceMethod || '—'} ${serviceUrl || ''}`.trim() || 'Method/URL 来自目标 API'
                        : 'Method/URL 随目标 API，无需在此选择'}
                </Text>
            </div>
            )}

            {!isCompact && (
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
                addLabel="传参给目标 API"
                namePlaceholder="参数名 → @FP"
                pathPlaceholder="来源 $.上游.out"
            />
            )}

            <div
                style={{
                    height: isCompact ? COMPACT_FOOTER_HEIGHT : FOOTER_HEIGHT,
                    position: 'relative',
                    pointerEvents: 'auto',
                    flexShrink: 0,
                    display: 'flex',
                    alignItems: 'center',
                    justifyContent: 'flex-end',
                    padding: '0 12px',
                }}
            >
                <div
                    style={{
                        position: 'absolute',
                        right: 10,
                        top: FT_RESULT_Y,
                        transform: 'translateY(-50%)',
                        display: 'flex',
                        alignItems: 'center',
                        gap: 6,
                    }}
                >
                    <Text style={{ fontSize: 12, color: '#595959' }}>Result</Text>
                </div>
            </div>

            {!isCompact && (
            <ResizeHandle
                node={node}
                minWidth={MIN_WIDTH}
                minHeight={minH}
                onResize={handleResize}
                color={themeObj.primary}
                axes="x"
                onResizeStart={() => setResizing(true)}
                onResizeEnd={() => setResizing(false)}
            />
            )}
        </NodeWrapper>
    );
};

export default ApiNodeComponent;
