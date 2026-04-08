// ============================================================================
// DatabaseNode.tsx — Database 节点 (Refactored)
// 功能: 动态变量(placeholder连接即新增)、拖拽排序、缩放、SQL操作
// 变量操作使用 useNodeVariables 共享 Hook
// ============================================================================

import React from 'react';
import { Typography, Space, Dropdown, Select } from 'antd';
import { Node } from '@antv/x6';
import CodeEditor from '../../../components/CodeEditor';
import { useNodeSelection, NodeHeader, NodeWrapper, ResizeHandle, getNodeTheme } from '../../shared/useNodeSelection';
import { useNodeVariables, NodeVariable } from '../../shared/useNodeVariables';
import { DynamicVariableList } from '../../shared/DynamicVariableList';
import {
    BaseExpressionNode,
    HEADER_HEIGHT, ROW_HEIGHT, VAR_PADDING, COND_PADDING, MIN_WIDTH, MIN_QUERY_HEIGHT,
} from '../../shared/BaseExpressionNode';

const { Text } = Typography;

export type DatabaseVariable = NodeVariable;

export interface DatabaseNodeData {
    datasourceId?: string;
    sqlType: 'SELECT' | 'INSERT' | 'UPDATE' | 'DELETE';
    returnType?: 'LIST' | 'OBJECT' | 'PAGE';
    sql: string;
    inputs: Record<string, { extractPath: string }>;
    themeColor?: string;
    __variables?: DatabaseVariable[];
}

// ── 布局常量 ──
const DATASOURCE_ROW_HEIGHT = 36;
const FOOTER_HEIGHT = 44;
const FT_RESULT_Y = 12;

export const DATABASE_LAYOUT = {
    headerHeight: HEADER_HEIGHT, footerHeight: FOOTER_HEIGHT, width: MIN_WIDTH,
    get totalHeight() { return HEADER_HEIGHT + DATASOURCE_ROW_HEIGHT + ROW_HEIGHT + VAR_PADDING + MIN_QUERY_HEIGHT + COND_PADDING + FOOTER_HEIGHT; },
    get footerTop() { return this.totalHeight - FOOTER_HEIGHT; },
    get outPortY() { return this.footerTop + FT_RESULT_Y; },
};

const varPortY = (idx: number) => HEADER_HEIGHT + DATASOURCE_ROW_HEIGHT + VAR_PADDING / 2 + idx * ROW_HEIGHT + ROW_HEIGHT / 2;

const ICONS = {
    database: (<svg viewBox="0 0 1024 1024" width="14" height="14" fill="currentColor"><path d="M832 64H192c-17.7 0-32 14.3-32 32v832c0 17.7 14.3 32 32 32h640c17.7 0 32-14.3 32-32V96c0-17.7-14.3-32-32-32zm-600 72h560v208H232V136zm560 480H232V408h560v208zm0 272H232V680h560v208z" /><path d="M304 208m-32 0a32 32 0 1 0 64 0 32 32 0 1 0-64 0ZM304 480m-32 0a32 32 0 1 0 64 0 32 32 0 1 0-64 0ZM304 752m-32 0a32 32 0 1 0 64 0 32 32 0 1 0-64 0Z" /></svg>),
    chevron: (<svg viewBox="0 0 1024 1024" width="12" height="12" fill="currentColor"><path d="M831.872 340.864 512 652.672 192.128 340.864a30.592 30.592 0 0 0-42.752 0 29.12 29.12 0 0 0 0 41.6L489.664 714.24a32 32 0 0 0 44.672 0l340.288-331.712a29.12 29.12 0 0 0 0-41.728 30.592 30.592 0 0 0-42.752 0z" /></svg>),
};

const SQL_TYPE_OPTIONS = [
    { key: 'SELECT', label: 'SELECT' },
    { key: 'INSERT', label: 'INSERT' },
    { key: 'UPDATE', label: 'UPDATE' },
    { key: 'DELETE', label: 'DELETE' },
];

const RETURN_TYPE_OPTIONS = [
    { key: 'LIST', label: 'LIST' },
    { key: 'OBJECT', label: 'OBJECT' },
    { key: 'PAGE', label: 'PAGE' },
];

// ============================================================================
export const DatabaseNode = ({ node }: { node: Node }) => {
    const [data, setData] = React.useState<DatabaseNodeData>(node.getData() as DatabaseNodeData);
    const themeObj = getNodeTheme(data?.themeColor || 'blue');
    const { outlineCss, borderColor, selected } = useNodeSelection(node, { defaultColor: themeObj.primary, selectedColor: themeObj.primary });
    const [size, setSize] = React.useState(node.getSize());

    React.useEffect(() => {
        const onD = () => setData({ ...node.getData() } as DatabaseNodeData);
        const onS = () => setSize({ ...node.getSize() });
        node.on('change:data', onD); node.on('change:size', onS);
        return () => { node.off('change:data', onD); node.off('change:size', onS); };
    }, [node]);

    const sqlType = data?.sqlType || 'SELECT';
    const returnType = data?.returnType || 'LIST';
    const sql = data?.sql || '';
    const datasourceId = data?.datasourceId;

    // ── 数据源列表 ──
    const [dataSourceOptions, setDataSourceOptions] = React.useState<{ label: string; value: string }[]>([]);
    React.useEffect(() => {
        import('@/pages/flow/dataSource/services/dataSource').then(({ queryDataSourceList }) => {
            queryDataSourceList().then((res: any) => {
                const list = res?.data || res || [];
                setDataSourceOptions(list.map((item: any) => ({ label: item.name, value: item.code })));
            }).catch(() => {
                setDataSourceOptions([
                    { label: '主库 (Primary)', value: 'ds1' },
                    { label: '从库 (Replica)', value: 'ds2' },
                ]);
            });
        }).catch(() => {
            setDataSourceOptions([
                { label: '主库 (Primary)', value: 'ds1' },
                { label: '从库 (Replica)', value: 'ds2' },
            ]);
        });
    }, []);

    // ── 使用共享变量 Hook ──
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

    // ── 端口同步 (out) ──
    React.useEffect(() => {
        const ports = node.getPorts();
        const existing = new Set(ports.map((p) => p.id));

        // 移除旧的 in 端口 (如果存在)
        if (existing.has('in')) {
            node.removePort('in');
        }

        // 2. 确保 out 端口存在 (放到右下角)
        const s = node.getSize();
        const ft = s.height - FOOTER_HEIGHT;
        const outY = ft + FT_RESULT_Y;
        const outX = s.width;

        if (!existing.has('out')) {
            node.addPort({
                id: 'out',
                group: 'absolute-out-solid',
                args: { x: outX, y: outY, dx: 0 },
                zIndex: 1,
            });
        } else {
            const p = ports.find(p => p.id === 'out');
            if (p?.attrs?.text?.text !== '') {
                node.setPortProp('out', 'attrs/text/text', '');
            }
            if (p?.group !== 'absolute-out-solid') {
                node.setPortProp('out', 'group', 'absolute-out-solid');
            }
        }
    }, [variables, node, size]);

    // ── 缩放 ──
    const [resizing, setResizing] = React.useState(false);
    const contentH = HEADER_HEIGHT + DATASOURCE_ROW_HEIGHT + variables.length * ROW_HEIGHT + VAR_PADDING + COND_PADDING + FOOTER_HEIGHT;
    const minH = contentH + MIN_QUERY_HEIGHT;

    React.useEffect(() => { if (!resizing) { const s = node.getSize(); if (s.height < minH) node.resize(Math.max(s.width, MIN_WIDTH), minH); } }, [minH, node, resizing]);

    const handleResize = React.useCallback((nw: number, nh: number) => {
        const ft = nh - FOOTER_HEIGHT;
        node.setPortProp('out', 'args', { x: nw, y: ft + FT_RESULT_Y, dx: 0 });
        updateEdges('out');
    }, [node, updateEdges]);

    // ── 底部端口同步 ──
    React.useEffect(() => {
        if (resizing) return;
        const s = node.getSize(); const ft = s.height - FOOTER_HEIGHT;
        try {
            node.setPortProp('out', 'args', { x: s.width, y: ft + FT_RESULT_Y, dx: 0 });
            updateEdges('out');
        } catch (_) { }
    }, [size, variables.length, resizing]);

    // ── 标题 ──
    const nodeLabel = (data as any)?.__label || 'Database';
    const handleTitleChange = React.useCallback((newTitle: string) => {
        node.setData({ ...node.getData(), __label: newTitle });
    }, [node]);

    // ── 下拉菜单 ──
    const sqlTypeMenu = {
        items: SQL_TYPE_OPTIONS,
        onClick: ({ key }: any) => {
            const newData: any = { ...node.getData(), sqlType: key };
            if (key !== 'SELECT') {
                delete newData.returnType;
            } else if (!newData.returnType) {
                newData.returnType = 'LIST';
            }
            node.setData(newData);
        },
    };

    const returnTypeMenu = {
        items: RETURN_TYPE_OPTIONS,
        onClick: ({ key }: any) => node.setData({ ...node.getData(), returnType: key }),
    };

    return (
        <NodeWrapper node={node} selected={selected} themeColor={borderColor} outlineCss={outlineCss} backgroundColor={themeObj.bodyBg}>
            {/* Header */}
            <NodeHeader icon={ICONS.database} title={nodeLabel} theme={themeObj} height={HEADER_HEIGHT}
                onTitleChange={handleTitleChange}
                extra={
                    <Space size={8}>
                        {sqlType === 'SELECT' && (
                            <Dropdown menu={returnTypeMenu} trigger={['click']}>
                                <div onClick={(e) => e.stopPropagation()} onMouseDown={(e) => e.stopPropagation()} style={{ cursor: 'pointer', display: 'flex', alignItems: 'center', gap: 4 }}>
                                    <Text style={{ fontSize: 11, color: themeObj.primary }}>{returnType}</Text>
                                    <div style={{ color: themeObj.primary, display: 'flex' }}>{ICONS.chevron}</div>
                                </div>
                            </Dropdown>
                        )}
                        <Dropdown menu={sqlTypeMenu} trigger={['click']}>
                            <div onClick={(e) => e.stopPropagation()} onMouseDown={(e) => e.stopPropagation()} style={{ cursor: 'pointer', display: 'flex', alignItems: 'center', gap: 4 }}>
                                <Text style={{ fontSize: 11, color: themeObj.primary }}>{sqlType}</Text>
                                <div style={{ color: themeObj.primary, display: 'flex' }}>{ICONS.chevron}</div>
                            </div>
                        </Dropdown>
                    </Space>
                }
            />

            {/* Data Source Selector */}
            <div style={{
                height: DATASOURCE_ROW_HEIGHT,
                padding: '4px 12px',
                display: 'flex',
                alignItems: 'center',
                gap: 8,
                borderBottom: '1px solid #f0f0f0',
                pointerEvents: 'auto',
                flexShrink: 0,
            }}>
                <Text style={{ fontSize: 11, color: '#8c8c8c', flexShrink: 0 }}>数据源</Text>
                <Select
                    size="small"
                    value={datasourceId}
                    placeholder="选择数据源..."
                    options={dataSourceOptions}
                    onChange={(val) => node.setData({ ...node.getData(), datasourceId: val })}
                    onMouseDown={(e) => e.stopPropagation()}
                    onClick={(e) => e.stopPropagation()}
                    style={{ flex: 1, fontSize: 11 }}
                    getPopupContainer={(trigger) => trigger.parentElement || document.body}
                    allowClear
                />
            </div>

            {/* Variables — 使用共享组件 */}
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

            {/* SQL Editor — CodeMirror */}
            <div style={{ padding: '8px 12px', pointerEvents: 'auto', flex: 1, display: 'flex', flexDirection: 'column', minHeight: MIN_QUERY_HEIGHT }}>
                <div style={{ flex: 1, minHeight: 60, display: 'flex', flexDirection: 'column' }}
                    onMouseDown={(e) => e.stopPropagation()}
                    onClick={(e) => e.stopPropagation()}
                >
                    <CodeEditor
                        value={sql}
                        onChange={(value) => {
                            const val = value || '';
                            const cleanSql = val.replace(/\/\*[\s\S]*?\*\//g, '').replace(/--.*/g, '').trim().toUpperCase();
                            const newData: any = { ...node.getData(), sql: val };

                            let detectedType = null;
                            if (cleanSql.startsWith('INSERT')) detectedType = 'INSERT';
                            else if (cleanSql.startsWith('UPDATE')) detectedType = 'UPDATE';
                            else if (cleanSql.startsWith('DELETE')) detectedType = 'DELETE';
                            else if (cleanSql.startsWith('SELECT')) detectedType = 'SELECT';

                            if (detectedType && newData.sqlType !== detectedType) {
                                newData.sqlType = detectedType;
                                if (detectedType !== 'SELECT') {
                                    delete newData.returnType;
                                } else if (!newData.returnType) {
                                    newData.returnType = 'LIST';
                                }
                            }

                            node.setData(newData);
                        }}
                        language="sql"
                        height="100%"
                        lineNumbers={false}
                        fontSize={12}
                        style={{ border: '1px solid #e8e8e8', flex: 1, display: 'flex', flexDirection: 'column' }}
                    />
                </div>
            </div>

            {/* Footer — Result 输出 */}
            <div style={{ height: FOOTER_HEIGHT, position: 'relative', pointerEvents: 'auto', flexShrink: 0, display: 'flex', alignItems: 'center', justifyContent: 'flex-end', padding: '0 12px' }}>
                <div style={{ position: 'absolute', right: 10, top: FT_RESULT_Y, transform: 'translateY(-50%)', display: 'flex', alignItems: 'center', gap: 6 }}>
                    <Text style={{ fontSize: 12, color: '#595959' }}>Result</Text>
                </div>
            </div>

            {/* Resize */}
            <ResizeHandle node={node} minWidth={MIN_WIDTH} minHeight={minH} onResize={handleResize} color={themeObj.primary}
                onResizeStart={() => setResizing(true)} onResizeEnd={() => setResizing(false)} />
        </NodeWrapper>
    );
};

export default DatabaseNode;
