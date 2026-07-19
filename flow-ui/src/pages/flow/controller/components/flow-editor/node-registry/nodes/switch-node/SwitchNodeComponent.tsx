// ============================================================================
// SwitchNodeComponent.tsx — Switch 多路值匹配
// · 每条 case 可编辑名称；出口 case_<id> 始终存在并对齐卡片
// · 仅左上角 in:payload，无多余控制流 in
// ============================================================================

import React from 'react';
import { Button, Input, Typography } from 'antd';
import { DeleteOutlined, PlusOutlined } from '@ant-design/icons';
import { Node } from '@antv/x6';
import {
    BaseExpressionNode,
    BottomContentProps,
    HEADER_HEIGHT,
    ROW_HEIGHT,
    VAR_PADDING,
    COND_PADDING,
    MIN_WIDTH,
} from '../../shared/BaseExpressionNode';
import { NODE_FOOTER_SAFE_RIGHT } from '../../shared/useNodeSelection';
import {
    SwitchCaseItem,
    casePortId,
    createEmptyCase,
    normalizeCases,
} from './switchCases';

const { Text } = Typography;

const ICONS = {
    switch: (
        <svg viewBox="0 0 1024 1024" width="14" height="14" fill="currentColor">
            <path d="M512 128c-48 0-96 32-128 80L192 512l192 304c32 48 80 80 128 80s96-32 128-80l192-304-192-304c-32-48-80-80-128-80zm0 128c16 0 32 12 48 36l144 220-144 220c-16 24-32 36-48 36s-32-12-48-36L320 512l144-220c16-24 32-36 48-36z" />
        </svg>
    ),
    drag: (
        <svg viewBox="0 0 1024 1024" width="12" height="12" fill="currentColor">
            <path d="M384 256a64 64 0 1 1-128 0 64 64 0 0 1 128 0z m0 256a64 64 0 1 1-128 0 64 64 0 0 1 128 0z m0 256a64 64 0 1 1-128 0 64 64 0 0 1 128 0z m384-512a64 64 0 1 1-128 0 64 64 0 0 1 128 0z m0 256a64 64 0 1 1-128 0 64 64 0 0 1 128 0z m0 256a64 64 0 1 1-128 0 64 64 0 0 1 128 0z" />
        </svg>
    ),
};

/** 单行 Case：名称 == 匹配值 + 删除 */
export const CASE_ROW_HEIGHT = 30;
/** @deprecated 使用 CASE_ROW_HEIGHT */
export const CONDITION_CARD_HEIGHT = CASE_ROW_HEIGHT;
const SECTION_PAD_Y = 4;
const SECTION_PAD_X = 4;
/** Add case + Default 同一行 */
const FOOTER_ACTION_HEIGHT = 26;
export const SWITCH_EXPR_MIN_HEIGHT = 36;

export function switchFooterHeight(caseCount: number): number {
    return SECTION_PAD_Y * 2 + caseCount * CASE_ROW_HEIGHT + FOOTER_ACTION_HEIGHT;
}

export const SWITCH_LAYOUT = {
    width: MIN_WIDTH,
    headerHeight: HEADER_HEIGHT,
    caseRowHeight: CASE_ROW_HEIGHT,
    exprMinHeight: SWITCH_EXPR_MIN_HEIGHT,

    footerHeight(caseCount: number = 0) {
        return switchFooterHeight(caseCount);
    },

    totalHeight(varCount: number = 0, caseCount: number = 0) {
        return (
            HEADER_HEIGHT
            + varCount * ROW_HEIGHT
            + VAR_PADDING
            + SWITCH_EXPR_MIN_HEIGHT
            + COND_PADDING
            + this.footerHeight(caseCount)
        );
    },

    footerTop(totalHeight: number, caseCount: number = 0) {
        return totalHeight - this.footerHeight(caseCount);
    },

    casePortY(totalHeight: number, index: number, caseCount: number = 0) {
        const ft = this.footerTop(totalHeight, caseCount);
        return ft + SECTION_PAD_Y + index * CASE_ROW_HEIGHT + CASE_ROW_HEIGHT / 2;
    },

    defaultPortY(totalHeight: number, caseCount: number = 0) {
        const ft = this.footerTop(totalHeight, caseCount);
        return (
            ft
            + SECTION_PAD_Y
            + caseCount * CASE_ROW_HEIGHT
            + FOOTER_ACTION_HEIGHT / 2
        );
    },
};

function getCasesFromData(node: Node): SwitchCaseItem[] {
    return normalizeCases((node.getData() as any)?.cases);
}

function refreshEdges(node: Node, portId: string) {
    const graph = node.model?.graph;
    if (!graph) return;
    graph.getConnectedEdges(node).forEach((edge) => {
        const src = edge.getSource() as any;
        const tgt = edge.getTarget() as any;
        if (src?.port === portId || tgt?.port === portId) {
            (graph.findViewByCell(edge) as any)?.update();
        }
    });
}

function syncSwitchPorts(
    node: Node,
    size: { width: number; height: number },
    updateEdges: (portId: string) => void,
) {
    const cases = getCasesFromData(node);
    const caseCount = cases.length;
    const w = size.width;
    const h = size.height;
    const ports = node.getPorts();
    const existing = new Set(ports.map((p) => p.id));

    const ensurePort = (id: string, group: string, x: number, y: number) => {
        if (!existing.has(id)) {
            node.addPort({ id, group, args: { x, y, dx: 0 }, zIndex: 1 });
            existing.add(id);
        } else {
            const p = ports.find((port) => port.id === id);
            if (p?.group !== group) node.setPortProp(id, 'group', group);
            node.setPortProp(id, 'args', { x, y, dx: 0 });
            updateEdges(id);
        }
    };

    // 去掉多余控制流 in；总入口仅 in:payload（BaseExpressionNode 维护）
    if (existing.has('in')) {
        try {
            node.removePort('in');
            existing.delete('in');
        } catch {
            /* ignore */
        }
    }

    ensurePort('default', 'absolute-out-solid', w, SWITCH_LAYOUT.defaultPortY(h, caseCount));

    const wanted = new Set<string>();
    cases.forEach((c, idx) => {
        const portId = casePortId(c.id);
        wanted.add(portId);
        ensurePort(portId, 'absolute-out-solid', w, SWITCH_LAYOUT.casePortY(h, idx, caseCount));
    });

    ports.forEach((p) => {
        if (!p.id?.startsWith('case_')) return;
        if (wanted.has(p.id)) return;
        try {
            node.removePort(p.id);
        } catch {
            /* ignore */
        }
    });
}

const handlePortSync = (node: Node, size: { width: number; height: number }) => {
    syncSwitchPorts(node, size, (pid) => refreshEdges(node, pid));
};

const handleResize = (
    node: Node,
    nw: number,
    nh: number,
    updateEdges: (portId: string) => void,
) => {
    syncSwitchPorts(node, { width: nw, height: nh }, updateEdges);
};

const handlePortPositionSync = (
    node: Node,
    size: { width: number; height: number },
    updateEdges: (portId: string) => void,
) => {
    syncSwitchPorts(node, size, updateEdges);
};

interface SwitchConditionsProps extends BottomContentProps {
    cases: SwitchCaseItem[];
    footerHeight: number;
    onAddCase: () => void;
    onRemoveCase: (index: number) => void;
    onUpdateCase: (index: number, patch: Partial<Pick<SwitchCaseItem, 'name' | 'value'>>) => void;
    onReorderCases: (next: SwitchCaseItem[]) => void;
}

type CaseDragState = { index: number; startY: number; currentY: number };

const SwitchConditions: React.FC<SwitchConditionsProps> = ({
    cases,
    footerHeight,
    onAddCase,
    onRemoveCase,
    onUpdateCase,
    onReorderCases,
}) => {
    const [dragState, setDragState] = React.useState<CaseDragState | null>(null);

    const handleDragStart = React.useCallback(
        (index: number) => (e: React.MouseEvent) => {
            e.stopPropagation();
            e.preventDefault();
            setDragState({ index, startY: e.clientY, currentY: e.clientY });
        },
        [],
    );

    React.useEffect(() => {
        if (!dragState) return;
        const { startY } = dragState;

        const onMove = (e: MouseEvent) => {
            e.preventDefault();
            setDragState((p) => (p ? { ...p, currentY: e.clientY } : null));
            const delta = e.clientY - startY;
            if (delta > CASE_ROW_HEIGHT / 2 && dragState.index < cases.length - 1) {
                const next = [...cases];
                [next[dragState.index], next[dragState.index + 1]] = [
                    next[dragState.index + 1],
                    next[dragState.index],
                ];
                onReorderCases(next);
                setDragState({
                    index: dragState.index + 1,
                    startY: startY + CASE_ROW_HEIGHT,
                    currentY: e.clientY,
                });
            } else if (delta < -CASE_ROW_HEIGHT / 2 && dragState.index > 0) {
                const next = [...cases];
                [next[dragState.index], next[dragState.index - 1]] = [
                    next[dragState.index - 1],
                    next[dragState.index],
                ];
                onReorderCases(next);
                setDragState({
                    index: dragState.index - 1,
                    startY: startY - CASE_ROW_HEIGHT,
                    currentY: e.clientY,
                });
            }
        };

        const onUp = () => setDragState(null);

        document.addEventListener('mousemove', onMove);
        document.addEventListener('mouseup', onUp);
        return () => {
            document.removeEventListener('mousemove', onMove);
            document.removeEventListener('mouseup', onUp);
        };
    }, [dragState, cases, onReorderCases]);

    return (
        <div
            style={{
                height: footerHeight,
                position: 'relative',
                pointerEvents: 'auto',
                flexShrink: 0,
                padding: `${SECTION_PAD_Y}px ${SECTION_PAD_X}px`,
                paddingRight: NODE_FOOTER_SAFE_RIGHT,
                boxSizing: 'border-box',
                display: 'flex',
                flexDirection: 'column',
                borderTop: '1px solid #f0f0f0',
                background: '#fafafa',
            }}
        >
            {cases.map((c, idx) => {
                const isDragging = dragState?.index === idx;
                const transform =
                    isDragging && dragState
                        ? `translateY(${dragState.currentY - dragState.startY}px)`
                        : 'translateY(0)';

                return (
                    <div
                        key={c.id}
                        style={{
                            height: CASE_ROW_HEIGHT,
                            flexShrink: 0,
                            display: 'flex',
                            alignItems: 'center',
                            gap: 2,
                            padding: '0 2px',
                            boxSizing: 'border-box',
                            borderBottom: '1px solid #f0f0f0',
                            transform,
                            zIndex: isDragging ? 100 : 1,
                            position: 'relative',
                            backgroundColor: isDragging ? '#f9f0ff' : 'transparent',
                            transition: isDragging ? 'none' : 'background-color 0.15s',
                        }}
                        onMouseDown={(e) => e.stopPropagation()}
                        onClick={(e) => e.stopPropagation()}
                    >
                        <div
                            onMouseDown={handleDragStart(idx)}
                            title="拖拽排序"
                            style={{
                                color: isDragging ? '#722ed1' : '#bfbfbf',
                                display: 'flex',
                                alignItems: 'center',
                                cursor: isDragging ? 'grabbing' : 'grab',
                                padding: '2px 0',
                                flexShrink: 0,
                                userSelect: 'none',
                            }}
                        >
                            {ICONS.drag}
                        </div>
                        <Input
                            size="small"
                            value={c.name}
                            placeholder={`Case ${idx + 1}`}
                            variant="borderless"
                            style={{
                                width: 64,
                                flexShrink: 0,
                                fontWeight: 600,
                                fontSize: 11,
                                padding: 0,
                                color: '#262626',
                            }}
                            onChange={(e) => onUpdateCase(idx, { name: e.target.value })}
                        />
                        <Text type="secondary" style={{ fontSize: 10, fontFamily: 'monospace', flexShrink: 0, lineHeight: 1 }}>
                            ==
                        </Text>
                        <Input
                            size="small"
                            value={c.value}
                            placeholder="匹配值"
                            variant="borderless"
                            style={{
                                flex: 1,
                                minWidth: 0,
                                fontFamily: 'ui-monospace, SFMono-Regular, Menlo, monospace',
                                fontSize: 11,
                                padding: '0 4px',
                                background: '#fff',
                                borderRadius: 3,
                                height: 22,
                            }}
                            onChange={(e) => onUpdateCase(idx, { value: e.target.value })}
                        />
                        <Button
                            type="text"
                            size="small"
                            icon={<DeleteOutlined />}
                            danger
                            onClick={() => onRemoveCase(idx)}
                            style={{ width: 22, height: 22, minWidth: 22, padding: 0, flexShrink: 0 }}
                        />
                    </div>
                );
            })}

            <div
                style={{
                    height: FOOTER_ACTION_HEIGHT,
                    flexShrink: 0,
                    display: 'flex',
                    alignItems: 'center',
                    justifyContent: 'space-between',
                    boxSizing: 'border-box',
                }}
                onMouseDown={(e) => e.stopPropagation()}
            >
                <Button
                    type="link"
                    size="small"
                    icon={<PlusOutlined />}
                    onClick={onAddCase}
                    style={{ padding: 0, height: 22, fontSize: 12 }}
                >
                    Add case
                </Button>
                <Text style={{ fontSize: 11, color: '#722ed1', fontWeight: 500, paddingRight: 2 }}>Default</Text>
            </div>
        </div>
    );
};

export const SwitchNodeComponent = ({ node }: { node: Node }) => {
    const [data, setData] = React.useState<any>(() => node.getData());

    React.useEffect(() => {
        const onD = () => setData({ ...node.getData() });
        node.on('change:data', onD);
        return () => {
            node.off('change:data', onD);
        };
    }, [node]);

    // 规范化：仅对象 cases；空则补一条 Case 1
    React.useEffect(() => {
        const d = node.getData() as any;
        const raw = d?.cases;
        const normalized = normalizeCases(raw);
        const next = normalized.length > 0 ? normalized : [createEmptyCase(0)];
        const needsWrite =
            !Array.isArray(raw)
            || raw.length !== next.length
            || next.some((c, i) => {
                const item = raw[i];
                return !item
                    || typeof item !== 'object'
                    || item.id !== c.id
                    || item.name !== c.name
                    || (item.value ?? item.match ?? '') !== c.value;
            });
        if (needsWrite) {
            node.setData(
                { ...d, cases: next, themeColor: d?.themeColor || 'purple' },
                { overwrite: true },
            );
        }
    }, [node]);

    const cases = normalizeCases(data?.cases);
    const footerHeight = SWITCH_LAYOUT.footerHeight(cases.length);

    const persistCases = React.useCallback(
        (next: SwitchCaseItem[]) => {
            node.setData({ ...node.getData(), cases: next }, { overwrite: true });
        },
        [node],
    );

    const onAddCase = React.useCallback(() => {
        const cur = getCasesFromData(node);
        persistCases([...cur, createEmptyCase(cur.length)]);
    }, [node, persistCases]);

    const onRemoveCase = React.useCallback(
        (index: number) => {
            const cur = getCasesFromData(node);
            const removed = cur[index];
            persistCases(cur.filter((_, i) => i !== index));
            if (removed?.id) {
                const portId = casePortId(removed.id);
                if (node.hasPort(portId)) {
                    try {
                        node.removePort(portId);
                    } catch {
                        /* ignore */
                    }
                }
            }
        },
        [node, persistCases],
    );

    const onUpdateCase = React.useCallback(
        (index: number, patch: Partial<Pick<SwitchCaseItem, 'name' | 'value'>>) => {
            const cur = getCasesFromData(node);
            const next = cur.map((c, i) => (i === index ? { ...c, ...patch } : c));
            persistCases(next);
        },
        [node, persistCases],
    );

    const onReorderCases = React.useCallback(
        (next: SwitchCaseItem[]) => {
            persistCases(next);
        },
        [persistCases],
    );

    React.useEffect(() => {
        syncSwitchPorts(node, node.getSize(), (pid) => refreshEdges(node, pid));
    }, [node, cases.map((c) => `${c.id}:${c.name}:${c.value}`).join('|'), footerHeight]);

    // 旧版双行卡片节点过高时，收回到紧凑高度（保留用户略微拉高表达式区的空间）
    React.useEffect(() => {
        const inputs = (node.getData() as any)?.inputs;
        const varCount = inputs && typeof inputs === 'object' ? Object.keys(inputs).length : 0;
        const target = SWITCH_LAYOUT.totalHeight(varCount, cases.length);
        const s = node.getSize();
        if (s.height > target + 48) {
            node.resize(Math.max(s.width, MIN_WIDTH), target);
        }
    }, [node, cases.length, footerHeight]);

    return (
        <BaseExpressionNode
            node={node}
            titleIcon={ICONS.switch}
            titleText="Switch"
            footerHeight={footerHeight}
            expressionField="expression"
            expressionPlaceholder="匹配表达式，如 status 或 r"
            minExpressionHeight={SWITCH_EXPR_MIN_HEIGHT}
            onPortSync={handlePortSync}
            onResize={handleResize}
            onPortPositionSync={handlePortPositionSync}
            bottomContent={(props: BottomContentProps) => (
                <SwitchConditions
                    {...props}
                    cases={cases}
                    footerHeight={footerHeight}
                    onAddCase={onAddCase}
                    onRemoveCase={onRemoveCase}
                    onUpdateCase={onUpdateCase}
                    onReorderCases={onReorderCases}
                />
            )}
        />
    );
};

export default SwitchNodeComponent;
