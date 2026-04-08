// ============================================================================
// SystemMethodNodeComponent.tsx — 系统方法调用节点 (V4 — 安全重构)
//
// 重构要点：
//   ✅ 数据源改为后端接口 GET /flow-api/sys-macros/dictionary
//   ✅ 仅展示 macroType === 'FUNCTION' 的宏条目
//   ✅ 入参列表从后端 macroParams 字段解析（逗号分隔）
//   ✅ 选中后不暴露 SpEL 表达式，底部改为展示 macroCode + returnType
//   ✅ 导出 JSON data 中绝不包含 expression，仅为：
//      { methodCode, inputs: { paramName: { extractPath } }, ... }
//   ✅ 移除本地 mock 数据和方法库管理 Drawer/Modal CRUD
//
// 布局：
//   ┌─────────────────────────────────────────┐
//   │  fx  System Method   [Select▼]  [🔗]   │  ← Header (40px)
//   ├─────────────────────────────────────────┤
//   │●  date           [extractPath Input   ] │  ← 参数行 (38px each)
//   │●  format         [extractPath Input   ] │
//   ├─────────────────────────────────────────┤
//   │  🏷 FORMAT_DATE         → String     ●  │  ← 信息行 + out 端口 (40px)
//   └─────────────────────────────────────────┘
// ============================================================================

import React, { useState, useCallback, useEffect, useRef } from 'react';
import {
    Select, Drawer, Table, Typography, Input,
    Tag, Tooltip, Space,
} from 'antd';
import {
    FunctionOutlined, LinkOutlined, CodeOutlined,
} from '@ant-design/icons';
import { Node } from '@antv/x6';
import { useNodeSelection, getNodeTheme, NodeWrapper, NodeHeader } from '../../shared/useNodeSelection';
import {
    useMacroDictionary,
    parseMacroParams,
    MacroDictItem,
} from '../_shared/useMacroDictionary';

const { Text } = Typography;

// ─────────────────────────────────────────────────────────────────────────────
// 布局常量
// ─────────────────────────────────────────────────────────────────────────────

/** 每个参数行高度 */
const ROW_H = 38;
/** Header 高度 */
const HEADER_H = 40;
/** 底部信息行高度 */
const FOOTER_H = 40;
/** 节点宽度（固定） */
const NODE_W = 320;
/** 端口主题色 */
const PORT_COLOR = '#a855f7';

export const SYSTEM_METHOD_LAYOUT = {
    width: NODE_W,
    /** 无参数时节点基础高度 = Header + Footer */
    baseHeight: HEADER_H + FOOTER_H,
    /** out 端口默认 Y（无参数时居中于 footer） */
    outPortY: HEADER_H + FOOTER_H / 2,
};

// ─────────────────────────────────────────────────────────────────────────────
// 端口坐标辅助
// ─────────────────────────────────────────────────────────────────────────────

/** 输入端口 Y：对齐每一参数行垂直中心 */
const calcInPortY = (rowIdx: number) => HEADER_H + rowIdx * ROW_H + ROW_H / 2;

/** 输出端口 Y：信息行垂直中心 */
const calcOutPortY = (paramCount: number) => HEADER_H + paramCount * ROW_H + FOOTER_H / 2;

/** 节点总高度 */
const calcNodeH = (paramCount: number) => HEADER_H + paramCount * ROW_H + FOOTER_H;

// ─────────────────────────────────────────────────────────────────────────────
// 核心：同步端口与 inputs 数据
// ─────────────────────────────────────────────────────────────────────────────

function syncPortsAndInputs(
    node: Node,
    params: string[],
    existing?: Record<string, { extractPath: string }>,
) {
    const nodeH = calcNodeH(params.length);
    const newOutY = calcOutPortY(params.length);

    // Step 1 & 2: 保持/更新现有输入端口，删除多余的，添加缺失的
    const expectedPortIds = new Set(params.map(p => `in:arg:${p}`));

    // 找出所有现有的输入端口
    const currentInPorts = node.getPorts().filter(p => p.id?.startsWith('in:arg:'));

    // 删除不在新参数列表中的端口
    const portsToRemove = currentInPorts.filter(p => p.id && !expectedPortIds.has(p.id));
    if (portsToRemove.length > 0) {
        node.removePorts(portsToRemove.map(p => p.id!));
    }

    // 添加或更新目标参数的端口
    params.forEach((param, idx) => {
        const portId = `in:arg:${param}`;
        const targetY = calcInPortY(idx);
        if (node.hasPort(portId)) {
            try {
                node.setPortProp(portId, 'args', { x: 0, y: targetY });
            } catch (_) { }
        } else {
            node.addPort({
                id: portId,
                group: 'absolute-in-solid',
                args: { x: 0, y: targetY, dx: 0 },
                zIndex: 10,
            });
        }
    });

    // Step 3: 确保 out 端口存在 & 更新 Y
    const outPortId = 'out';
    const outExists = node.getPorts().some(p => p.id === outPortId);
    if (!outExists) {
        if (node.hasPort('out')) node.removePort('out');
        node.addPort({
            id: outPortId,
            group: 'absolute-out-solid',
            args: { x: NODE_W, y: newOutY, dx: 0 },
            zIndex: 10,
        });
    } else {
        try {
            node.setPortProp(outPortId, 'args', { x: NODE_W, y: newOutY });
        } catch (_) { /* ignore */ }
    }

    // Step 4: 调整节点高度
    node.resize(NODE_W, nodeH);

    // Step 5: 同步 node.data.inputs（保留同名参数已有 extractPath）
    const newInputs: Record<string, { extractPath: string }> = {};
    params.forEach(param => {
        newInputs[param] = { extractPath: existing?.[param]?.extractPath ?? '' };
    });
    const currentData = node.getData() as Record<string, any>;
    node.setData({ ...currentData, inputs: newInputs }, { overwrite: true });
}

// ─────────────────────────────────────────────────────────────────────────────
// 主节点组件
// ─────────────────────────────────────────────────────────────────────────────

export const SystemMethodNodeComponent = ({ node }: { node: Node }) => {
    const [data, setData] = React.useState<any>(node.getData() || {});
    const [drawerOpen, setDrawerOpen] = useState(false);
    const initializedRef = useRef(false);

    // ── 从后端拉取宏字典，仅保留 FUNCTION 类型 ──
    const methods = useMacroDictionary('FUNCTION');

    // 监听 node.data 变化
    useEffect(() => {
        const onDataChange = () => setData({ ...node.getData() });
        node.on('change:data', onDataChange);
        return () => { node.off('change:data', onDataChange); };
    }, [node]);

    // 初始化：根据已有 inputs 恢复端口（JSON 回显兼容）
    useEffect(() => {
        if (initializedRef.current) return;
        initializedRef.current = true;

        const nodeData = node.getData() as any;
        const existingInputs: Record<string, { extractPath: string }> = nodeData?.inputs || {};
        const inputKeys = Object.keys(existingInputs);

        if (inputKeys.length > 0) {
            syncPortsAndInputs(node, inputKeys, existingInputs);
        } else if (nodeData?.methodCode) {
            syncPortsAndInputs(node, [], {});
        } else {
            // ── 未选方法：添加占位端口 in:placeholder ──
            syncPortsAndInputs(node, [], {});
            if (!node.hasPort('in:placeholder')) {
                node.addPort({
                    id: 'in:placeholder',
                    group: 'absolute-in-hollow',
                    args: { x: 0, y: HEADER_H + FOOTER_H / 2, dx: 0 },
                    zIndex: 10,
                });
            }
        }
    }, [node]);

    const themeObj = getNodeTheme(data?.themeColor || '#a855f7');
    const { outlineCss, selected } = useNodeSelection(node, {
        defaultColor: '#e9d5ff',
        selectedColor: PORT_COLOR,
        borderRadius: 12,
    });

    /**
     * 方法切换处理器
     *
     * 🔒 安全净化：
     *   只向 node.data 写入 methodCode + inputs（入参映射），
     *   绝不写入 expression，确保导出的 JSON 不泄露 SpEL 表达式。
     *
     * 入参列表来源：后端 macroParams 字段（逗号分隔，如 "date, format"）
     */
    const handleMethodChange = useCallback((code: string) => {
        const method = methods.find(m => m.macroCode === code);
        if (!method) return;

        // 从后端 macroParams 字段解析入参列表
        const params = parseMacroParams(method.macroParams);

        // ── 占位端口 → 真实端口重定向 ──
        if (node.hasPort('in:placeholder') && params.length > 0) {
            const graph = (node as any).model?.graph ?? (node as any).getModel?.()?.graph;
            if (graph && typeof graph.getConnectedEdges === 'function') {
                const connectedEdges = graph.getConnectedEdges(node);
                const firstRealPortId = `in:arg:${params[0]}`;

                connectedEdges.forEach((edge: any) => {
                    const target = edge.getTarget() as any;
                    if (target?.port === 'in:placeholder' && target?.cell === node.id) {
                        edge.setTarget({ cell: node.id, port: firstRealPortId });
                    }
                });
            }
        }

        const currentData = node.getData() as any;
        const oldInputs = currentData?.inputs || {};
        syncPortsAndInputs(node, params, oldInputs);

        // ── 移除占位端口 ──
        if (node.hasPort('in:placeholder')) {
            node.removePort('in:placeholder');
        }

        // ── 🔒 净化写入：只保存 methodCode，不保存 expression ──
        const afterSync = node.getData() as any;

        // 先删除可能存在的旧 expression 字段（兼容升级旧数据）
        const { expression: _discarded, ...cleanData } = afterSync;

        node.setData({
            ...cleanData,
            methodCode: method.macroCode,
            __label: method.macroName,
            // ⚠️ 注意：此处故意不写入 expression！
        }, { overwrite: true });
    }, [node, methods]);

    // 单个参数 extractPath 更新
    const handleExtractPathChange = useCallback((param: string, value: string) => {
        const currentData = node.getData() as any;
        const inputs = { ...(currentData?.inputs || {}), [param]: { extractPath: value } };
        node.setData({ ...currentData, inputs }, { overwrite: true });
    }, [node]);

    // 查找当前选中的宏条目
    const selectedMethod = methods.find(m => m.macroCode === data?.methodCode);
    // 入参列表：优先从 data.inputs 恢复（兼容已保存的 JSON），否则从后端字典解析
    const params: string[] = selectedMethod
        ? parseMacroParams(selectedMethod.macroParams)
        : Object.keys(data?.inputs || {});
    const inputs: Record<string, { extractPath: string }> = data?.inputs || {};
    const nodeLabel = data?.__label || 'System Method';

    // ── Header extra: Select + 方法列表按钮 ──────────────────────────────────
    const headerExtra = (
        <div
            onMouseDown={e => e.stopPropagation()}
            style={{ flex: 1, marginLeft: 6, minWidth: 0, display: 'flex', alignItems: 'center', gap: 2 }}
        >
            <Select
                size="small"
                variant="borderless"
                placeholder="选择方法..."
                value={data?.methodCode || undefined}
                onChange={handleMethodChange}
                dropdownMatchSelectWidth={240}
                style={{ flex: 1, fontSize: 12, minWidth: 0 }}
                // ✅ 下拉挂到 body，不受画布容器 overflow:hidden 限制
                getPopupContainer={() => document.body}
                options={methods.map(m => {
                    const mParams = parseMacroParams(m.macroParams);
                    return {
                        value: m.macroCode,
                        label: (
                            <span>
                                <span style={{ fontWeight: 500 }}>{m.macroName}</span>
                                {mParams.length > 0 && (
                                    <span style={{ color: '#8c8c8c', fontSize: 11, marginLeft: 4 }}>
                                        ({mParams.join(', ')})
                                    </span>
                                )}
                            </span>
                        ),
                    };
                })}
            />
            <Tooltip title="查看方法列表">
                <div
                    onClick={e => { e.stopPropagation(); setDrawerOpen(true); }}
                    style={{
                        width: 22, height: 22, display: 'flex', alignItems: 'center',
                        justifyContent: 'center', cursor: 'pointer', borderRadius: 4,
                        color: PORT_COLOR, flexShrink: 0, transition: 'background 0.15s',
                    }}
                    onMouseEnter={e => (e.currentTarget.style.background = '#f3e8ff')}
                    onMouseLeave={e => (e.currentTarget.style.background = 'transparent')}
                >
                    <LinkOutlined style={{ fontSize: 12 }} />
                </div>
            </Tooltip>
        </div>
    );

    return (
        <>
            <NodeWrapper
                node={node}
                selected={selected}
                themeColor={PORT_COLOR}
                outlineCss={{ ...outlineCss, borderRadius: 12 }}
                backgroundColor={themeObj.bodyBg}
            >
                {/* ── Header ── */}
                <NodeHeader
                    icon={<FunctionOutlined style={{ fontSize: 13 }} />}
                    title={nodeLabel}
                    theme={themeObj}
                    height={HEADER_H}
                    onTitleChange={newTitle => {
                        node.setData({ ...node.getData(), __label: newTitle });
                    }}
                    extra={headerExtra}
                />

                {/* ── Body: 动态参数列表 ── */}
                {params.length > 0 && (
                    <div style={{ pointerEvents: 'auto', flexShrink: 0 }}>
                        {params.map((param, idx) => {
                            const extractPath = inputs[param]?.extractPath ?? '';
                            const isLast = idx === params.length - 1;
                            return (
                                <div
                                    key={param}
                                    style={{
                                        height: ROW_H,
                                        display: 'flex',
                                        alignItems: 'center',
                                        padding: '0 12px 0 20px',
                                        gap: 8,
                                        borderBottom: isLast ? 'none' : '1px solid #f5f0ff',
                                        position: 'relative',
                                    }}
                                >
                                    {/* 参数名标签 */}
                                    <div
                                        style={{
                                            width: 64,
                                            flexShrink: 0,
                                            fontSize: 12,
                                            fontWeight: 500,
                                            color: '#7c3aed',
                                            background: '#f5f0ff',
                                            borderRadius: 4,
                                            padding: '2px 7px',
                                            overflow: 'hidden',
                                            textOverflow: 'ellipsis',
                                            whiteSpace: 'nowrap',
                                            userSelect: 'none',
                                            textAlign: 'center',
                                        }}
                                        title={param}
                                    >
                                        {param}
                                    </div>

                                    {/* extractPath Input */}
                                    <Input
                                        size="small"
                                        variant="filled"
                                        placeholder="extractPath..."
                                        value={extractPath}
                                        onChange={e => handleExtractPathChange(param, e.target.value)}
                                        onMouseDown={e => e.stopPropagation()}
                                        style={{
                                            flex: 1,
                                            fontSize: 12,
                                            height: 26,
                                            minWidth: 0,
                                            borderRadius: 4,
                                        }}
                                    />
                                </div>
                            );
                        })}
                    </div>
                )}

                {/* ── 底部信息行：展示 macroCode + returnType（不暴露 SpEL） ── */}
                <div
                    style={{
                        height: FOOTER_H,
                        display: 'flex',
                        alignItems: 'center',
                        padding: '0 20px 0 12px',
                        gap: 6,
                        background: '#faf5ff',
                        borderTop: '1px solid #e9d5ff',
                        borderRadius: '0 0 11px 11px',
                        pointerEvents: 'auto',
                        flexShrink: 0,
                        minHeight: FOOTER_H,
                    }}
                >
                    <CodeOutlined style={{ color: PORT_COLOR, fontSize: 12, flexShrink: 0 }} />
                    {selectedMethod ? (
                        <>
                            {/* 展示宏编码 */}
                            <Text
                                style={{
                                    flex: 1,
                                    fontSize: 11,
                                    color: '#595959',
                                    fontFamily: 'monospace',
                                    overflow: 'hidden',
                                    textOverflow: 'ellipsis',
                                    whiteSpace: 'nowrap',
                                    minWidth: 0,
                                }}
                                title={`宏编码: ${selectedMethod.macroCode}`}
                            >
                                {selectedMethod.macroCode}
                            </Text>
                            {/* 展示返回值类型标签 */}
                            {selectedMethod.returnType && (
                                <Tag
                                    color="purple"
                                    style={{ fontSize: 10, margin: 0, lineHeight: '16px', padding: '0 6px', flexShrink: 0 }}
                                >
                                    → {selectedMethod.returnType}
                                </Tag>
                            )}
                        </>
                    ) : (
                        <Text
                            style={{
                                flex: 1,
                                fontSize: 11,
                                color: '#bfbfbf',
                                fontFamily: 'monospace',
                                overflow: 'hidden',
                                textOverflow: 'ellipsis',
                                whiteSpace: 'nowrap',
                                minWidth: 0,
                            }}
                        >
                            请选择系统方法
                        </Text>
                    )}
                    {/* 右侧留 8px 给 X6 out 端口圆心 */}
                    <div style={{ width: 8, flexShrink: 0 }} />
                </div>
            </NodeWrapper>

            {/* ── 方法列表浏览抽屉（只读，不暴露 SpEL）── */}
            <MethodListDrawer
                open={drawerOpen}
                onClose={() => setDrawerOpen(false)}
                onSelect={handleMethodChange}
                selectedCode={data?.methodCode}
                methods={methods}
            />
        </>
    );
};

// ─────────────────────────────────────────────────────────────────────────────
// 方法列表浏览抽屉（只读）
//
// 🔒 安全：不展示 SpEL 表达式，不提供本地 CRUD。
//    方法管理统一在「全局参数管理(SysMacroManage)」页面进行。
// ─────────────────────────────────────────────────────────────────────────────

interface MethodListDrawerProps {
    open: boolean;
    onClose: () => void;
    onSelect: (code: string) => void;
    selectedCode?: string;
    methods: MacroDictItem[];
}

const MethodListDrawer: React.FC<MethodListDrawerProps> = ({
    open, onClose, onSelect, selectedCode, methods,
}) => {
    const columns = [
        {
            title: '名称 / 编码',
            key: 'name',
            width: 160,
            render: (_: any, m: MacroDictItem) => (
                <div>
                    <div style={{ fontWeight: 500, fontSize: 13 }}>{m.macroName}</div>
                    <Text type="secondary" style={{ fontSize: 11, fontFamily: 'monospace' }}>
                        {m.macroCode}
                    </Text>
                </div>
            ),
        },
        {
            title: '入参',
            key: 'params',
            width: 140,
            render: (_: any, m: MacroDictItem) => {
                const params = parseMacroParams(m.macroParams);
                return params.length > 0 ? (
                    <div style={{ display: 'flex', flexWrap: 'wrap', gap: 4 }}>
                        {params.map(p => (
                            <Tag key={p} color="purple" style={{ fontSize: 11, margin: 0 }}>
                                {p}
                            </Tag>
                        ))}
                    </div>
                ) : (
                    <Text type="secondary" style={{ fontSize: 11 }}>无入参</Text>
                );
            },
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
    ];

    return (
        <Drawer
            title={
                <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
                    <Space>
                        <FunctionOutlined style={{ color: PORT_COLOR }} />
                        <Text strong>系统方法列表</Text>
                    </Space>
                    <Text type="secondary" style={{ fontSize: 12 }}>
                        管理请前往「全局参数管理」
                    </Text>
                </div>
            }
            placement="right"
            width={520}
            onClose={onClose}
            open={open}
        >
            <Text type="secondary" style={{ display: 'block', marginBottom: 16, fontSize: 13 }}>
                以下为当前所有已启用的方法型宏定义。点击行可快速选中。
                如需新增或编辑方法，请前往「全局参数管理」页面操作。
            </Text>
            <Table<MacroDictItem>
                size="small"
                dataSource={methods}
                columns={columns}
                pagination={false}
                rowKey="macroCode"
                onRow={m => ({
                    onClick: () => { onSelect(m.macroCode); onClose(); },
                    style: {
                        cursor: 'pointer',
                        background: m.macroCode === selectedCode ? '#faf5ff' : undefined,
                    },
                })}
            />
        </Drawer>
    );
};

export default SystemMethodNodeComponent;
