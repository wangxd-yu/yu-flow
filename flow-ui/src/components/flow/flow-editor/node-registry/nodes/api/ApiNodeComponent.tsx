// ============================================================================
// ApiNodeComponent.tsx
// 内部 Flow API 编排调用（对齐 Database 骨架）：
//   NodeWrapper + NodeHeader + 目标 API 选择 + DynamicVariableList + Footer Result
//   端口：in:payload + in:var:* + success / fail
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
} from '@/services/flow/flowController';
import {
    getServiceFlow,
    queryServiceFlowPage,
    resolveRuntimeContract,
} from '@/services/flow/serviceFlowService';
import {
    extractContractParams,
    mergeContractParamsIntoVariables,
} from './contractParams';
import {
    extractServiceContractParams,
    mergeServiceContractIntoVariables,
} from './serviceContractParams';
import {
    COMPACT_FOOTER_HEIGHT,
    COMPACT_NODE_WIDTH,
    CompactExitLabels,
    HTTP_COMPACT_FOOTER_HEIGHT,
    compactExitPortY,
    getGraphNodeViewMode,
    useCompactNodeResize,
} from '../../shared/NodeViewMode';
import { NODE_FOOTER_SAFE_RIGHT } from '../../shared/useNodeSelection';

const API_MULTI_FOOTER = 56;

const { Text } = Typography;

const SERVICE_ROW_HEIGHT = 36;
/** 目标 API 的 method/url 只读说明行 */
const SERVICE_META_HEIGHT = 20;
const SERVICE_BLOCK_HEIGHT = SERVICE_ROW_HEIGHT + SERVICE_META_HEIGHT;

export const API_LAYOUT = {
    headerHeight: HEADER_HEIGHT,
    footerHeight: API_MULTI_FOOTER,
    width: MIN_WIDTH,
    payloadPortY: PAYLOAD_PORT_Y,
    get totalHeight() {
        return (
            HEADER_HEIGHT +
            SERVICE_BLOCK_HEIGHT +
            ROW_HEIGHT +
            VAR_PADDING +
            API_MULTI_FOOTER
        );
    },
    get footerTop() {
        return this.totalHeight - API_MULTI_FOOTER;
    },
    successPortY(height: number) {
        return height - API_MULTI_FOOTER + 28;
    },
    failPortY(height: number) {
        return height - API_MULTI_FOOTER + 48;
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
    /** api（默认）| service */
    targetType?: 'api' | 'service';
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

    const targetType: 'api' | 'service' = data?.targetType === 'service' ? 'service' : 'api';
    const serviceId = data?.serviceId;
    const serviceName = data?.__serviceName;
    const serviceMethod = data?.__serviceMethod;
    const serviceUrl = data?.__serviceUrl;

    const [apiOptions, setApiOptions] = React.useState<ApiOption[]>([]);
    const [loadingApis, setLoadingApis] = React.useState(false);

    const loadTargets = React.useCallback(async (keyword?: string) => {
        setLoadingApis(true);
        try {
            if (targetType === 'service') {
                const res: any = await queryServiceFlowPage({
                    page: 0,
                    size: 50,
                    name: keyword || undefined,
                    enabled: true,
                    publishStatus: 1,
                });
                const items = res?.items || res?.data?.items || [];
                setApiOptions(
                    items.map((item: any) => ({
                        label: item.name || item.id,
                        value: item.id,
                        name: item.name,
                    })),
                );
            } else {
                const res: any = await queryAutoApiConfigList({
                    page: 0,
                    size: 50,
                    name: keyword || undefined,
                    publishStatus: 1,
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
            }
        } catch {
            setApiOptions([]);
        } finally {
            setLoadingApis(false);
        }
    }, [targetType]);

    React.useEffect(() => {
        loadTargets();
    }, [loadTargets]);

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

    // 服务 + 已同步契约：去掉末尾「+」占位行与非契约手动行
    React.useEffect(() => {
        if (targetType !== 'service') return;
        const hasContract = variables.some((v) => v.fromContract && !!v.name?.trim());
        if (!hasContract) return;
        const locked = variables.filter((v) => v.fromContract && !!v.name?.trim());
        if (locked.length === variables.length) return;
        syncToNodeData(locked);
    }, [targetType, variables, syncToNodeData]);

    usePayloadEntryConnection(node);
    const hasPayload = hasPayloadInput(data);

    const [syncingContract, setSyncingContract] = React.useState(false);

    /** 拉取目标 API / 服务契约，自动带出入参行（保留已填路径） */
    const syncParamsFromContract = React.useCallback(
        async (targetId: string, existingVars?: NodeVariable[]) => {
            if (!targetId) return;
            setSyncingContract(true);
            try {
                const cur =
                    existingVars ||
                    (node.getData() as ApiNodeData)?.__variables ||
                    variables;

                if (targetType === 'service') {
                    const detail: any = await getServiceFlow(targetId);
                    const svc = detail?.data || detail;
                    const defs = extractServiceContractParams(resolveRuntimeContract(svc));
                    const merged = mergeServiceContractIntoVariables(defs, cur);
                    syncToNodeData(merged, {
                        __serviceMethod: '',
                        __serviceUrl: '',
                        __serviceName: svc?.name || (node.getData() as any)?.__serviceName,
                    });
                    if (defs.length === 0) {
                        message.info('目标服务未定义入参契约，可手动添加（写入 $.service.input）');
                    }
                    return;
                }

                const detail: any = await queryAutoApiConfigDetail(targetId);
                const defs = extractContractParams(detail?.contract);
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
                message.warning(
                    targetType === 'service'
                        ? '读取服务契约失败，请手动配置入参'
                        : '读取目标 API 契约失败，请手动配置入参',
                );
            } finally {
                setSyncingContract(false);
            }
        },
        [node, syncToNodeData, variables, targetType],
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

        const successY = isCompactMode
            ? compactExitPortY(s.height - HTTP_COMPACT_FOOTER_HEIGHT, 0)
            : API_LAYOUT.successPortY(s.height);
        const failY = isCompactMode
            ? compactExitPortY(s.height - HTTP_COMPACT_FOOTER_HEIGHT, 1)
            : API_LAYOUT.failPortY(s.height);
        const outX = s.width;

        const setOut = (id: string, group: string, y: number) => {
            if (!node.hasPort(id)) {
                node.addPort({ id, group, args: { x: outX, y, dx: 0 }, zIndex: 1 });
            } else {
                node.setPortProp(id, 'group', group);
                node.setPortProp(id, 'args', { x: outX, y, dx: 0 });
            }
        };

        if (node.hasPort('out')) {
            node.removePort('out');
        }
        setOut('success', 'absolute-out-solid', successY);
        setOut('fail', 'absolute-out-hollow', failY);
    }, [variables, node, size]);

    const [resizing, setResizing] = React.useState(false);
    const minH =
        HEADER_HEIGHT +
        SERVICE_BLOCK_HEIGHT +
        variables.length * ROW_HEIGHT +
        VAR_PADDING +
        API_MULTI_FOOTER;

    const compactHeight = HEADER_HEIGHT + HTTP_COMPACT_FOOTER_HEIGHT;
    const { isCompact } = useCompactNodeResize(node, {
        cardMinHeight: minH,
        compactHeight,
        minWidth: MIN_WIDTH,
        cardDefaultWidth: MIN_WIDTH,
        compactWidth: COMPACT_NODE_WIDTH,
        resizing,
    });

    React.useEffect(() => {
        if (!resizing && !isCompact) {
            const s = node.getSize();
            const w = Math.max(s.width, MIN_WIDTH);
            // 随变量行增减贴合高度（避免连线误加占位后只涨不缩留下空白）
            if (Math.abs(s.height - minH) > 1) {
                node.resize(w, minH);
            }
        }
    }, [minH, node, resizing, isCompact]);

    const handleResize = React.useCallback(
        (nw: number, nh: number) => {
            const isCompactMode = getGraphNodeViewMode(node) === 'compact';
            const successY = isCompactMode
                ? compactExitPortY(nh - HTTP_COMPACT_FOOTER_HEIGHT, 0)
                : API_LAYOUT.successPortY(nh);
            const failY = isCompactMode
                ? compactExitPortY(nh - HTTP_COMPACT_FOOTER_HEIGHT, 1)
                : API_LAYOUT.failPortY(nh);
            node.setPortProp('success', 'args', { x: nw, y: successY, dx: 0 });
            node.setPortProp('fail', 'args', { x: nw, y: failY, dx: 0 });
            updateEdges('success');
            updateEdges('fail');
        },
        [node, updateEdges],
    );

    React.useEffect(() => {
        if (resizing) return;
        const s = node.getSize();
        const isCompactMode = getGraphNodeViewMode(node) === 'compact';
        const successY = isCompactMode
            ? compactExitPortY(s.height - HTTP_COMPACT_FOOTER_HEIGHT, 0)
            : API_LAYOUT.successPortY(s.height);
        const failY = isCompactMode
            ? compactExitPortY(s.height - HTTP_COMPACT_FOOTER_HEIGHT, 1)
            : API_LAYOUT.failPortY(s.height);
        try {
            node.setPortProp('success', 'args', { x: s.width, y: successY, dx: 0 });
            node.setPortProp('fail', 'args', { x: s.width, y: failY, dx: 0 });
            updateEdges('success');
            updateEdges('fail');
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

    const handleTargetTypeChange = React.useCallback(
        (next: 'api' | 'service') => {
            const prev = node.getData() as ApiNodeData;
            node.setData({
                ...prev,
                targetType: next,
                serviceId: '',
                __serviceName: '',
                __serviceMethod: '',
                __serviceUrl: '',
            });
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
                targetType,
                serviceId: val || '',
                __serviceName: src?.name || (typeof src?.label === 'string' ? src.label : '') || '',
                __serviceMethod: targetType === 'api' ? (src?.method || '') : '',
                __serviceUrl: targetType === 'api' ? (src?.url || '') : '',
            });
            if (val) {
                void syncParamsFromContract(val, prev.__variables || variables);
            }
        },
        [node, apiOptions, syncParamsFromContract, variables, targetType],
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
                <div style={{ display: 'flex', alignItems: 'center', gap: 6, height: SERVICE_ROW_HEIGHT - 6 }}>
                    <Select
                        size="small"
                        value={targetType}
                        options={[
                            { value: 'api', label: 'API' },
                            { value: 'service', label: '服务' },
                        ]}
                        onChange={handleTargetTypeChange}
                        onMouseDown={(e) => e.stopPropagation()}
                        onClick={(e) => e.stopPropagation()}
                        style={{ width: 72, fontSize: 11, flexShrink: 0 }}
                        getPopupContainer={() => document.body}
                        dropdownStyle={{ zIndex: 10000 }}
                    />
                    <Select
                        size="small"
                        showSearch
                        allowClear
                        loading={loadingApis || syncingContract}
                        value={serviceId || undefined}
                        placeholder={targetType === 'service' ? '选择内部服务...' : '选择内部 Flow API...'}
                        options={selectOptions}
                        filterOption={false}
                        onSearch={(kw) => loadTargets(kw)}
                        onChange={handleServiceChange}
                        onMouseDown={(e) => e.stopPropagation()}
                        onClick={(e) => e.stopPropagation()}
                        style={{ flex: 1, fontSize: 11 }}
                        getPopupContainer={() => document.body}
                        dropdownStyle={{ zIndex: 10000 }}
                    />
                    <Tooltip
                        title={
                            targetType === 'service'
                                ? '从服务契约重新同步入参（保留已填路径）'
                                : '从目标 API 契约重新同步入参（保留已填路径）'
                        }
                    >
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
                        paddingLeft: 2,
                    }}
                    title={
                        targetType === 'service'
                            ? '内部服务：入参写入 $.service.input'
                            : serviceId
                              ? `${serviceMethod || ''} ${serviceUrl || ''}`.trim() || 'Method/URL 随目标 API 配置，此处不可改'
                              : '无需选 Method：沿用目标 API 已配置的 Method/URL'
                    }
                >
                    {targetType === 'service'
                        ? (serviceId ? `服务 · ${serviceName || serviceId}` : '内部服务：按契约入参 → $.service.input')
                        : (serviceId
                            ? `${serviceMethod || '—'} ${serviceUrl || ''}`.trim() || 'Method/URL 来自目标 API'
                            : 'Method/URL 随目标 API，无需在此选择')}
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
                addLabel={targetType === 'service' ? '传参给目标服务' : '传参给目标 API'}
                namePlaceholder={targetType === 'service' ? '参数名 → $.service.input' : '参数名 → @FP'}
                pathPlaceholder="来源 $.上游.out"
                hideAdd={
                    targetType === 'service'
                    && variables.some((v) => v.fromContract && !!v.name?.trim())
                }
                hideRemove={
                    targetType === 'service'
                    && variables.some((v) => v.fromContract && !!v.name?.trim())
                }
            />
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
                        height: API_MULTI_FOOTER,
                        display: 'flex',
                        flexDirection: 'column',
                        justifyContent: 'center',
                        alignItems: 'flex-end',
                        paddingRight: NODE_FOOTER_SAFE_RIGHT,
                        fontSize: 10,
                        borderTop: `1px solid ${themeObj.headerBorder}`,
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
