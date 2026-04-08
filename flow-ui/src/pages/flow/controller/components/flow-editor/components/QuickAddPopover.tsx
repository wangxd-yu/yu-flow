// ============================================================================
// QuickAddPopover.tsx
// 快捷添加弹窗 —— 类似 Spotlight / Postman 连线弹窗
// 当用户从端口拖出连线到空白画布松手时弹出，提供搜索 + 分组节点列表
//
// V3.3: 支持反向拉线 (Reverse Drag) 与基于极性的智能过滤 (Polarity Filtering)
//   - direction='forward': 从 out 端口拉线，需要下游节点 → 隐藏仅有输出的节点
//   - direction='reverse': 从 in 端口拉线，需要上游数据源 → 隐藏无输出的终端节点
//   并在选中节点后自动反转连线方向 (Edge Flipping)
// ============================================================================

import React, { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { Input, Empty, message } from 'antd';
import { SearchOutlined, ThunderboltOutlined } from '@ant-design/icons';
import type { Graph, Edge } from '@antv/x6';
import type { DslNodeType } from '../types';
import { getRegistrationsByCategory, getNodeRegistration, getNodeSize, getDefaultPorts } from '../node-registry';
import type { NodeRegistration } from '../node-registry';
import {
    createDefaultDslNode,
    addSingleNodeToGraph,
    EDGE_CONFIG,
} from '../adapter';

// ── Types ──────────────────────────────────────────────────────────────────

export interface QuickAddPopoverProps {
    /** X6 Graph 实例 */
    graph: Graph;
    /** 弹窗显示的页面坐标 X */
    x: number;
    /** 弹窗显示的页面坐标 Y */
    y: number;
    /** 悬空虚线边的 ID */
    sourceEdgeId: string;
    /** 新节点应放置的画布坐标 */
    canvasPosition: { x: number; y: number };
    /** 拉线极性：forward=正向（从 out 拉），reverse=反向（从 in 拉） */
    direction: 'forward' | 'reverse';
    /** 单例检查 */
    canCreate?: (type: DslNodeType) => { ok: boolean; reason?: string };
    /** 节点创建成功后的回调 (用于触发选中状态、emitChange 等) */
    onNodeCreated?: (nodeId: string, type: DslNodeType) => void;
    /** 关闭弹窗回调 */
    onClose: () => void;
}

// ── 排序权重 ─────────────────────────────────────────────────────────────

const CATEGORY_WEIGHTS: Record<string, number> = {
    '基础节点': 100,
    '逻辑节点': 90,
    '数据节点': 80,
    '调用节点': 70,
    '外部资源': 60,
};

const NODE_WEIGHTS: Partial<Record<DslNodeType, number>> = {
    request: 100, response: 90,
    if: 100, switch: 90, evaluate: 80, forEach: 70,
    record: 100, template: 90, collect: 80, systemVar: 70,
    serviceCall: 100, httpRequest: 90, database: 80,
};

// ── 极性过滤配置 ─────────────────────────────────────────────────────────

/**
 * 无输入端口的节点类型（只有 out / headers / params 等输出端口）
 * 正向拉线 (forward) 时，这些节点无法作为下游目标，应在弹窗中隐藏
 */
const OUTPUT_ONLY_TYPES: Set<DslNodeType> = new Set([
    'systemVar',  // 系统变量：只有 out，无 in
    'request',    // 请求入口：只有 headers/params/body 输出端口
]);

/**
 * 无输出端口的终端节点类型
 * 反向拉线 (reverse) 时，这些节点无法作为上游数据源，应在弹窗中隐藏
 */
const NO_OUTPUT_TYPES: Set<DslNodeType> = new Set([
    'response',   // HTTP 响应节点：只有输入端口，无数据输出
]);

/**
 * 反向拉线时推荐置顶的数据源节点（最常用的上游数据源）
 */
const REVERSE_PREFERRED_TYPES: DslNodeType[] = [
    'systemVar',   // 系统变量
    'database',    // 数据库
    'evaluate',    // 表达式
    'systemMethod', // 系统方法
];

// ── 输出端口 ID 白名单 (用于区分输入/输出端口) ──────────────────────────

const OUTPUT_PORT_IDS = new Set([
    'out', 'true', 'false', 'item', 'done', 'default',
    'headers', 'params', 'body', 'list', 'finish',
]);

function isOutputPort(portId: string): boolean {
    return OUTPUT_PORT_IDS.has(portId)
        || portId.startsWith('out:')
        || portId.startsWith('case_');
}

// ── 主组件 ─────────────────────────────────────────────────────────────────

export default function QuickAddPopover({
    graph,
    x,
    y,
    sourceEdgeId,
    canvasPosition,
    direction,
    canCreate,
    onNodeCreated,
    onClose,
}: QuickAddPopoverProps) {
    const popoverRef = useRef<HTMLDivElement>(null);
    const inputRef = useRef<any>(null);
    const [search, setSearch] = useState('');
    const [highlightIndex, setHighlightIndex] = useState(0);

    // ── 自动聚焦 ──
    useEffect(() => {
        // 延迟聚焦，确保 DOM 已渲染
        const timer = setTimeout(() => {
            inputRef.current?.focus?.();
        }, 50);
        return () => clearTimeout(timer);
    }, []);

    // ── Click Away 监听 ──
    useEffect(() => {
        const handleClickOutside = (e: MouseEvent) => {
            if (popoverRef.current && !popoverRef.current.contains(e.target as HTMLElement)) {
                handleCancel();
            }
        };
        const handleEsc = (e: KeyboardEvent) => {
            if (e.key === 'Escape') {
                handleCancel();
            }
        };
        // 使用 setTimeout 注册，防止触发弹出的那个 mouseup 事件立刻关闭
        const timer = setTimeout(() => {
            document.addEventListener('mousedown', handleClickOutside);
            document.addEventListener('keydown', handleEsc);
        }, 100);
        return () => {
            clearTimeout(timer);
            document.removeEventListener('mousedown', handleClickOutside);
            document.removeEventListener('keydown', handleEsc);
        };
    }, [sourceEdgeId]); // eslint-disable-line react-hooks/exhaustive-deps

    // ── 分组 + 过滤（基于极性的智能过滤） ──
    const filteredGroups = useMemo(() => {
        const keyword = search.trim().toLowerCase();
        const isReverse = direction === 'reverse';

        // 反向拉线时的推荐集合，用于后续排序置顶
        const preferredSet = isReverse
            ? new Set(REVERSE_PREFERRED_TYPES)
            : null;

        return getRegistrationsByCategory()
            .map(([category, regs]) => {
                const filtered = regs
                    .filter((r) => {
                        // ── 极性过滤 ──
                        if (direction === 'forward') {
                            // 正向拉线：隐藏仅有输出、无输入端口的节点
                            if (OUTPUT_ONLY_TYPES.has(r.type)) return false;
                        } else {
                            // 反向拉线：隐藏无输出端口的终端节点
                            if (NO_OUTPUT_TYPES.has(r.type)) return false;
                        }

                        if (!keyword) return true;
                        return (
                            r.label.toLowerCase().includes(keyword) ||
                            r.type.toLowerCase().includes(keyword) ||
                            category.toLowerCase().includes(keyword)
                        );
                    })
                    .sort((a, b) => {
                        // 反向拉线时，推荐的数据源节点置顶
                        if (preferredSet) {
                            const aPreferred = preferredSet.has(a.type) ? 1 : 0;
                            const bPreferred = preferredSet.has(b.type) ? 1 : 0;
                            if (aPreferred !== bPreferred) return bPreferred - aPreferred;
                        }
                        const wa = a.sortOrder ?? NODE_WEIGHTS[a.type] ?? 0;
                        const wb = b.sortOrder ?? NODE_WEIGHTS[b.type] ?? 0;
                        return wb - wa;
                    });
                return [category, filtered] as [string, NodeRegistration[]];
            })
            .filter(([, list]) => list.length > 0)
            .sort((a, b) => {
                const wa = CATEGORY_WEIGHTS[a[0]] ?? 0;
                const wb = CATEGORY_WEIGHTS[b[0]] ?? 0;
                return wb - wa;
            });
    }, [search, direction]);

    // 扁平化列表，用于键盘导航
    const flatItems = useMemo(() => {
        return filteredGroups.flatMap(([, items]) => items);
    }, [filteredGroups]);

    // 当过滤结果变化时重置高亮索引
    useEffect(() => {
        setHighlightIndex(0);
    }, [search]);

    // ── 核心：选中节点后的联动逻辑（含正向吸附 & 反向反转 Edge Flipping） ──
    const handleSelect = useCallback(
        (type: DslNodeType) => {
            // 1. 单例检查
            if (canCreate) {
                const allow = canCreate(type);
                if (!allow.ok) return;
            }

            // 2. 创建新节点
            // 反向拉线时，需要让新节点的输出端口（右侧）对齐悬空箭头位置
            // 因此将节点位置向左偏移一个节点宽度，并垂直居中
            let nodePosition = canvasPosition;
            if (direction === 'reverse') {
                const defaultPorts = getDefaultPorts(type);
                const nodeSize = getNodeSize(type, defaultPorts);
                nodePosition = {
                    x: canvasPosition.x - nodeSize.width,
                    y: canvasPosition.y - nodeSize.height / 2,
                };
            }
            const dslNode = createDefaultDslNode(type, nodePosition);
            const newNode = addSingleNodeToGraph(graph, dslNode);

            // 3. 先通知外层关闭弹窗，使体验连贯
            onNodeCreated?.(dslNode.id, type);
            onClose();

            // 4. 处理悬空边的吸附（使用 setTimeout 等待 React 异步渲染生成动态端口）
            const edgeCell = graph.getCellById(sourceEdgeId);
            if (edgeCell && edgeCell.isEdge()) {
                const edge = edgeCell as Edge;

                // 先将线恢复实线状态，给人已经"牵上"的感觉
                edge.setAttrs({
                    line: {
                        ...EDGE_CONFIG.attrs.line,
                        strokeDasharray: '',
                    },
                });

                setTimeout(() => {
                    const ports = newNode.getPorts() || [];

                    if (direction === 'forward') {
                        // ═══════════════════════════════════════════════════════
                        // 正向吸附 (Forward): edge.source 是老节点 out，target 接新节点 in
                        // ═══════════════════════════════════════════════════════

                        // ── 安全检查：新节点是否有可用的输入端口 ──
                        const viableInputPorts = ports.filter((p: any) => {
                            if (!p.id) return false;
                            if (isOutputPort(p.id)) return false;
                            return true;
                        });

                        // 如果新节点没有任何可用的输入端口 → 销毁悬空边，仅保留节点
                        if (viableInputPorts.length === 0) {
                            graph.removeCell(sourceEdgeId);
                            return;
                        }

                        let targetPortId = 'in'; // 默认兜底

                        // 规则A: 优先匹配动态变量 placeholder，以触发变量自动新增
                        const varPorts = ports.filter((p: any) => p.id?.startsWith('in:var:'));
                        if (varPorts.length > 0) {
                            targetPortId = varPorts[varPorts.length - 1].id!;
                        } else {
                            // 规则A2: 匹配 SystemMethod 占位端口
                            const placeholderPort = ports.find((p: any) => p.id === 'in:placeholder');
                            if (placeholderPort) {
                                targetPortId = placeholderPort.id!;
                            } else {
                                // 规则B: 标准 in 或 start(For) 端口
                                const inPort = ports.find((p: any) => p.id === 'in' || p.id === 'start');
                                if (inPort?.id) {
                                    targetPortId = inPort.id;
                                } else {
                                    // 规则C: 找不到就随便找一个不是输出的端口
                                    const possibleInputs = ports.filter((p: any) =>
                                        !isOutputPort(p.id)
                                    );
                                    if (possibleInputs.length > 0) {
                                        targetPortId = possibleInputs[0].id!;
                                    } else if (ports.length > 0) {
                                        targetPortId = ports[0].id!; // 都没有的话只能强连
                                    }
                                }
                            }
                        }

                        // 正式把线的终点目标落实在端口上
                        edge.setTarget({ cell: newNode.id, port: targetPortId });

                        // 编程调用 setTarget 不触发 X6 的 edge:connected 交互事件，
                        // 手动触发以驱动占位符端口自增等逻辑
                        graph.trigger('edge:connected', {
                            edge,
                            isNew: true,
                            currentCell: newNode,
                            currentPort: targetPortId
                        });

                    } else {
                        // ═══════════════════════════════════════════════════════
                        // 反向反转 (Reverse / Edge Flipping):
                        // ═══════════════════════════════════════════════════════

                        // ── Step 1: 获取老节点信息（悬空线的 source 即是老节点的 in 端口）──
                        const oldSourceCell = edge.getSourceCell();
                        const oldSourcePortId = edge.getSourcePortId();

                        // 先将悬空反向的"旧边"彻底销毁，避免在 React Render 期间混淆 X6 的连线状态
                        // 从而导致新建节点卡片显示不出来的问题
                        graph.removeCell(sourceEdgeId);

                        if (!oldSourceCell || !oldSourcePortId) {
                            return;
                        }

                        // ── Step 2: 在新节点上找一个输出端口 ──
                        const outputPorts = ports.filter((p: any) => p.id && isOutputPort(p.id));

                        if (outputPorts.length === 0) {
                            // 新节点没有输出端口，无法反向连接
                            return;
                        }

                        // 优先选 'out' 端口，否则取第一个输出端口
                        const newOutputPortId = (outputPorts.find((p: any) => p.id === 'out') || outputPorts[0]).id!;

                        // ── Step 3: 反转连线方向，直接创建一条【全新的实线边】(Edge Flipping) ──
                        const newEdge = graph.addEdge({
                            source: { cell: newNode.id, port: newOutputPortId },
                            target: { cell: oldSourceCell.id, port: oldSourcePortId },
                            attrs: EDGE_CONFIG.attrs,
                            router: EDGE_CONFIG.router,
                            connector: EDGE_CONFIG.connector,
                            zIndex: 0,
                        });

                        // 手动触发 edge:connected 事件，驱动占位符端口等逻辑
                        graph.trigger('edge:connected', {
                            edge: newEdge,
                            isNew: true,
                            currentCell: oldSourceCell,
                            currentPort: oldSourcePortId
                        });
                    }
                }, 150); // 给 React 留出150ms渲染端口的时间
            }
        },
        [graph, sourceEdgeId, canvasPosition, direction, canCreate, onNodeCreated, onClose],
    );

    // ── 取消操作：删除悬空虚线边 ──
    const handleCancel = useCallback(() => {
        const edgeCell = graph.getCellById(sourceEdgeId);
        if (edgeCell) {
            graph.removeCell(edgeCell);
        }
        onClose();
    }, [graph, sourceEdgeId, onClose]);

    // ── 键盘导航 (上下箭头 + Enter) ──
    const handleKeyDown = useCallback(
        (e: React.KeyboardEvent) => {
            if (e.key === 'ArrowDown') {
                e.preventDefault();
                setHighlightIndex((i) => Math.min(i + 1, flatItems.length - 1));
            } else if (e.key === 'ArrowUp') {
                e.preventDefault();
                setHighlightIndex((i) => Math.max(i - 1, 0));
            } else if (e.key === 'Enter') {
                e.preventDefault();
                if (flatItems[highlightIndex]) {
                    handleSelect(flatItems[highlightIndex].type);
                }
            }
        },
        [flatItems, highlightIndex, handleSelect],
    );

    // ── 弹窗位置自适应（防止溢出屏幕） ──
    const [adjustedPos, setAdjustedPos] = useState({ x, y });
    useEffect(() => {
        const el = popoverRef.current;
        if (!el) return;
        const timer = requestAnimationFrame(() => {
            const rect = el.getBoundingClientRect();
            let newX = x;
            let newY = y;
            const padding = 12;
            if (rect.right > window.innerWidth - padding) {
                newX = x - (rect.right - window.innerWidth + padding);
            }
            if (rect.bottom > window.innerHeight - padding) {
                newY = y - rect.height - 16;
            }
            if (newX < padding) newX = padding;
            if (newY < padding) newY = padding;
            setAdjustedPos({ x: newX, y: newY });
        });
        return () => cancelAnimationFrame(timer);
    }, [x, y]);

    // ── 当前高亮项，用于滚动到视口 ──
    const listRef = useRef<HTMLDivElement>(null);
    useEffect(() => {
        if (listRef.current) {
            const el = listRef.current.querySelector(`[data-idx="${highlightIndex}"]`);
            el?.scrollIntoView({ block: 'nearest' });
        }
    }, [highlightIndex]);

    // ── 渲染 ──
    let flatIdx = -1;

    return (
        <div
            ref={popoverRef}
            onKeyDown={handleKeyDown}
            style={{
                position: 'fixed',
                left: adjustedPos.x,
                top: adjustedPos.y,
                zIndex: 10000,
                width: 280,
                maxHeight: 420,
                display: 'flex',
                flexDirection: 'column',
                background: 'rgba(255, 255, 255, 0.98)',
                backdropFilter: 'blur(12px)',
                borderRadius: 12,
                boxShadow: '0 8px 32px rgba(0,0,0,0.18), 0 0 0 1px rgba(0,0,0,0.06)',
                overflow: 'hidden',
                animation: 'quickAddFadeIn 0.15s ease-out',
            }}
        >
            {/* ── 头部搜索栏 ── */}
            <div
                style={{
                    padding: '12px 12px 8px',
                    borderBottom: '1px solid rgba(0,0,0,0.06)',
                    background: 'linear-gradient(to bottom, rgba(245,247,250,0.8), rgba(255,255,255,0.8))',
                }}
            >
                <div style={{ display: 'flex', alignItems: 'center', gap: 6, marginBottom: 8 }}>
                    <ThunderboltOutlined style={{ color: '#1677ff', fontSize: 13 }} />
                    <span style={{ fontSize: 12, fontWeight: 600, color: '#1f1f1f', letterSpacing: 0.5 }}>
                        {direction === 'reverse' ? '选择上游数据源' : '快捷添加节点'}
                    </span>
                    {/* 极性标签 */}
                    <span style={{
                        fontSize: 10,
                        color: direction === 'reverse' ? '#8b5cf6' : '#1677ff',
                        background: direction === 'reverse' ? 'rgba(139,92,246,0.08)' : 'rgba(22,119,255,0.06)',
                        padding: '1px 6px',
                        borderRadius: 4,
                        marginLeft: 'auto',
                        border: `1px solid ${direction === 'reverse' ? 'rgba(139,92,246,0.2)' : 'rgba(22,119,255,0.15)'}`,
                    }}>
                        {direction === 'reverse' ? '⬅ 反向' : '➡ 正向'}
                    </span>
                </div>
                <Input
                    ref={inputRef}
                    placeholder={direction === 'reverse' ? '搜索数据源节点...' : '搜索节点类型...'}
                    prefix={<SearchOutlined style={{ color: '#bfbfbf' }} />}
                    size="middle"
                    allowClear
                    value={search}
                    onChange={(e) => setSearch(e.target.value)}
                    style={{
                        borderRadius: 8,
                        background: '#fff',
                        border: '1px solid #e5e7eb',
                    }}
                    autoFocus
                />
            </div>

            {/* ── 节点列表区 ── */}
            <div
                ref={listRef}
                style={{
                    flex: 1,
                    overflowY: 'auto',
                    padding: '4px 0',
                    scrollbarWidth: 'thin',
                }}
            >
                {filteredGroups.length === 0 ? (
                    <Empty
                        image={Empty.PRESENTED_IMAGE_SIMPLE}
                        description="未找到匹配节点"
                        style={{ padding: '24px 0', margin: 0 }}
                    />
                ) : (
                    filteredGroups.map(([category, items]) => (
                        <div key={category}>
                            {/* 分组标题 */}
                            <div
                                style={{
                                    padding: '6px 14px 4px',
                                    fontSize: 10,
                                    fontWeight: 600,
                                    color: '#9ca3af',
                                    textTransform: 'uppercase',
                                    letterSpacing: 1,
                                    userSelect: 'none',
                                }}
                            >
                                {category}
                            </div>
                            {/* 节点项 */}
                            {items.map((reg) => {
                                flatIdx++;
                                const idx = flatIdx;
                                const isHighlighted = idx === highlightIndex;
                                const disabled = canCreate ? !canCreate(reg.type).ok : false;

                                return (
                                    <div
                                        key={reg.type}
                                        data-idx={idx}
                                        onClick={disabled ? undefined : () => handleSelect(reg.type)}
                                        onMouseEnter={() => setHighlightIndex(idx)}
                                        style={{
                                            display: 'flex',
                                            alignItems: 'center',
                                            gap: 10,
                                            padding: '7px 14px',
                                            cursor: disabled ? 'not-allowed' : 'pointer',
                                            opacity: disabled ? 0.4 : 1,
                                            background: isHighlighted
                                                ? 'rgba(22, 119, 255, 0.06)'
                                                : 'transparent',
                                            transition: 'background 0.1s',
                                        }}
                                    >
                                        {/* 色标 */}
                                        <div
                                            style={{
                                                width: 20,
                                                height: 20,
                                                borderRadius: 6,
                                                background: `${reg.color}18`,
                                                border: `1.5px solid ${reg.color}`,
                                                display: 'flex',
                                                alignItems: 'center',
                                                justifyContent: 'center',
                                                flexShrink: 0,
                                            }}
                                        >
                                            <div
                                                style={{
                                                    width: 8,
                                                    height: 8,
                                                    borderRadius: '50%',
                                                    background: reg.color,
                                                }}
                                            />
                                        </div>
                                        {/* 名称 */}
                                        <span
                                            style={{
                                                fontSize: 13,
                                                fontWeight: 500,
                                                color: '#1f1f1f',
                                                flex: 1,
                                                overflow: 'hidden',
                                                textOverflow: 'ellipsis',
                                                whiteSpace: 'nowrap',
                                            }}
                                        >
                                            {reg.label}
                                        </span>
                                        {/* 快捷键提示 */}
                                        {isHighlighted && !disabled && (
                                            <span
                                                style={{
                                                    fontSize: 10,
                                                    color: '#9ca3af',
                                                    fontFamily: 'monospace',
                                                    background: 'rgba(0,0,0,0.04)',
                                                    padding: '1px 4px',
                                                    borderRadius: 3,
                                                    border: '1px solid rgba(0,0,0,0.06)',
                                                }}
                                            >
                                                ↵
                                            </span>
                                        )}
                                    </div>
                                );
                            })}
                        </div>
                    ))
                )}
            </div>

            {/* ── CSS Keyframes (内联) ── */}
            <style>{`
                @keyframes quickAddFadeIn {
                    from {
                        opacity: 0;
                        transform: translateY(4px) scale(0.98);
                    }
                    to {
                        opacity: 1;
                        transform: translateY(0) scale(1);
                    }
                }
            `}</style>
        </div>
    );
}
