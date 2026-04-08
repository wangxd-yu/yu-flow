import React, { useEffect, useState, useCallback, useMemo } from 'react';
import { Input, Typography, Space, InputNumber, Button } from 'antd';
import { PlusOutlined, DeleteOutlined } from '@ant-design/icons';
import { Node } from '@antv/x6';
import CodeEditor from '../../../components/CodeEditor';
import {
    useNodeSelection, NodeHeader, NodeWrapper, ResizeHandle, getNodeTheme,
} from '../../shared/useNodeSelection';
import { useNodeVariables, NodeVariable } from '../../shared/useNodeVariables';
import { DynamicVariableList } from '../../shared/DynamicVariableList';
import { createId } from '../../../utils/id';

const { Text } = Typography;

// ── 主题色：橙色终止色 ──
const RESPONSE_COLOR = '#FF6B35';

// ── 图标：右箭头加竖线 →| ──
const ICONS = {
    response: (
        <svg viewBox="0 0 1024 1024" width="14" height="14" fill="currentColor">
            <path d="M256 128v768h72V128h-72zm192 324.7L628.7 272l50.9 50.9L586.3 416H832v72H586.3l93.3 93.1-50.9 50.9L448 452.7z" />
        </svg>
    ),
};

// ── 布局常量 ──
const HEADER_HEIGHT = 40;
const STATUS_ROW_HEIGHT = 36;
const SECTION_HEADER_HEIGHT = 28;
const VAR_ROW_HEIGHT = 40;
const HEADER_ROW_HEIGHT = 32;
const VAR_PADDING = 8;
const BODY_MIN_HEIGHT = 80;
const ACCENT_WIDTH = 8;
const MIN_WIDTH = 320;
const GAP = 8;

interface KVItem { id: string; key: string; value: string; }

// ════════════════════════════════════════════════════════════════════
// 主组件
// ════════════════════════════════════════════════════════════════════

export const ResponseNodeComponent: React.FC<{ node: Node }> = ({ node }) => {
    const [data, setData] = useState<any>(node.getData());
    const themeObj = getNodeTheme(data?.themeColor || 'orange');
    const { outlineCss, borderColor, selected } = useNodeSelection(node, {
        defaultColor: themeObj.primary,
        selectedColor: themeObj.primary,
    });

    const [size, setSize] = useState(node.getSize());
    const [resizing, setResizing] = useState(false);

    // ── 数据初始化 ──
    useEffect(() => {
        const d = node.getData();
        const updates: any = {};
        let changed = false;

        if (d.headers && !Array.isArray(d.headers) && !d.__headers) {
            updates.__headers = Object.entries(d.headers).map(([k, v]) => ({ id: createId('h'), key: k, value: v }));
            changed = true;
        } else if (!d.__headers) {
            updates.__headers = [];
            changed = true;
        }

        if (changed) {
            node.setData({ ...d, ...updates }, { overwrite: true });
        }
    }, []);

    // 监听 data / size 变化
    useEffect(() => {
        const onDataChange = () => setData({ ...node.getData() });
        const onSizeChange = () => setSize({ ...node.getSize() });
        node.on('change:data', onDataChange);
        node.on('change:size', onSizeChange);
        return () => {
            node.off('change:data', onDataChange);
            node.off('change:size', onSizeChange);
        };
    }, [node]);

    const headersList = (data?.__headers || []) as KVItem[];

    const calcVarPortY = useCallback((idx: number) => {
        return HEADER_HEIGHT + STATUS_ROW_HEIGHT + SECTION_HEADER_HEIGHT + VAR_PADDING / 2 + idx * VAR_ROW_HEIGHT + VAR_ROW_HEIGHT / 2;
    }, []);

    // ── 使用共享变量 Hook (Inputs 动态变量) ──
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
    } = useNodeVariables(node, {
        varPortY: calcVarPortY,
        rowHeight: VAR_ROW_HEIGHT,
    });

    // ── Headers 操作 ──
    const syncHeaders = (newList: KVItem[]) => {
        const headersMap: Record<string, string> = {};
        newList.forEach((h: KVItem) => {
            if (h.key) {
                headersMap[h.key] = h.value;
            }
        });
        node.setData({ ...node.getData(), __headers: newList, headers: headersMap }, { overwrite: true });
    };

    const onAddHeader = () => {
        syncHeaders([...headersList, { id: createId('h'), key: '', value: '' }]);
    };

    const onUpdateHeader = (idx: number, key: keyof KVItem, value: string) => {
        const newList = [...headersList];
        newList[idx] = { ...newList[idx], [key]: value };
        syncHeaders(newList);
    };

    const onRemoveHeader = (idx: number) => {
        const newList = [...headersList];
        newList.splice(idx, 1);
        syncHeaders(newList);
    };

    // ── 内容高度计算 ──
    const headersTotalHeight = SECTION_HEADER_HEIGHT + headersList.length * HEADER_ROW_HEIGHT + GAP;
    const variablesTotalHeight = SECTION_HEADER_HEIGHT + variables.length * VAR_ROW_HEIGHT + VAR_PADDING + GAP;

    const contentH = HEADER_HEIGHT + STATUS_ROW_HEIGHT
        + headersTotalHeight
        + variablesTotalHeight
        + SECTION_HEADER_HEIGHT + BODY_MIN_HEIGHT;

    // ── 尺寸自动调整 + Variables 端口位置同步 ──
    useEffect(() => {
        if (resizing) return;

        // 1. 先调整节点大小
        const s = node.getSize();
        if (s.height < contentH) {
            node.resize(Math.max(s.width, MIN_WIDTH), contentH);
        }

        // 同步现有的变量端口位置，由于 headers 的增减可能导致 y 坐标改变
        variables.forEach((v: NodeVariable, idx: number) => {
            const vpid = `in:var:${v.id}`;
            if (node.hasPort(vpid)) {
                node.setPortProp(vpid, 'args', { x: 0, y: calcVarPortY(idx) });
                updateEdges(vpid);
            }
        });

        // 清理旧端口
        if (node.hasPort('in')) node.removePort('in');
        if (node.hasPort('in:headers')) node.removePort('in:headers');
        if (node.hasPort('in:body')) node.removePort('in:body');
    }, [contentH, variables.length, headersList.length, node, resizing, variables, calcVarPortY, updateEdges]);

    // ── Resize 回调 ──
    const handleResize = useCallback((nw: number, _nh: number) => {
        // no-op
    }, []);

    // ── 标题 ──
    const nodeLabel = data?.__label || 'Response';
    const handleTitleChange = useCallback((newTitle: string) => {
        node.setData({ ...node.getData(), __label: newTitle }, { overwrite: true });
    }, [node]);

    // ── Status Code ──
    const statusCode = data?.status ?? 200;
    const handleStatusChange = useCallback((val: number | null) => {
        if (val !== null) {
            node.setData({ ...node.getData(), status: val }, { overwrite: true });
        }
    }, [node]);

    // ── Body 表达式 ──
    const bodyExpr = typeof data?.body === 'object' ? JSON.stringify(data.body, null, 2) : (data?.body || '');
    const handleBodyChange = useCallback((value: string) => {
        node.setData({ ...node.getData(), body: value || '' }, { overwrite: true });
    }, [node]);

    return (
        <NodeWrapper
            node={node}
            selected={selected}
            themeColor={borderColor}
            outlineCss={outlineCss}
            backgroundColor="#fafafa"
            extraStyle={{ flexDirection: 'row' }}
        >
            {/* Main content area */}
            <div style={{ flex: 1, height: '100%', display: 'flex', flexDirection: 'column', overflow: 'hidden' }}>
                {/* Header */}
                <NodeHeader
                    icon={ICONS.response}
                    title={nodeLabel}
                    theme={themeObj}
                    height={HEADER_HEIGHT}
                    onTitleChange={handleTitleChange}
                />

                {/* Status Code Row */}
                <div
                    style={{
                        height: STATUS_ROW_HEIGHT,
                        padding: '0 12px',
                        display: 'flex',
                        alignItems: 'center',
                        justifyContent: 'space-between',
                        borderBottom: '1px solid #f0f0f0',
                        pointerEvents: 'auto',
                        flexShrink: 0,
                    }}
                >
                    <Text style={{ fontSize: 11, color: '#8c8c8c', flexShrink: 0 }}>Status Code</Text>
                    <InputNumber
                        size="small"
                        value={statusCode}
                        min={100}
                        max={599}
                        onChange={handleStatusChange}
                        onMouseDown={(e) => e.stopPropagation()}
                        style={{ width: 80 }}
                    />
                </div>

                {/* Variables Section — 动态变量 */}
                <div style={{ pointerEvents: 'auto', flexShrink: 0, marginBottom: GAP }}>
                    <div
                        style={{
                            height: SECTION_HEADER_HEIGHT,
                            display: 'flex',
                            alignItems: 'center',
                            justifyContent: 'space-between',
                            padding: '0 12px',
                        }}
                    >
                        <Text strong style={{ fontSize: 12 }}>Variables</Text>
                    </div>

                    {/* Variable Rows — 使用共享组件 */}
                    <DynamicVariableList
                        variables={variables}
                        rowHeight={VAR_ROW_HEIGHT}
                        dragState={dragState}
                        hoverRowIndex={hoverRowIndex}
                        onHoverChange={setHoverRowIndex}
                        onDragStart={handleDragStart}
                        onAddVar={onAddVar}
                        onUpdateVar={onUpdateVar}
                        onRemoveVar={onRemoveVar}
                    />
                </div>

                {/* Headers Section — 静态键值对 */}
                <div style={{ pointerEvents: 'auto', flexShrink: 0, marginBottom: GAP }}>
                    <div
                        style={{
                            height: SECTION_HEADER_HEIGHT,
                            display: 'flex',
                            alignItems: 'center',
                            justifyContent: 'space-between',
                            padding: '0 12px',
                        }}
                    >
                        <Text strong style={{ fontSize: 12 }}>Headers</Text>
                        <Button type="text" size="small" icon={<PlusOutlined />} onClick={onAddHeader} />
                    </div>

                    <div style={{ padding: '0 12px' }}>
                        {headersList.map((h: KVItem, idx: number) => (
                            <div key={h.id} style={{ height: HEADER_ROW_HEIGHT, display: 'flex', alignItems: 'center', marginBottom: 4, position: 'relative' }}>
                                <Space.Compact style={{ width: '100%' }} size="small">
                                    <Input
                                        placeholder="Key"
                                        value={h.key}
                                        onChange={e => onUpdateHeader(idx, 'key', e.target.value)}
                                        style={{ width: '40%', pointerEvents: 'auto' }}
                                        onMouseDown={e => e.stopPropagation()}
                                    />
                                    <Input
                                        placeholder="Value"
                                        value={h.value}
                                        onChange={e => onUpdateHeader(idx, 'value', e.target.value)}
                                        style={{ width: '60%', pointerEvents: 'auto' }}
                                        onMouseDown={e => e.stopPropagation()}
                                    />
                                </Space.Compact>
                                <DeleteOutlined
                                    onClick={(e) => {
                                        e.preventDefault();
                                        e.stopPropagation();
                                        onRemoveHeader(idx);
                                    }}
                                    style={{ marginLeft: 8, color: '#999', cursor: 'pointer', pointerEvents: 'auto' }}
                                />
                            </div>
                        ))}
                    </div>
                </div>

                {/* Body Section — 表达式输入 */}
                <div
                    style={{
                        display: 'flex',
                        flexDirection: 'column',
                        flex: 1,
                        minHeight: 0,
                        pointerEvents: 'auto',
                    }}
                >
                    <div
                        style={{
                            height: SECTION_HEADER_HEIGHT,
                            display: 'flex',
                            alignItems: 'center',
                            justifyContent: 'space-between',
                            padding: '0 12px',
                            flexShrink: 0,
                        }}
                    >
                        <Text strong style={{ fontSize: 12 }}>Body</Text>
                        <Text type="secondary" style={{ fontSize: 10 }}>返回值包装表达式</Text>
                    </div>

                    <div
                        style={{
                            flex: 1,
                            padding: '0 12px 8px',
                            minHeight: BODY_MIN_HEIGHT - SECTION_HEADER_HEIGHT,
                            display: 'flex',
                            flexDirection: 'column',
                        }}
                    >
                        <div
                            style={{ flex: 1, minHeight: 60, display: 'flex', flexDirection: 'column' }}
                            onMouseDown={(e) => e.stopPropagation()}
                            onClick={(e) => e.stopPropagation()}
                        >
                            <CodeEditor
                                value={bodyExpr}
                                onChange={handleBodyChange}
                                language="json"
                                height="100%"
                                lineNumbers={false}
                                fontSize={12}
                                style={{ border: '1px solid #e8e8e8', flex: 1, display: 'flex', flexDirection: 'column' }}
                            />
                        </div>
                    </div>
                </div>
            </div>

            {/* Right accent bar — 终止节点视觉标识 */}
            <div
                style={{
                    width: ACCENT_WIDTH,
                    height: '100%',
                    flexShrink: 0,
                    background: themeObj.primary,
                    borderRadius: '0 10px 10px 0',
                    pointerEvents: 'none',
                }}
            />

            {/* Resize Handle */}
            <ResizeHandle
                node={node}
                minWidth={MIN_WIDTH}
                minHeight={contentH}
                color={themeObj.primary}
                onResize={handleResize}
                onResizeStart={() => setResizing(true)}
                onResizeEnd={() => setResizing(false)}
            />
        </NodeWrapper>
    );
};

export default ResponseNodeComponent;
