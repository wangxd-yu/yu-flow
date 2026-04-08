import React, { useEffect, useState, useRef } from 'react';
import { Typography, Select, Input, Button, Space } from 'antd';
import { Node } from '@antv/x6';
import { PlusOutlined, DeleteOutlined } from '@ant-design/icons';
import { NodeHeader, NodeWrapper, useNodeSelection, getNodeTheme, ResizeHandle } from '../../shared/useNodeSelection';
import { createId } from '../../../utils/id';

const { Text } = Typography;
const { TextArea } = Input;
const { Option } = Select;

const ICONS = {
    globe: (<svg viewBox="0 0 1024 1024" width="14" height="14" fill="currentColor"><path d="M512 64C264.6 64 64 264.6 64 512s200.6 448 448 448 448-200.6 448-448S759.4 64 512 64z m0 824c-207.3 0-376-168.7-376-376s168.7-376 376-376 376 168.7 376 376-168.7 376-376 376z m-32.8-109.6c-48-52.8-85.6-120.8-107.2-200.8h280c-21.6 80-59.2 148-107.2 200.8zM245.2 696c21.2 59.2 54 110.8 95.2 150-52-41.6-92-93.2-117.2-150H245.2z m37.2-368h129.2c4.4-36.4 12.4-71.2 23.2-104-56.8 20-106.8 56.4-144 104z m-59.2 128H344c2.8 34.8 8.8 68.4 17.2 100.8H232c-3.6-32.8-5.6-66.4-5.6-100.8s2-68 5.6-100.8h129.2c-8.4 32.4-14.4 66-17.2 100.8H223.2z m34.8 156h117.2c-25.2-56.8-65.2-108.4-117.2-150-41.2 39.2-74 90.8-95.2 150z m444-462c-37.2-47.6-87.2-84-144-104 10.8 32.8 18.8 67.6 23.2 104h120.8z m-91.6 306c-8.4 32.4-14.4 66-17.2 100.8H680c3.6-32.8 5.6-66.4 5.6-100.8s-2-68-5.6-100.8h-111.6c-2.8 34.8-8.8 68.4-17.2 100.8h127.2z m130-46c21.2-59.2 54-110.8 95.2-150-52 41.6-92 93.2-117.2 150h22z" /></svg>)
};

const HEADER_HEIGHT = 40;
const SECTION_HEADER_HEIGHT = 28;
const ROW_HEIGHT = 32;
const GAP = 8;
const MIN_WIDTH = 420;
const FOOTER_HEIGHT = 64; // 增加到64以避免调整大小手柄重叠
const PADDING_TOP = 12;
const PADDING_BOTTOM = 0;

export const HTTP_REQUEST_LAYOUT = {
    // 基础常量，具体使用取决于动态内容
    width: MIN_WIDTH,
};

interface KVItem { id: string; key: string; value: string; }

export const HttpRequestNodeComponent = ({ node }: { node: Node }) => {
    // 1. 数据初始化
    const [data, setData] = useState<any>(node.getData());
    const themeObj = getNodeTheme(data?.themeColor);
    const { outlineCss, selected } = useNodeSelection(node, { defaultColor: themeObj.primary, selectedColor: themeObj.primary });

    // ── 标题 ──
    const nodeLabel = data?.__label || 'HTTP Request';
    const handleTitleChange = React.useCallback((newTitle: string) => {
        node.setData({ ...node.getData(), __label: newTitle });
    }, [node]);

    // 调整大小状态
    const [size, setSize] = useState(node.getSize());
    const [resizing, setResizing] = useState(false);

    // 2. 数据迁移
    useEffect(() => {
        const d = node.getData();
        const updates: any = {};
        let changed = false;

        if (d.params && !Array.isArray(d.params)) {
            updates.params = Object.entries(d.params).map(([k, v]) => ({ id: createId('p'), key: k, value: v }));
            changed = true;
        }
        if (d.headers && !Array.isArray(d.headers)) {
            updates.headers = Object.entries(d.headers).map(([k, v]) => ({ id: createId('h'), key: k, value: v }));
            changed = true;
        }
        if (!d.params && !updates.params) { updates.params = []; changed = true; }
        if (!d.headers && !updates.headers) { updates.headers = []; changed = true; }
        if (!d.formData && !Array.isArray(d.formData)) { updates.formData = []; changed = true; }

        if (changed) {
            node.setData({ ...d, ...updates });
        }
    }, [node]);

    // 3. 更新监听
    useEffect(() => {
        const onData = () => setData({ ...node.getData() });
        const onSize = () => setSize({ ...node.getSize() });
        node.on('change:data', onData);
        node.on('change:size', onSize);
        return () => { node.off('change:data', onData); node.off('change:size', onSize); };
    }, [node]);

    // 4. 更新数据辅助函数
    const updateData = (key: string, val: any) => node.setData({ ...node.getData(), [key]: val });

    const addToList = (listKey: string) => {
        const currentData = node.getData();
        const list = currentData[listKey] || [];
        node.setData({ ...currentData, [listKey]: [...list, { id: createId('i'), key: '', value: '' }] });
    };
    const removeFromList = (listKey: string, idx: number) => {
        const currentData = node.getData();
        const list = [...(currentData[listKey] || [])];
        // 首先清理端口
        const prefix = listKey === 'formData' ? 'form' : (listKey === 'params' ? 'param' : 'header');
        if (list[idx]) {
            const pid = `in:${prefix}:${list[idx].id}`;
            if (node.hasPort(pid)) node.removePort(pid);
        }
        list.splice(idx, 1);
        node.setData({ ...currentData, [listKey]: list });
    };
    const updateListItem = (listKey: string, idx: number, field: keyof KVItem, val: string) => {
        const currentData = node.getData();
        const list = [...(currentData[listKey] || [])];
        list[idx] = { ...list[idx], [field]: val };
        node.setData({ ...currentData, [listKey]: list });
    };

    // 访问器
    const params = (data.params || []) as KVItem[];
    const headers = (data.headers || []) as KVItem[];
    const formData = (data.formData || []) as KVItem[];
    const bodyType = data.bodyType || 'json';
    const method = data.method || 'GET';
    const showBody = ['POST', 'PUT', 'PATCH', 'DELETE'].includes(method);

    // 5. 布局和端口计算
    useEffect(() => {
        // 始终移除我们不需要的默认端口
        if (node.hasPort('in')) node.removePort('in');
        if (node.hasPort('out')) node.removePort('out'); // Just in case

        // 计算变量
        let currentY = HEADER_HEIGHT + PADDING_TOP;

        // 1. 方法行
        currentY += ROW_HEIGHT + GAP;

        // 2. Headers 部分（上移）
        const headersListStartY = currentY + SECTION_HEADER_HEIGHT;
        currentY += SECTION_HEADER_HEIGHT + headers.length * (ROW_HEIGHT + 4) + GAP;

        // 3. Params 部分（下移）
        const paramsListStartY = currentY + SECTION_HEADER_HEIGHT;
        currentY += SECTION_HEADER_HEIGHT + params.length * (ROW_HEIGHT + 4) + GAP;

        // 4. Body 部分
        let bodyStartY = currentY;
        let bodyContentHeight = 0;

        if (showBody) {
            bodyStartY += SECTION_HEADER_HEIGHT; // 内容从标题后开始
            if (bodyType === 'json') {
                bodyContentHeight = 100; // 最小基础高度
            } else if (bodyType === 'form-data') {
                bodyContentHeight = formData.length * (ROW_HEIGHT + 4) + SECTION_HEADER_HEIGHT; // + 添加按钮
            } else {
                bodyContentHeight = 20; // 无
            }
            currentY += SECTION_HEADER_HEIGHT + bodyContentHeight + GAP;
        }

        const minContentHeight = currentY + PADDING_BOTTOM;
        const minTotalHeight = minContentHeight + FOOTER_HEIGHT;

        // --- 调整大小逻辑 ---
        if (!resizing) {
            const s = node.getSize();
            if (showBody && bodyType === 'json') {
                // 如果是 JSON，我们强制最小高度，但允许更高
                if (s.height < minTotalHeight) {
                    node.resize(Math.max(s.width, MIN_WIDTH), minTotalHeight);
                }
            } else {
                // 对于其他类型，我们强制确切高度（自动收缩/增长）
                if (Math.abs(s.height - minTotalHeight) > 2) {
                    node.resize(Math.max(s.width, MIN_WIDTH), minTotalHeight);
                }
            }
        }

        const setPort = (id: string, y: number) => {
            if (!node.hasPort(id)) {
                node.addPort({
                    id,
                    group: 'absolute-in-solid',
                    args: { x: 0, y, dx: 0 },
                    zIndex: 10
                });
            }
            node.setPortProp(id, 'args', { x: 0, y, dx: 0 });
        };
        const removePort = (id: string) => { if (node.hasPort(id)) node.removePort(id); };

        headers.forEach((item, idx) => {
            setPort(`in:header:${item.id}`, headersListStartY + idx * (ROW_HEIGHT + 4) + ROW_HEIGHT / 2);
        });

        params.forEach((item, idx) => {
            setPort(`in:param:${item.id}`, paramsListStartY + idx * (ROW_HEIGHT + 4) + ROW_HEIGHT / 2);
        });

        const jsonPortId = 'in:body:json';
        if (showBody && bodyType === 'json') {
            setPort(jsonPortId, bodyStartY + 16);
        } else {
            removePort(jsonPortId);
        }

        if (showBody && bodyType === 'form-data') {
            formData.forEach((item, idx) => {
                setPort(`in:form:${item.id}`, bodyStartY + idx * (ROW_HEIGHT + 4) + ROW_HEIGHT / 2);
            });
        } else {
            node.getPorts().forEach(p => {
                if (p.id?.startsWith('in:form:')) node.removePort(p.id);
            });
        }

        // 输出
        const currentSize = node.getSize();
        const footerYOffset = currentSize.height - FOOTER_HEIGHT;

        // 稍微向上移动端口，以便为调整大小手柄腾出空间且不重叠
        const successY = footerYOffset + 14;
        const failY = footerYOffset + 38;

        if (!node.hasPort('success')) node.addPort({ id: 'success', group: 'absolute-out-solid', args: { x: currentSize.width, y: successY, dx: 0 } });
        if (!node.hasPort('fail')) node.addPort({ id: 'fail', group: 'absolute-out-hollow', args: { x: currentSize.width, y: failY, dx: 0 } });

        node.setPortProp('success', 'args', { x: currentSize.width, y: successY, dx: 0 });
        node.setPortProp('fail', 'args', { x: currentSize.width, y: failY, dx: 0 });

    }, [data, resizing, size.width, size.height]);

    // 7. 渲染辅助函数
    const renderKVRow = (listKey: string, item: KVItem, idx: number) => (
        <div key={item.id} style={{ height: ROW_HEIGHT, display: 'flex', alignItems: 'center', marginBottom: 4, position: 'relative' }}>
            <div style={{ position: 'absolute', left: -6, top: '50%', marginTop: -4, width: 8, height: 8, borderRadius: '50%', background: '#1677ff' }} />
            <Space.Compact style={{ width: '100%', marginLeft: 10 }} size="small">
                <Input
                    placeholder="Key"
                    value={item.key}
                    onChange={e => updateListItem(listKey, idx, 'key', e.target.value)}
                    style={{ width: '40%', pointerEvents: 'auto' }}
                    onMouseDown={e => e.stopPropagation()}
                />
                <Input
                    placeholder="Value"
                    value={item.value}
                    onChange={e => updateListItem(listKey, idx, 'value', e.target.value)}
                    style={{ width: '60%', pointerEvents: 'auto' }}
                    onMouseDown={e => e.stopPropagation()}
                />
            </Space.Compact>
            <DeleteOutlined
                onClick={(e) => {
                    e.preventDefault();
                    e.stopPropagation();
                    removeFromList(listKey, idx);
                }}
                style={{ marginLeft: 8, color: '#999', cursor: 'pointer', pointerEvents: 'auto', zIndex: 10 }}
            />
        </div>
    );

    return (
        <NodeWrapper node={node} selected={selected} themeColor={themeObj.primary} outlineCss={outlineCss} backgroundColor="#fafafa">
            <NodeHeader icon={ICONS.globe} title={nodeLabel} theme={themeObj} height={HEADER_HEIGHT} onTitleChange={handleTitleChange} />

            <div style={{ padding: `${PADDING_TOP}px 12px ${PADDING_BOTTOM}px`, display: 'flex', flexDirection: 'column', pointerEvents: 'auto', height: `calc(100% - ${HEADER_HEIGHT}px - ${FOOTER_HEIGHT}px)` }}>

                {/* 1. 方法 & URL */}
                <div style={{ display: 'flex', gap: 8, height: ROW_HEIGHT, marginBottom: GAP, flexShrink: 0, alignItems: 'center' }}>
                    <Select
                        value={data.method || 'GET'}
                        onChange={(v) => updateData('method', v)}
                        style={{ width: 100 }}
                        size="small"
                        onMouseDown={e => e.stopPropagation()}
                        getPopupContainer={trigger => trigger.parentNode}
                    >
                        {['GET', 'POST', 'PUT', 'DELETE', 'PATCH'].map(m => <Option key={m} value={m}>{m}</Option>)}
                    </Select>
                    <Input
                        value={data.url} onChange={(e) => updateData('url', e.target.value)}
                        placeholder="https://api.example.com" size="small" style={{ flex: 1 }}
                        onMouseDown={e => e.stopPropagation()}
                    />
                </div>

                <div style={{ flex: 1, overflowY: 'hidden', display: 'flex', flexDirection: 'column' }}>
                    {/* 2. Headers (已交换) */}
                    <div style={{ marginBottom: GAP, flexShrink: 0 }}>
                        <div style={{ height: SECTION_HEADER_HEIGHT, display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
                            <Text strong style={{ fontSize: 12 }}>Headers</Text>
                            <Button type="text" size="small" icon={<PlusOutlined />} onClick={() => addToList('headers')} />
                        </div>
                        {headers.map((h, idx) => renderKVRow('headers', h, idx))}
                    </div>

                    {/* 3. Params (已交换) */}
                    <div style={{ marginBottom: GAP, flexShrink: 0 }}>
                        <div style={{ height: SECTION_HEADER_HEIGHT, display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
                            <Text strong style={{ fontSize: 12 }}>Params</Text>
                            <Button type="text" size="small" icon={<PlusOutlined />} onClick={() => addToList('params')} />
                        </div>
                        {params.map((p, idx) => renderKVRow('params', p, idx))}
                    </div>

                    {/* 4. Body */}
                    {showBody && (
                        <div style={{ marginBottom: GAP, display: 'flex', flexDirection: 'column', flex: bodyType === 'json' ? 1 : 0, minHeight: 0 }}>
                            <div style={{ height: SECTION_HEADER_HEIGHT, display: 'flex', alignItems: 'center', justifyContent: 'space-between', flexShrink: 0 }}>
                                <Text strong style={{ fontSize: 12 }}>Body</Text>
                                <Select
                                    size="small"
                                    value={bodyType}
                                    onChange={v => updateData('bodyType', v)}
                                    style={{ width: 100 }}
                                    onMouseDown={e => e.stopPropagation()}
                                    getPopupContainer={trigger => trigger.parentNode}
                                >
                                    <Option value="none">None</Option>
                                    <Option value="json">JSON</Option>
                                    <Option value="form-data">Form</Option>
                                </Select>
                            </div>

                            {bodyType === 'json' && (
                                <div style={{ position: 'relative', flex: 1, minHeight: 60 }}>
                                    <div style={{ position: 'absolute', left: -6, top: 16, width: 8, height: 8, borderRadius: '50%', background: '#1677ff', zIndex: 1 }} />
                                    {/* 设置高度 100% 以填充 flex 容器 */}
                                    <TextArea
                                        value={typeof data.body === 'object' ? JSON.stringify(data.body, null, 2) : data.body}
                                        onChange={(e) => updateData('body', e.target.value)}
                                        style={{ height: '100%', resize: 'none', fontSize: 12, fontFamily: 'monospace', marginLeft: 10, width: 'calc(100% - 10px)' }}
                                        placeholder="{ ... }"
                                        onMouseDown={e => e.stopPropagation()}
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

                            {bodyType === 'none' && <div style={{ height: 20, color: '#ccc', fontSize: 12, textAlign: 'center', flexShrink: 0 }}>No Body</div>}
                        </div>
                    )}
                </div>

            </div>

            {/* 底部 */}
            <div style={{
                height: FOOTER_HEIGHT,
                borderTop: '1px solid #f0f0f0',
                padding: '8px 12px 20px 12px', // 增加了底部填充 
                background: '#fff',
                position: 'relative',
                display: 'flex',
                flexDirection: 'column',
                justifyContent: 'center',
                gap: 4,
                pointerEvents: 'auto',
                flexShrink: 0
            }}>
                <Text style={{ fontSize: 10, color: '#999' }}>Success Condition</Text>
                <Input
                    placeholder="e.g. status == 200"
                    value={data.successCondition}
                    onChange={e => updateData('successCondition', e.target.value)}
                    size="small"
                    variant="borderless"
                    style={{ padding: '0', fontSize: 12, borderBottom: '1px dashed #d9d9d9', width: '70%' }}
                    onMouseDown={e => e.stopPropagation()}
                />
                {/* 根据新的高度和布局调整标签 */}
                <div style={{ position: 'absolute', right: 8, top: 8, fontSize: 10, color: '#52c41a' }}>success</div>
                <div style={{ position: 'absolute', right: 8, top: 32, fontSize: 10, color: '#ff4d4f' }}>fail</div>
            </div>

            <ResizeHandle node={node} minWidth={MIN_WIDTH} minHeight={MIN_WIDTH} onResizeStart={() => setResizing(true)} onResizeEnd={() => setResizing(false)} />
        </NodeWrapper>
    );
};
export default HttpRequestNodeComponent;
