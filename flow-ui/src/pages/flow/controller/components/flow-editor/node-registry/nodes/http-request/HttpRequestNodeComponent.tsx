// ============================================================================
// HttpRequestNodeComponent.tsx
// 通用中间节点模板（对齐 Evaluate / Database）：
//   NodeWrapper 纵向卡片 + NodeHeader + 内容区 + Footer 出口
//   无左侧色带（色带仅用于 request / schedule 等流程起点）
// ============================================================================

import React, { useEffect, useState } from 'react';
import { Typography, Select, Input, Button, Space, Dropdown } from 'antd';
import { Node } from '@antv/x6';
import { PlusOutlined, DeleteOutlined } from '@ant-design/icons';
import {
    NodeHeader,
    NodeWrapper,
    useNodeSelection,
    getNodeTheme,
    ResizeHandle,
} from '../../shared/useNodeSelection';
import {
    PAYLOAD_PORT_ID,
    PAYLOAD_PORT_Y,
    ensurePayloadPort,
    usePayloadEntryConnection,
    hasPayloadInput,
    PayloadEntryChrome,
} from '../../shared/usePayloadEntryPort';
import { commitFlowNodeIdChange } from '../../shared/nodeIdUtils';
import { NODE_HEADER_WITH_ID_HEIGHT } from '../../shared/useNodeSelection';
import { createId } from '../../../utils/id';
import {
    COMPACT_NODE_WIDTH,
    CompactExitLabels,
    compactExitPortY,
    getGraphNodeViewMode,
    HTTP_COMPACT_FOOTER_HEIGHT,
    useCompactNodeResize,
} from '../../shared/NodeViewMode';

const { Text } = Typography;
const { TextArea } = Input;
const { Option } = Select;

const ICONS = {
    globe: (
        <svg viewBox="0 0 1024 1024" width="14" height="14" fill="currentColor">
            <path d="M512 64C264.6 64 64 264.6 64 512s200.6 448 448 448 448-200.6 448-448S759.4 64 512 64zm0 820c-207.3 0-376-168.7-376-376S304.7 132 512 132s376 168.7 376 376-168.7 376-376 376zm-32.8-109.6c-48-52.8-85.6-120.8-107.2-200.8h280c-21.6 80-59.2 148-107.2 200.8zM245.2 696c21.2 59.2 54 110.8 95.2 150-52-41.6-92-93.2-117.2-150h22zm37.2-368h129.2c4.4-36.4 12.4-71.2 23.2-104-56.8 20-106.8 56.4-144 104zm-59.2 128H344c2.8 34.8 8.8 68.4 17.2 100.8H232c-3.6-32.8-5.6-66.4-5.6-100.8s2-68 5.6-100.8h129.2c-8.4 32.4-14.4 66-17.2 100.8H223.2zm34.8 156h117.2c-25.2-56.8-65.2-108.4-117.2-150-41.2 39.2-74 90.8-95.2 150zm444-462c-37.2-47.6-87.2-84-144-104 10.8 32.8 18.8 67.6 23.2 104h120.8zm-91.6 306c-8.4 32.4-14.4 66-17.2 100.8H680c3.6-32.8 5.6-66.4 5.6-100.8s-2-68-5.6-100.8H568.4c-2.8 34.8-8.8 68.4-17.2 100.8h127.2zm130-46c21.2-59.2 54-110.8 95.2-150-52 41.6-92 93.2-117.2 150h22z" />
        </svg>
    ),
    chevron: (
        <svg viewBox="0 0 1024 1024" width="12" height="12" fill="currentColor">
            <path d="M831.872 340.864 512 652.672 192.128 340.864a30.592 30.592 0 0 0-42.752 0 29.12 29.12 0 0 0 0 41.6L489.664 714.24a32 32 0 0 0 44.672 0l340.288-331.712a29.12 29.12 0 0 0 0-41.728 30.592 30.592 0 0 0-42.752 0z" />
        </svg>
    ),
};

const HEADER_HEIGHT = NODE_HEADER_WITH_ID_HEIGHT;
const SECTION_HEADER_HEIGHT = 26;
const ROW_HEIGHT = 32;
const GAP = 8;
const MIN_WIDTH = 420;
const FOOTER_HEIGHT = 82;
const PADDING_TOP = 10;
const PADDING_BOTTOM = 8;
/** JSON Body 编辑区最小高度 */
const JSON_BODY_MIN_HEIGHT = 64;
const FORM_ADD_BTN_HEIGHT = 28;

export const HTTP_REQUEST_LAYOUT = {
    width: MIN_WIDTH,
    headerHeight: HEADER_HEIGHT,
    footerHeight: FOOTER_HEIGHT,
    /** 总入口 in:payload（Header 左侧中线） */
    payloadPortY: PAYLOAD_PORT_Y,
};

/** 按当前 Headers/Params/Body 配置计算节点最小高度 */
function calcHttpRequestMinHeight(opts: {
    headerCount: number;
    paramCount: number;
    formCount: number;
    showBody: boolean;
    bodyType: string;
}): number {
    let y = HEADER_HEIGHT + PADDING_TOP;
    y += ROW_HEIGHT + GAP; // URL
    y += SECTION_HEADER_HEIGHT + opts.headerCount * (ROW_HEIGHT + 4) + GAP;
    y += SECTION_HEADER_HEIGHT + opts.paramCount * (ROW_HEIGHT + 4) + GAP;
    if (opts.showBody) {
        y += SECTION_HEADER_HEIGHT;
        if (opts.bodyType === 'json') y += JSON_BODY_MIN_HEIGHT;
        else if (opts.bodyType === 'form-data') {
            y += opts.formCount * (ROW_HEIGHT + 4) + FORM_ADD_BTN_HEIGHT + 4;
        } else {
            y += 4; // none：仅下拉
        }
        y += GAP;
    }
    y += PADDING_BOTTOM + FOOTER_HEIGHT;
    return y;
}

const METHOD_OPTIONS = [
    { key: 'GET', label: 'GET' },
    { key: 'POST', label: 'POST' },
    { key: 'PUT', label: 'PUT' },
    { key: 'DELETE', label: 'DELETE' },
    { key: 'PATCH', label: 'PATCH' },
];

interface KVItem { id: string; key: string; value: string; }

type KvListKey = '__headers' | '__params' | 'formData';

const listToMap = (list: KVItem[]): Record<string, string> => {
    const map: Record<string, string> = {};
    list.forEach((item) => {
        if (item.key) map[item.key] = item.value ?? '';
    });
    return map;
};

const mapToList = (map: Record<string, any>, idPrefix: string): KVItem[] =>
    Object.entries(map).map(([k, v]) => ({
        id: createId(idPrefix),
        key: k,
        value: v == null ? '' : String(v),
    }));

export const HttpRequestNodeComponent = ({ node }: { node: Node }) => {
    const [data, setData] = useState<any>(node.getData());
    const themeObj = getNodeTheme(data?.themeColor || 'orange');
    const { outlineCss, borderColor, selected } = useNodeSelection(node, {
        defaultColor: themeObj.primary,
        selectedColor: themeObj.primary,
    });

    const [size, setSize] = useState(node.getSize());
    const [resizing, setResizing] = useState(false);

    // UI 列表用 __headers / __params；DSL 导出用 Map（与后端 HttpRequestStep 对齐）
    useEffect(() => {
        const d = node.getData();
        const updates: any = {};
        let changed = false;

        if (!d.__headers) {
            if (Array.isArray(d.headers)) {
                updates.__headers = d.headers;
                updates.headers = listToMap(d.headers);
            } else if (d.headers && typeof d.headers === 'object') {
                updates.__headers = mapToList(d.headers, 'h');
            } else {
                updates.__headers = [];
                updates.headers = {};
            }
            changed = true;
        } else if (Array.isArray(d.headers)) {
            // 历史脏数据：headers 仍是数组 → 纠正为 Map
            updates.headers = listToMap(d.__headers);
            changed = true;
        }

        if (!d.__params) {
            if (Array.isArray(d.params)) {
                updates.__params = d.params;
                updates.params = listToMap(d.params);
            } else if (d.params && typeof d.params === 'object') {
                updates.__params = mapToList(d.params, 'p');
            } else {
                updates.__params = [];
                updates.params = {};
            }
            changed = true;
        } else if (Array.isArray(d.params)) {
            updates.params = listToMap(d.__params);
            changed = true;
        }

        if (!d.formData || !Array.isArray(d.formData)) {
            updates.formData = [];
            changed = true;
        }

        // 历史节点无 logEnabled：与后端一致，默认关闭（不自动补 true）
        if (d.logEnabled === undefined) {
            updates.logEnabled = false;
            changed = true;
        }

        // 历史节点无 ignoreSsl 时默认开启（自签名内网 HTTPS）
        if (d.ignoreSsl === undefined) {
            updates.ignoreSsl = true;
            changed = true;
        }

        if (d.timeout === undefined || d.timeout === null) {
            updates.timeout = 30000;
            changed = true;
        }
        if (d.retryCount === undefined) {
            updates.retryCount = 0;
            changed = true;
        }
        if (d.retryIntervalMs === undefined) {
            updates.retryIntervalMs = 1000;
            changed = true;
        }

        if (changed) node.setData({ ...d, ...updates }, { overwrite: true });
    }, [node]);

    useEffect(() => {
        const onData = () => setData({ ...node.getData() });
        const onSize = () => setSize({ ...node.getSize() });
        node.on('change:data', onData);
        node.on('change:size', onSize);
        return () => { node.off('change:data', onData); node.off('change:size', onSize); };
    }, [node]);

    const updateData = (key: string, val: any) => node.setData({ ...node.getData(), [key]: val });

    const syncKvList = (listKey: KvListKey, newList: KVItem[]) => {
        const current = node.getData();
        if (listKey === 'formData') {
            node.setData({ ...current, formData: newList }, { overwrite: true });
            return;
        }
        const mapKey = listKey === '__headers' ? 'headers' : 'params';
        node.setData(
            { ...current, [listKey]: newList, [mapKey]: listToMap(newList) },
            { overwrite: true },
        );
    };

    const addToList = (listKey: KvListKey) => {
        const current = node.getData();
        const list = (current[listKey] || []) as KVItem[];
        syncKvList(listKey, [...list, { id: createId('i'), key: '', value: '' }]);
    };

    const removeFromList = (listKey: KvListKey, idx: number) => {
        const current = node.getData();
        const list = [...((current[listKey] || []) as KVItem[])];
        const prefix = listKey === 'formData' ? 'form' : (listKey === '__params' ? 'param' : 'header');
        if (list[idx]) {
            const pid = `in:${prefix}:${list[idx].id}`;
            if (node.hasPort(pid)) node.removePort(pid);
        }
        list.splice(idx, 1);
        syncKvList(listKey, list);
    };

    const updateListItem = (listKey: KvListKey, idx: number, field: keyof KVItem, val: string) => {
        const current = node.getData();
        const list = [...((current[listKey] || []) as KVItem[])];
        list[idx] = { ...list[idx], [field]: val };
        syncKvList(listKey, list);
    };

    const params = (data.__params || []) as KVItem[];
    const headers = (data.__headers || []) as KVItem[];
    const formData = (data.formData || []) as KVItem[];
    const bodyType = data.bodyType || 'json';
    const method = data.method || 'GET';
    const showBody = ['POST', 'PUT', 'PATCH', 'DELETE'].includes(method);

    const nodeLabel = data?.__label || 'HTTP Request';
    const handleTitleChange = React.useCallback((newTitle: string) => {
        node.setData({ ...node.getData(), __label: newTitle });
    }, [node]);

    const methodMenu = {
        items: METHOD_OPTIONS,
        onClick: ({ key }: any) => updateData('method', key),
    };

    usePayloadEntryConnection(node);
    const hasPayload = hasPayloadInput(data);

    const minTotalHeight = calcHttpRequestMinHeight({
        headerCount: headers.length,
        paramCount: params.length,
        formCount: formData.length,
        showBody,
        bodyType,
    });

    const compactHeight = HEADER_HEIGHT + HTTP_COMPACT_FOOTER_HEIGHT;
    const { isCompact } = useCompactNodeResize(node, {
        cardMinHeight: minTotalHeight,
        compactHeight,
        minWidth: MIN_WIDTH,
        cardDefaultWidth: MIN_WIDTH,
        compactWidth: COMPACT_NODE_WIDTH,
        resizing,
    });

    // 端口与高度
    useEffect(() => {
        if (node.hasPort('out')) node.removePort('out');

        const isCompactMode = getGraphNodeViewMode(node) === 'compact';

        // 历史 URL 行控制流 in → 迁到总入口 in:payload 后移除
        let migratedIn = false;
        try {
            const graph = node.model?.graph;
            if (graph) {
                graph.getConnectedEdges(node).forEach((edge: any) => {
                    if (edge.getTargetCellId?.() !== node.id) return;
                    if (String(edge.getTargetPortId?.()) !== 'in') return;
                    edge.setTarget({ cell: node.id, port: PAYLOAD_PORT_ID });
                    migratedIn = true;
                });
            }
        } catch {
            /* ignore */
        }
        if (migratedIn) {
            const prev = node.getData() as any;
            if (!prev?.inputs?.payload) {
                node.setData(
                    {
                        ...prev,
                        inputs: {
                            ...(prev?.inputs || {}),
                            payload: { extractPath: '$' },
                        },
                    },
                    { overwrite: true },
                );
            }
        }
        if (node.hasPort('in')) node.removePort('in');
        // 清掉 adapter/历史 DSL 留下的 in:var / manual 幽灵桩（无坐标时叠在左上角）
        node.getPorts().forEach((p) => {
            const id = p.id || '';
            if (id.startsWith('in:var:') || p.group === 'manual') {
                try {
                    node.removePort(id);
                } catch {
                    /* ignore */
                }
            }
        });
        ensurePayloadPort(node, PAYLOAD_PORT_Y);

        /** 清掉历史脏数据里的 X6 端口文字（absolute 组本身无 text markup） */
        const clearPortLabel = (id: string) => {
            try {
                const p = node.getPort(id) as any;
                if (p?.attrs?.text?.text) {
                    node.setPortProp(id, 'attrs/text/text', '');
                }
            } catch {
                /* ignore */
            }
        };

        const setAbsoluteInPort = (id: string, y: number) => {
            const existing = node.getPort(id);
            if (existing && existing.group !== 'absolute-in-solid') {
                try {
                    node.setPortProp(id, 'group', 'absolute-in-solid');
                    node.setPortProp(id, 'args', { x: 0, y, dx: 0 });
                    clearPortLabel(id);
                    return;
                } catch {
                    node.removePort(id);
                }
            }
            if (!node.hasPort(id)) {
                node.addPort({
                    id,
                    group: 'absolute-in-solid',
                    args: { x: 0, y, dx: 0 },
                    zIndex: 10,
                });
            } else {
                node.setPortProp(id, 'args', { x: 0, y, dx: 0 });
                clearPortLabel(id);
            }
        };

        const setAbsoluteOutPort = (id: string, group: string, y: number, width: number) => {
            if (!node.hasPort(id)) {
                node.addPort({
                    id,
                    group,
                    args: { x: width, y, dx: 0 },
                    zIndex: 10,
                });
            } else {
                const p = node.getPort(id);
                if (p?.group !== group) node.setPortProp(id, 'group', group);
                node.setPortProp(id, 'args', { x: width, y, dx: 0 });
                clearPortLabel(id);
            }
        };

        const currentSize = node.getSize();

        if (isCompactMode) {
            const parkY = HEADER_HEIGHT / 2;
            headers.forEach((item) => {
                setAbsoluteInPort(`in:header:${item.id}`, parkY);
            });
            params.forEach((item) => {
                setAbsoluteInPort(`in:param:${item.id}`, parkY);
            });

            const jsonPortId = 'in:body:json';
            if (showBody && bodyType === 'json') setAbsoluteInPort(jsonPortId, parkY);
            else if (node.hasPort(jsonPortId)) node.removePort(jsonPortId);

            if (showBody && bodyType === 'form-data') {
                formData.forEach((item) => {
                    setAbsoluteInPort(`in:form:${item.id}`, parkY);
                });
            } else {
                node.getPorts().forEach((p) => {
                    if (p.id?.startsWith('in:form:')) node.removePort(p.id);
                });
            }

            const ft = currentSize.height - HTTP_COMPACT_FOOTER_HEIGHT;
            setAbsoluteOutPort('success', 'absolute-out-solid', compactExitPortY(ft, 0), currentSize.width);
            setAbsoluteOutPort('fail', 'absolute-out-hollow', compactExitPortY(ft, 1), currentSize.width);
            return;
        }

        let currentY = HEADER_HEIGHT + PADDING_TOP;
        currentY += ROW_HEIGHT + GAP;

        const headersListStartY = currentY + SECTION_HEADER_HEIGHT;
        currentY += SECTION_HEADER_HEIGHT + headers.length * (ROW_HEIGHT + 4) + GAP;

        const paramsListStartY = currentY + SECTION_HEADER_HEIGHT;
        currentY += SECTION_HEADER_HEIGHT + params.length * (ROW_HEIGHT + 4) + GAP;

        let bodyStartY = currentY;
        if (showBody) {
            bodyStartY += SECTION_HEADER_HEIGHT;
        }

        // 切换 Body 类型 / 增删行时：保证高度够用；非 JSON 时收回多余空白，避免与手动 resize 打架
        if (!resizing) {
            const s = node.getSize();
            const fitH = calcHttpRequestMinHeight({
                headerCount: headers.length,
                paramCount: params.length,
                formCount: formData.length,
                showBody,
                bodyType,
            });
            const nextH = bodyType === 'json' && showBody
                ? Math.max(s.height, fitH) // JSON：只升高，保留用户拉高
                : fitH; // None / Form：贴合内容
            if (Math.abs(s.height - nextH) > 2) {
                node.resize(Math.max(s.width, MIN_WIDTH), nextH);
            }
        }

        headers.forEach((item, idx) => {
            setAbsoluteInPort(`in:header:${item.id}`, headersListStartY + idx * (ROW_HEIGHT + 4) + ROW_HEIGHT / 2);
        });
        params.forEach((item, idx) => {
            setAbsoluteInPort(`in:param:${item.id}`, paramsListStartY + idx * (ROW_HEIGHT + 4) + ROW_HEIGHT / 2);
        });

        const jsonPortId = 'in:body:json';
        if (showBody && bodyType === 'json') setAbsoluteInPort(jsonPortId, bodyStartY + 16);
        else if (node.hasPort(jsonPortId)) node.removePort(jsonPortId);

        if (showBody && bodyType === 'form-data') {
            formData.forEach((item, idx) => {
                setAbsoluteInPort(`in:form:${item.id}`, bodyStartY + idx * (ROW_HEIGHT + 4) + ROW_HEIGHT / 2);
            });
        } else {
            node.getPorts().forEach((p) => {
                if (p.id?.startsWith('in:form:')) node.removePort(p.id);
            });
        }

        const footerY = currentSize.height - FOOTER_HEIGHT;
        const successY = footerY + 28;
        const failY = footerY + 56;

        setAbsoluteOutPort('success', 'absolute-out-solid', successY, currentSize.width);
        setAbsoluteOutPort('fail', 'absolute-out-hollow', failY, currentSize.width);
    }, [data, resizing, size.width, size.height, node, isCompact]);

    const renderKVRow = (listKey: KvListKey, item: KVItem, idx: number) => (
        <div key={item.id} style={{ height: ROW_HEIGHT, display: 'flex', alignItems: 'center', marginBottom: 4, position: 'relative' }}>
            <div style={{
                position: 'absolute', left: -6, top: '50%', marginTop: -3,
                width: 6, height: 6, borderRadius: '50%', background: themeObj.primary,
            }} />
            <Space.Compact style={{ width: '100%', marginLeft: 8 }} size="small">
                <Input
                    placeholder="Key"
                    value={item.key}
                    onChange={(e) => updateListItem(listKey, idx, 'key', e.target.value)}
                    style={{ width: '40%' }}
                    onMouseDown={(e) => e.stopPropagation()}
                />
                <Input
                    placeholder="Value"
                    value={item.value}
                    onChange={(e) => updateListItem(listKey, idx, 'value', e.target.value)}
                    style={{ width: '60%' }}
                    onMouseDown={(e) => e.stopPropagation()}
                />
            </Space.Compact>
            <DeleteOutlined
                onClick={(e) => { e.preventDefault(); e.stopPropagation(); removeFromList(listKey, idx); }}
                style={{ marginLeft: 8, color: '#999', cursor: 'pointer', pointerEvents: 'auto' }}
            />
        </div>
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
                    icon={ICONS.globe}
                    title={nodeLabel}
                    theme={themeObj}
                    height={HEADER_HEIGHT}
                    node={node}
                    nodeId={node.id}
                    onNodeIdChange={(id) => commitFlowNodeIdChange(node, id)}
                    onTitleChange={handleTitleChange}
                    extra={
                        <Space size={8}>
                            {data.authType && data.authType !== 'none' ? (
                                <Text
                                    style={{ fontSize: 10, color: '#8c8c8c' }}
                                    title={`鉴权: ${data.authType}（右侧属性面板可改）`}
                                >
                                    {String(data.authType).toUpperCase()}
                                </Text>
                            ) : null}
                            <Dropdown menu={methodMenu} trigger={['click']}>
                                <div
                                    onClick={(e) => e.stopPropagation()}
                                    onMouseDown={(e) => e.stopPropagation()}
                                    style={{ cursor: 'pointer', display: 'flex', alignItems: 'center', gap: 4 }}
                                >
                                    <Text style={{ fontSize: 11, color: themeObj.primary }}>{method}</Text>
                                    <div style={{ color: themeObj.primary, display: 'flex' }}>{ICONS.chevron}</div>
                                </div>
                            </Dropdown>
                        </Space>
                    }
                />
            </PayloadEntryChrome>

            {!isCompact && (
            <div
                style={{
                    padding: `${PADDING_TOP}px 12px ${PADDING_BOTTOM}px`,
                    display: 'flex',
                    flexDirection: 'column',
                    pointerEvents: 'auto',
                    flex: 1,
                    minHeight: 0,
                }}
            >
                {/* URL 行（入口统一用 Header 总入口 in:payload） */}
                <div
                    style={{
                        display: 'flex',
                        height: ROW_HEIGHT,
                        marginBottom: GAP,
                        flexShrink: 0,
                        alignItems: 'center',
                    }}
                >
                    <Input
                        value={data.url}
                        onChange={(e) => updateData('url', e.target.value)}
                        placeholder="https://api.example.com"
                        size="small"
                        style={{ flex: 1 }}
                        onMouseDown={(e) => e.stopPropagation()}
                    />
                </div>

                <div style={{ flex: 1, minHeight: 0, overflow: 'auto', display: 'flex', flexDirection: 'column' }}>
                    <div style={{ marginBottom: GAP, flexShrink: 0 }}>
                        <div style={{ height: SECTION_HEADER_HEIGHT, display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
                            <Text style={{ fontSize: 12, color: '#8c8c8c' }}>Headers</Text>
                            <Button type="text" size="small" icon={<PlusOutlined />} onClick={() => addToList('__headers')} />
                        </div>
                        {headers.map((h, idx) => renderKVRow('__headers', h, idx))}
                    </div>

                    <div style={{ marginBottom: GAP, flexShrink: 0 }}>
                        <div style={{ height: SECTION_HEADER_HEIGHT, display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
                            <Text style={{ fontSize: 12, color: '#8c8c8c' }}>Params</Text>
                            <Button type="text" size="small" icon={<PlusOutlined />} onClick={() => addToList('__params')} />
                        </div>
                        {params.map((p, idx) => renderKVRow('__params', p, idx))}
                    </div>

                    {showBody && (
                        <div
                            style={{
                                marginBottom: GAP,
                                display: 'flex',
                                flexDirection: 'column',
                                flex: bodyType === 'json' ? 1 : 0,
                                flexShrink: bodyType === 'json' ? 1 : 0,
                                minHeight: bodyType === 'json' ? JSON_BODY_MIN_HEIGHT + SECTION_HEADER_HEIGHT : undefined,
                            }}
                        >
                            <div style={{ height: SECTION_HEADER_HEIGHT, display: 'flex', alignItems: 'center', justifyContent: 'space-between', flexShrink: 0 }}>
                                <Text style={{ fontSize: 12, color: '#8c8c8c' }}>Body</Text>
                                <Select
                                    size="small"
                                    value={bodyType}
                                    onChange={(v) => updateData('bodyType', v)}
                                    style={{ width: 96 }}
                                    onMouseDown={(e) => e.stopPropagation()}
                                    getPopupContainer={() => document.body}
                                >
                                    <Option value="none">None</Option>
                                    <Option value="json">JSON</Option>
                                    <Option value="form-data">Form</Option>
                                </Select>
                            </div>
                            {bodyType === 'json' && (
                                <div style={{ position: 'relative', flex: 1, minHeight: JSON_BODY_MIN_HEIGHT }}>
                                    <div style={{
                                        position: 'absolute', left: -6, top: 14,
                                        width: 6, height: 6, borderRadius: '50%', background: themeObj.primary, zIndex: 1,
                                    }} />
                                    <TextArea
                                        value={typeof data.body === 'object' ? JSON.stringify(data.body, null, 2) : data.body}
                                        onChange={(e) => updateData('body', e.target.value)}
                                        style={{
                                            height: '100%', minHeight: JSON_BODY_MIN_HEIGHT, resize: 'none', fontSize: 12,
                                            fontFamily: 'ui-monospace, SFMono-Regular, Menlo, monospace',
                                            marginLeft: 8, width: 'calc(100% - 8px)',
                                            background: '#f5f5f5', borderRadius: 4,
                                        }}
                                        placeholder="{ ... }"
                                        onMouseDown={(e) => e.stopPropagation()}
                                    />
                                </div>
                            )}
                            {bodyType === 'form-data' && (
                                <div style={{ flexShrink: 0 }}>
                                    {formData.map((f, idx) => renderKVRow('formData', f, idx))}
                                    <Button type="dashed" size="small" block icon={<PlusOutlined />} onClick={() => addToList('formData')} style={{ marginTop: 4 }}>
                                        Add Field
                                    </Button>
                                </div>
                            )}
                        </div>
                    )}
                </div>
            </div>
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
            <>
            {/* Footer — 成功条件；策略配置在右侧属性面板 */}
            <div
                style={{
                    height: FOOTER_HEIGHT,
                    borderTop: '1px solid #f0f0f0',
                    position: 'relative',
                    pointerEvents: 'auto',
                    flexShrink: 0,
                    padding: '8px 12px 8px',
                    display: 'flex',
                    flexDirection: 'column',
                    boxSizing: 'border-box',
                }}
            >
                <Text style={{ fontSize: 11, color: '#8c8c8c', flexShrink: 0, marginBottom: 4 }}>
                    成功条件
                </Text>
                <TextArea
                    placeholder="status == 200  或  status == 200 && body.code == 0"
                    value={data.successCondition}
                    onChange={(e) => updateData('successCondition', e.target.value)}
                    autoSize={false}
                    rows={2}
                    style={{
                        fontSize: 12,
                        width: 'calc(100% - 64px)',
                        resize: 'none',
                        lineHeight: '18px',
                        padding: '4px 8px',
                        fontFamily: 'ui-monospace, SFMono-Regular, Menlo, monospace',
                    }}
                    onMouseDown={(e) => e.stopPropagation()}
                />
                <div style={{ position: 'absolute', right: 22, top: 22, fontSize: 11, color: '#52c41a' }}>success</div>
                <div style={{ position: 'absolute', right: 22, top: 50, fontSize: 11, color: '#ff4d4f' }}>fail</div>
            </div>
            </>
            )}

            {!isCompact && (
            <ResizeHandle
                node={node}
                minWidth={MIN_WIDTH}
                minHeight={minTotalHeight}
                color={themeObj.primary}
                onResizeStart={() => setResizing(true)}
                onResizeEnd={() => setResizing(false)}
            />
            )}
        </NodeWrapper>
    );
};

export default HttpRequestNodeComponent;
