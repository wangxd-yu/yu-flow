// ============================================================================
// DslPalette.tsx — V3.2 组件面板：从 node-registry 动态读取节点列表
// 接收 graphRef (MutableRefObject) 而非 graph 值，确保拖拽始终能拿到最新 graph
// ============================================================================

import React, { useCallback, useMemo } from 'react';
import { Collapse, Tooltip, message, theme } from 'antd';
import { InfoCircleOutlined } from '@ant-design/icons';
import type { Graph } from '@antv/x6';
import type { DslNodeType } from '../types';
import { getRegistrationsByCategory } from '../node-registry';
import {
    isPaletteNodeAllowed,
    resolveEditorContext,
    type FlowEditorContext,
} from '../editorContext';

export type DslPaletteProps = {
    graphRef: React.MutableRefObject<Graph | null>;
    onAddNode: (type: DslNodeType, position?: { x: number; y: number }) => void;
    canCreate?: (type: DslNodeType) => { ok: boolean; reason?: string };
    /** 资产上下文：隐藏异类入口节点 */
    editorContext?: FlowEditorContext;
};

type PaletteItem = { type: DslNodeType; label: string; color: string };

// ── 排序配置 ──
// 分组排序权重 (越大越靠前)
const CATEGORY_WEIGHTS: Record<string, number> = {
    '基础节点': 100,
    '逻辑节点': 90,
    '循环节点': 85,
    '数据节点': 80,
    '调用节点': 70,
    '外部资源': 60,
};

// 节点排序权重 (越大越靠前)
const NODE_WEIGHTS: Partial<Record<DslNodeType, number>> = {
    // 基础节点
    request: 100,
    response: 90,
    // 逻辑节点
    if: 100,
    switch: 90,
    evaluate: 80,
    delay: 70,
    errorHandler: 60,
    // 循环节点
    forEach: 100,
    for: 90,
    collect: 80,
    parallel: 70,
    // 数据节点
    record: 100,
    template: 90,
    systemVar: 70,
    // 调用节点
    httpRequest: 100,
    api: 95,
    database: 80,
};

export default function DslPalette({
    graphRef,
    onAddNode,
    canCreate,
    editorContext,
}: DslPaletteProps) {
    const { token } = theme.useToken();
    const resolvedContext = useMemo(
        () => resolveEditorContext(editorContext),
        [editorContext],
    );

    // 从 node-registry 读取面板分组，并按资产上下文过滤入口节点
    const paletteGroups: [string, PaletteItem[]][] = getRegistrationsByCategory()
        .map(
            ([category, regs]) => {
                const sortedItems = regs
                    .filter((r) => isPaletteNodeAllowed(r.type, resolvedContext))
                    .map((r) => ({ type: r.type, label: r.label, color: r.color, sortOrder: r.sortOrder }))
                    .sort((a, b) => {
                        const weightA = a.sortOrder ?? NODE_WEIGHTS[a.type] ?? 0;
                        const weightB = b.sortOrder ?? NODE_WEIGHTS[b.type] ?? 0;
                        return weightB - weightA;
                    });

                return [category, sortedItems] as [string, PaletteItem[]];
            }
        )
        .filter(([, list]) => list.length > 0)
        // 组间排序
        .sort((a, b) => {
            const weightA = CATEGORY_WEIGHTS[a[0]] ?? 0;
            const weightB = CATEGORY_WEIGHTS[b[0]] ?? 0;
            if (weightA !== weightB) {
                return weightB - weightA;
            }
            return a[0].localeCompare(b[0]); // 权重相同则按名称字典序
        });


    const tryAdd = useCallback(
        (type: DslNodeType, pos?: { x: number; y: number }) => {
            if (canCreate) {
                const allow = canCreate(type);
                if (!allow.ok) { message.warning(allow.reason || '无法创建该节点'); return; }
            }
            onAddNode(type, pos);
        },
        [canCreate, onAddNode],
    );

    const handleDoubleClick = useCallback(
        (type: DslNodeType) => {
            const g = graphRef.current;
            if (g) {
                const area = g.getGraphArea();
                const x = (area?.x ?? 0) + (area?.width ?? 600) / 2 - 80;
                const y = (area?.y ?? 0) + (area?.height ?? 400) / 2 - 30;
                tryAdd(type, { x: Math.round(x), y: Math.round(y) });
            } else {
                tryAdd(type);
            }
        },
        [graphRef, tryAdd],
    );

    const handleDragStart = useCallback(
        (type: DslNodeType, color: string, label: string) => (e: React.MouseEvent) => {
            e.preventDefault();
            const ghost = document.createElement('div');
            ghost.style.cssText = `
                position:fixed;z-index:9999;pointer-events:none;
                padding:6px 12px;background:${color}22;border:1.5px solid ${color};
                border-radius:6px;font-size:11px;font-weight:500;color:${color};
                white-space:nowrap;box-shadow:0 4px 12px rgba(0,0,0,.15);
                transform:translate(-50%,-50%);opacity:.9;
            `;
            ghost.textContent = label;
            document.body.appendChild(ghost);
            ghost.style.left = e.clientX + 'px';
            ghost.style.top = e.clientY + 'px';

            const onMove = (ev: MouseEvent) => {
                ghost.style.left = ev.clientX + 'px';
                ghost.style.top = ev.clientY + 'px';
            };
            const onUp = (ev: MouseEvent) => {
                document.removeEventListener('mousemove', onMove);
                document.removeEventListener('mouseup', onUp);
                document.body.removeChild(ghost);

                const g = graphRef.current; // 始终从 ref 取最新
                if (!g) return;
                const container = (g as any).container as HTMLElement;
                if (!container) return;
                const rect = container.getBoundingClientRect();
                if (ev.clientX >= rect.left && ev.clientX <= rect.right && ev.clientY >= rect.top && ev.clientY <= rect.bottom) {
                    const local = g.clientToLocal(ev.clientX, ev.clientY);
                    tryAdd(type, { x: Math.round(local.x - 60), y: Math.round(local.y - 30) });
                }
            };
            document.addEventListener('mousemove', onMove);
            document.addEventListener('mouseup', onUp);
        },
        [graphRef, tryAdd],
    );

    return (
        <div style={{ width: '100%', height: '100%', background: '#fff', overflow: 'auto', display: 'flex', flexDirection: 'column' }}>
            <div style={{ padding: '12px 16px 8px', fontWeight: 600, fontSize: 14, color: '#1f1f1f', borderBottom: '1px solid #f0f0f0' }}>
                组件面板
            </div>
            <Collapse
                ghost
                defaultActiveKey={paletteGroups.map(([k]) => k)}
                items={paletteGroups.map(([category, list]) => ({
                    key: category,
                    label: <span style={{ fontWeight: 600, fontSize: 12 }}>{category}</span>,
                    children: (
                        <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 8 }}>
                            {list.map((item) => (
                                <div
                                    key={item.type}
                                    style={{ border: `1.5px solid ${item.color}66`, borderRadius: 6, padding: '8px 6px', cursor: 'grab', userSelect: 'none', background: `${item.color}08`, textAlign: 'center', fontSize: 11, position: 'relative', transition: 'all 0.2s' }}
                                    onMouseEnter={(e) => { const d = e.currentTarget; d.style.borderColor = item.color; d.style.boxShadow = `0 2px 8px ${item.color}22`; d.style.transform = 'translateY(-1px)'; }}
                                    onMouseLeave={(e) => { const d = e.currentTarget; d.style.borderColor = item.color + '66'; d.style.boxShadow = 'none'; d.style.transform = 'translateY(0)'; }}
                                    onDoubleClick={() => handleDoubleClick(item.type)}
                                    onMouseDown={handleDragStart(item.type, item.color, item.label)}
                                >
                                    <div style={{ width: 8, height: 8, borderRadius: '50%', background: item.color, margin: '0 auto 4px' }} />
                                    <div style={{ overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap', fontWeight: 500 }}>{item.label}</div>
                                    <div style={{ position: 'absolute', top: 2, right: 2 }}>
                                        <Tooltip title="拖拽到画布或双击添加" placement="right">
                                            <InfoCircleOutlined style={{ fontSize: 10, color: token.colorTextSecondary }} />
                                        </Tooltip>
                                    </div>
                                </div>
                            ))}
                        </div>
                    ),
                }))}
            />
        </div>
    );
}
