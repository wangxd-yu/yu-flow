// ============================================================================
// SystemVarNodeComponent.tsx — 系统变量节点 (V4 — 安全重构)
//
// 重构要点：
//   ✅ 数据源改为后端接口 GET /flow-api/sys-macros/dictionary
//   ✅ 仅展示 macroType === 'VARIABLE' 的宏条目
//   ✅ 选中后展示 macroCode + returnType，不暴露任何 SpEL 表达式
//   ✅ 导出 JSON data 中绝对不包含 expression 字段
//   ✅ 抽屉中不再展示 SpEL 表达式列，只展示安全字段
// ============================================================================

import React, { useState } from 'react';
import { Select, Drawer, Table, Typography, Switch, Tooltip, Tag } from 'antd';
import { SettingOutlined, LinkOutlined } from '@ant-design/icons';
import { Node } from '@antv/x6';
import { useNodeSelection, getNodeTheme, NodeToolbar } from '../../shared/useNodeSelection';
import { useMacroDictionary, MacroDictItem } from '../_shared/useMacroDictionary';

const { Text } = Typography;

export const SYSTEM_VAR_LAYOUT = {
    width: 280,
    totalHeight: 38,
    outPortY: 19,
};

export const SystemVarNodeComponent = ({ node }: { node: Node }) => {
    const [data, setData] = React.useState<any>(node.getData() || {});
    const [drawerOpen, setDrawerOpen] = useState(false);
    const [hovered, setHovered] = useState(false);

    // ── 从后端拉取宏字典，仅保留 VARIABLE 类型 ──
    const variables = useMacroDictionary('VARIABLE');

    React.useEffect(() => {
        const onDataChange = () => setData({ ...node.getData() });
        const onSizeChange = () => {
            const size = node.getSize();
            // 同步调整输出端口位置
            node.setPortProp('out', 'args', { x: size.width, y: size.height / 2 });
            const graph = node.model?.graph;
            if (graph) {
                graph.getConnectedEdges(node).forEach(edge => {
                    const view = graph.findViewByCell(edge) as any;
                    if (view) view.update();
                });
            }
        };

        node.on('change:data', onDataChange);
        node.on('change:size', onSizeChange);
        return () => {
            node.off('change:data', onDataChange);
            node.off('change:size', onSizeChange);
        };
    }, [node]);

    const themeObj = getNodeTheme(data?.themeColor || '#34d399');
    const { outlineCss, selected } = useNodeSelection(node, { defaultColor: themeObj.primary, selectedColor: themeObj.primary });

    /**
     * 用户在下拉框中选择变量时的处理逻辑
     *
     * 🔒 安全净化：
     *   只向 node.data 写入 variableCode（宏编码），
     *   绝不写入 expression，确保导出 JSON 不泄露 SpEL。
     */
    const handleSelectChange = (val: string) => {
        const target = variables.find(v => v.macroCode === val);
        if (target) {
            node.setData({
                ...node.getData(),
                variableCode: target.macroCode,  // 前端凭证
                __label: target.macroName,        // 仅用于画布显示
                // ⚠️ 注意：此处故意不写入 expression！
            });
        }
    };

    // 查找当前选中的宏条目（用于显示额外信息）
    const selectedMacro = variables.find(v => v.macroCode === data.variableCode);

    return (
        <>
            <div
                onMouseEnter={() => setHovered(true)}
                onMouseLeave={() => setHovered(false)}
                style={{ position: 'relative', width: '100%', height: '100%' }}
            >
                <NodeToolbar node={node} selected={selected} themeColor={themeObj.primary} visible={hovered && selected} />

                <div
                    style={{
                        width: '100%',
                        height: '100%',
                        borderRadius: 4,
                        backgroundColor: '#ffffff',
                        border: `1px solid ${selected ? themeObj.primary : '#e5e6eb'}`,
                        boxShadow: selected ? outlineCss.boxShadow : '0 2px 4px rgba(0,0,0,0.02)',
                        display: 'flex',
                        alignItems: 'center',
                        padding: '2px',
                        transition: 'all 0.2s'
                    }}
                >
                    {/* 左侧图标 */}
                    <div style={{
                        width: 32,
                        height: 32,
                        borderRadius: 4,
                        backgroundColor: '#f5f5f5',
                        display: 'flex',
                        alignItems: 'center',
                        justifyContent: 'center',
                        flexShrink: 0
                    }}>
                        <SettingOutlined style={{ color: '#8c8c8c' }} />
                    </div>

                    {/* 中间下拉框 —— 数据源改为后端接口 */}
                    <div onMouseDown={(e) => e.stopPropagation()} style={{ flex: 1, padding: '0 8px', overflow: 'hidden' }}>
                        <Select
                            size="small"
                            bordered={false}
                            placeholder="选择系统变量"
                            value={data.variableCode || undefined}
                            onChange={handleSelectChange}
                            dropdownMatchSelectWidth={false}
                            style={{ width: '100%', fontSize: 13 }}
                            // ✅ 下拉挂到 body，突破画布 overflow 限制
                            getPopupContainer={() => document.body}
                            options={variables.map(v => ({
                                value: v.macroCode,
                                label: (
                                    <span>
                                        <span style={{ fontWeight: 500 }}>{v.macroName}</span>
                                        {/* 显示返回值类型标签 */}
                                        {v.returnType && (
                                            <Tag
                                                color="green"
                                                style={{ fontSize: 10, marginLeft: 6, lineHeight: '16px', padding: '0 4px' }}
                                            >
                                                {v.returnType}
                                            </Tag>
                                        )}
                                    </span>
                                ),
                            }))}
                        />
                    </div>

                    {/* 禁用缓存开关 */}
                    <div onMouseDown={(e) => e.stopPropagation()} style={{ flexShrink: 0, padding: '0 8px', display: 'flex', alignItems: 'center', borderLeft: '1px solid #f0f0f0' }}>
                        <Tooltip title="禁用缓存 (Volatile)：开启后，下游每次读取该变量都会重新执行求值。适用于获取实时时间戳或动态随机数。默认关闭，保证单次流程中该变量全局唯一。">
                            <Switch
                                size="small"
                                checked={!!data.volatileVar}
                                onChange={(checked) => node.setData({ ...node.getData(), volatileVar: checked })}
                            />
                        </Tooltip>
                    </div>

                    {/* 右侧外链按钮 —— 打开变量列表抽屉 */}
                    <div style={{
                        width: 30,
                        height: 30,
                        display: 'flex',
                        alignItems: 'center',
                        justifyContent: 'center',
                        flexShrink: 0,
                        marginRight: 2,
                        borderLeft: '1px solid #f0f0f0',
                        cursor: 'pointer',
                        transition: 'background 0.2s',
                        borderRadius: '0 4px 4px 0'
                    }}
                        onMouseEnter={(e) => e.currentTarget.style.backgroundColor = '#f5f5f5'}
                        onMouseLeave={(e) => e.currentTarget.style.backgroundColor = 'transparent'}
                        onClick={(e) => {
                            e.stopPropagation();
                            setDrawerOpen(true);
                        }}>
                        <LinkOutlined style={{ color: '#1677ff' }} />
                    </div>
                </div>
            </div>

            {/* ── 全局变量浏览抽屉（只读，不暴露 SpEL）── */}
            <Drawer
                title={
                    <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
                        <Text strong>系统全局变量列表</Text>
                    </div>
                }
                placement="right"
                width={460}
                onClose={() => setDrawerOpen(false)}
                open={drawerOpen}
            >
                <div>
                    <Text type="secondary" style={{ display: 'block', marginBottom: 16 }}>
                        全局系统变量由管理员在「全局参数管理」页面配置。以下为当前所有已启用的变量型宏定义。
                    </Text>
                    <Table<MacroDictItem>
                        size="small"
                        dataSource={variables}
                        pagination={false}
                        rowKey="macroCode"
                        columns={[
                            {
                                title: '宏编码',
                                dataIndex: 'macroCode',
                                key: 'macroCode',
                                width: 160,
                                render: (code: string) => (
                                    <Text copyable style={{ fontSize: 12, fontFamily: 'monospace' }}>
                                        {code}
                                    </Text>
                                ),
                            },
                            {
                                title: '名称',
                                dataIndex: 'macroName',
                                key: 'macroName',
                                width: 140,
                            },
                            {
                                title: '返回类型',
                                dataIndex: 'returnType',
                                key: 'returnType',
                                width: 100,
                                render: (type: string) =>
                                    type ? <Tag color="green">{type}</Tag> : <Text type="secondary">—</Text>,
                            },
                            // 🔒 安全：此处故意不展示 expression 列
                        ]}
                        onRow={(record) => ({
                            onClick: () => {
                                handleSelectChange(record.macroCode);
                                setDrawerOpen(false);
                            },
                            style: {
                                cursor: 'pointer',
                                background: record.macroCode === data.variableCode ? '#f6ffed' : undefined,
                            },
                        })}
                    />
                </div>
            </Drawer>
        </>
    );
};

export default SystemVarNodeComponent;
