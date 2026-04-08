// ============================================================================
// CollectNodeComponent.tsx — Collect (Gather 汇聚屏障) 节点  V5.1
//
// 修复：
//   · 实时配对检测（反向查找上游 For 节点，不依赖 autoBindLoopNodes 的 data 注入）
//   · Badge 放在 addon slot，避免被 overflow:hidden 裁剪
//   · 保留原生 outlineCss 边框，孤立状态仅当确实有边才判定红框
//   · 配对成功后用 pairColor 渲染右侧装饰条 + 顶部 Badge
// ============================================================================

import React from 'react';
import { Tooltip } from 'antd';
import { Node } from '@antv/x6';
import {
    useNodeSelection,
    getNodeTheme,
    NodeHeader,
    NodeWrapper,
    type NodeTheme,
} from '../../shared/useNodeSelection';

export const COLLECT_COLOR = '#0ea5e9';

// ── 配对安全冷色板 ──────────────────────────────────────────────────────────
const PAIR_COLORS = [
    '#6366f1', '#3b82f6', '#0ea5e9', '#8b5cf6',
    '#a855f7', '#d946ef', '#06b6d4', '#14b8a6',
];

// ── 布局常量 —— 严格两行，每行高 30px ─────────────────────────────────────
export const COLLECT_LAYOUT = {
    width: 150,
    height: 100,   // 40(header) + 60(body: row1=30 + row2=30)
    headerHeight: 40,
    portY: {
        item: 55,   // 行1 中心
        list: 55,   // 行1 中心
        finish: 85, // 行2 中心
    },
} as const;

const ICON_FUNNEL = (
    <svg viewBox="0 0 1024 1024" width="14" height="14" fill="currentColor">
        <path d="M880.1 154H143.9a8 8 0 00-6.8 12.2L468 638.3V864a8 8 0 0013.6 5.7l96-88a8 8 0 002.4-5.7V638.3l331-472.1A8 8 0 00880.1 154z" />
    </svg>
);

function resolveTheme(themeColor: string): NodeTheme {
    const t = getNodeTheme(themeColor);
    if (t.primary !== '#595959' || themeColor === '#595959') return t;
    return {
        primary: themeColor,
        headerBg: `${themeColor}22`,
        headerBorder: `${themeColor}66`,
        titleColor: themeColor,
        bodyBg: '#ffffff',
    };
}

// ── 实时配对检测 Hook ─────────────────────────────────────────────────────────
// Collect 通过反向查找上游 For 节点来判断配对状态
type CollectPairStatus = 'paired' | 'orphan' | 'fresh';
interface CollectPairState {
    status: CollectPairStatus;
    pairIndex: number | null;
    pairColor: string;
    forNodeId: string | null;
}

// ── BFS 辅助：从 forNode 下游搜索，判断能否到达指定 collectId（scopeDepth 感知）──
function _bfsCanReachCollect(graph: any, forNode: Node, collectId: string): boolean {
    type Q = { nodeId: string; scopeDepth: number };
    const visited = new Set<string>();
    const queue: Q[] = [];

    // 从 For 的出边入队（排除 start 控制流）
    const initEdges = (graph.getOutgoingEdges(forNode) || []).filter((e: any) => {
        const port = (e.getSource() as any)?.port as string | undefined;
        return port !== 'start';
    });
    for (const e of initEdges) {
        const tid = (e as any).getTargetCellId();
        if (tid && !visited.has(tid)) { visited.add(tid); queue.push({ nodeId: tid, scopeDepth: 0 }); }
    }

    while (queue.length > 0) {
        const { nodeId: nid, scopeDepth } = queue.shift()!;
        const cell = graph.getCellById(nid);
        if (!cell?.isNode()) continue;
        const n = cell as Node;
        const nType = (n.getData() as any)?.__dslType;

        if (nType === 'collect') {
            if (scopeDepth === 0 && n.id === collectId) return true;
            if (scopeDepth === 0) continue; // 不是我们要找的 Collect，停止此路径
            // 子循环的 Collect，退出一层后继续
            const oe = graph.getOutgoingEdges(n) || [];
            for (const e of oe) { const t = (e as any).getTargetCellId(); if (t && !visited.has(t)) { visited.add(t); queue.push({ nodeId: t, scopeDepth: scopeDepth - 1 }); } }
            continue;
        }
        if (nType === 'for') {
            const oe = (graph.getOutgoingEdges(n) || []).filter((e: any) => (e.getSource() as any)?.port !== 'start');
            for (const e of oe) { const t = (e as any).getTargetCellId(); if (t && !visited.has(t)) { visited.add(t); queue.push({ nodeId: t, scopeDepth: scopeDepth + 1 }); } }
            continue;
        }
        // 普通节点
        const oe = (graph.getOutgoingEdges(n) || []).filter((e: any) => {
            const p = (e.getSource() as any)?.port as string | undefined;
            return p !== 'start' && p !== 'finish';
        });
        for (const e of oe) { const t = (e as any).getTargetCellId(); if (t && !visited.has(t)) { visited.add(t); queue.push({ nodeId: t, scopeDepth }); } }
    }
    return false;
}

function useCollectPairState(node: Node): CollectPairState {
    const [state, setState] = React.useState<CollectPairState>({
        status: 'fresh', pairIndex: null, pairColor: COLLECT_COLOR, forNodeId: null,
    });

    React.useEffect(() => {
        const graph = node.model?.graph;
        if (!graph) return;

        const compute = () => {
            // 1. 收集所有 For 节点，按 ID（含创建时间戳）排序，保证序号稳定递增
            const forNodes = graph.getNodes()
                .filter((n) => (n.getData() as any)?.__dslType === 'for')
                .sort((a, b) => a.id.localeCompare(b.id));

            // 2. 对每个 For 做下游 BFS，看是否能走到本 Collect（scopeDepth 感知）
            let pairedFor: Node | null = null;
            for (const fn of forNodes) {
                if (_bfsCanReachCollect(graph, fn, node.id)) {
                    pairedFor = fn;
                    break; // 取第一个命中的（排序靠前的 For 优先）
                }
            }

            const myData = node.getData() as any;

            if (pairedFor) {
                const idx = forNodes.indexOf(pairedFor);
                const forData = pairedFor.getData() as any;
                const basePairColor = PAIR_COLORS[Math.max(0, idx) % PAIR_COLORS.length];
                const finalPairColor = myData?.themeColor || forData?.themeColor || basePairColor;

                setState({
                    status: 'paired',
                    pairIndex: idx >= 0 ? idx + 1 : 1,
                    pairColor: finalPairColor,
                    forNodeId: pairedFor.id,
                });
                return;
            }

            // 3. 未找到上游 For：有入边 → 孤立，无入边 → 新建（不报警）
            const hasInEdges = (graph.getIncomingEdges(node) || []).length > 0;
            setState({
                status: hasInEdges ? 'orphan' : 'fresh',
                pairIndex: null,
                pairColor: myData?.themeColor || COLLECT_COLOR,
                forNodeId: null,
            });
        };

        compute();

        // ── 延迟补偿：adapter.ts 的边在 100ms 后添加，forceEdgeRefresh 在 200~900ms ──
        const t1 = setTimeout(compute, 300);
        const t2 = setTimeout(compute, 800);
        const t3 = setTimeout(compute, 1500);

        graph.on('node:added', compute);
        graph.on('node:removed', compute);
        graph.on('edge:added', compute);
        graph.on('edge:removed', compute);
        graph.on('edge:connected', compute);
        graph.on('edge:change:source', compute);
        graph.on('edge:change:target', compute);
        graph.on('cell:change:data', compute);
        graph.on('cell:added', compute);
        return () => {
            clearTimeout(t1); clearTimeout(t2); clearTimeout(t3);
            graph.off('node:added', compute);
            graph.off('node:removed', compute);
            graph.off('edge:added', compute);
            graph.off('edge:removed', compute);
            graph.off('edge:connected', compute);
            graph.off('edge:change:source', compute);
            graph.off('edge:change:target', compute);
            graph.off('cell:change:data', compute);
            graph.off('cell:added', compute);
        };
    }, [node]);

    return state;
}

// ── CollectNodeComponent ──────────────────────────────────────────────────────
export const CollectNodeComponent = ({ node }: { node: Node }) => {
    const [data, setData] = React.useState<any>(node.getData());

    React.useEffect(() => {
        const onD = () => setData({ ...node.getData() });
        node.on('change:data', onD);
        return () => { node.off('change:data', onD); };
    }, [node]);

    const { status, pairIndex, pairColor, forNodeId } = useCollectPairState(node);
    const isPaired = status === 'paired';
    const isOrphan = status === 'orphan';

    // 配对成功时用 pairColor 作主题色，孤立则恢复默认灰色边框避免报警暗示
    const effectiveColor = isPaired ? pairColor : '#8c8c8c';
    const theme = resolveTheme(effectiveColor);

    const { outlineCss, selected } = useNodeSelection(node, {
        defaultColor: theme.primary,
        selectedColor: theme.primary,
        borderRadius: 10,
    });

    const nodeLabel = data?.__label || 'Collect';

    // ── 悬停联动 ────────────────────────────────────────────────────────
    const handleMouseEnter = React.useCallback(() => {
        if (!forNodeId) return;
        const graph = node.model?.graph;
        if (!graph) return;
        graph.trigger('loop-pair:hover-enter', { sourceId: node.id, peerId: forNodeId });
    }, [node, forNodeId]);

    const handleMouseLeave = React.useCallback(() => {
        if (!forNodeId) return;
        const graph = node.model?.graph;
        if (!graph) return;
        graph.trigger('loop-pair:hover-leave', { sourceId: node.id, peerId: forNodeId });
    }, [node, forNodeId]);

    const [peerHighlight, setPeerHighlight] = React.useState(false);
    React.useEffect(() => {
        const graph = node.model?.graph;
        if (!graph) return;
        const onEnter = ({ peerId }: any) => { if (peerId === node.id) setPeerHighlight(true); };
        const onLeave = ({ peerId }: any) => { if (peerId === node.id) setPeerHighlight(false); };
        graph.on('loop-pair:hover-enter', onEnter);
        graph.on('loop-pair:hover-leave', onLeave);
        return () => {
            graph.off('loop-pair:hover-enter', onEnter);
            graph.off('loop-pair:hover-leave', onLeave);
        };
    }, [node]);

    // ── 内联标签 (Inline Tag) ──
    let inlineTag: React.ReactNode = null;
    if (isPaired && pairIndex != null) {
        inlineTag = (
            <Tooltip title="已配对分发点" placement="top">
                <div style={{
                    display: 'inline-flex', alignItems: 'center', justifyContent: 'center',
                    height: 20, padding: '0 8px', marginLeft: 8,
                    borderRadius: 6,
                    backgroundColor: `${pairColor}22`,
                    color: pairColor,
                    fontSize: 11, fontWeight: 600,
                    border: `1px solid ${pairColor}44`,
                    userSelect: 'none', cursor: 'default',
                }}>Loop #{pairIndex}</div>
            </Tooltip>
        );
    } else if (isOrphan) {
        inlineTag = (
            <Tooltip title="孤立的汇聚点，请确保将其连接到某个 For 循环分支中" placement="top">
                <div style={{
                    display: 'inline-flex', alignItems: 'center', justifyContent: 'center',
                    height: 20, padding: '0 8px', marginLeft: 8,
                    borderRadius: 6,
                    backgroundColor: '#f3f4f6',
                    color: '#6b7280',
                    fontSize: 11, fontWeight: 600,
                    border: '1px solid #d1d5db',
                    userSelect: 'none', cursor: 'default',
                }}>未绑定</div>
            </Tooltip>
        );
    }

    return (
        <NodeWrapper
            node={node}
            selected={selected}
            themeColor={theme.primary}
            outlineCss={outlineCss}
            backgroundColor={theme.bodyBg}
            extraStyle={{
                borderRadius: 10,
                boxShadow: peerHighlight && isPaired
                    ? `0 0 0 3px ${pairColor}55, 0 2px 12px ${pairColor}33`
                    : undefined,
                transition: 'box-shadow .25s ease',
            }}
        >
            <NodeHeader
                icon={ICON_FUNNEL}
                title={nodeLabel}
                theme={theme}
                height={COLLECT_LAYOUT.headerHeight}
                extra={inlineTag}
                onTitleChange={(t) => node.setData({ ...node.getData(), __label: t })}
            />

            {/* ── 端口标签层：严格两行 Flex ── */}
            <div
                style={{ flex: 1, display: 'flex', flexDirection: 'column' }}
                onMouseEnter={handleMouseEnter}
                onMouseLeave={handleMouseLeave}
            >
                {/* 行1：数据流 item ↔ list */}
                <div style={{
                    display: 'flex', justifyContent: 'space-between', alignItems: 'center',
                    height: 30, paddingLeft: 10, paddingRight: 10,
                }}>
                    <span style={{ fontSize: 11, color: '#595959', userSelect: 'none', pointerEvents: 'none', lineHeight: 1, display: 'flex', alignItems: 'center', gap: 4 }}>
                        <span style={{ opacity: 0.6 }}>?</span>
                        Item
                    </span>
                    <span style={{ fontSize: 11, color: '#595959', userSelect: 'none', pointerEvents: 'none', lineHeight: 1, display: 'flex', alignItems: 'center', gap: 4 }}>
                        List
                        <span style={{ opacity: 0.6, fontSize: 12 }}>[ ]</span>
                    </span>
                </div>

                {/* 行2：控制流 空 ↔ finish */}
                <div style={{
                    display: 'flex', justifyContent: 'space-between', alignItems: 'center',
                    height: 30, paddingLeft: 10, paddingRight: 10,
                }}>
                    <span />
                    <span style={{ fontSize: 10, color: '#8b5cf6', userSelect: 'none', pointerEvents: 'none', lineHeight: 1, display: 'flex', alignItems: 'center', gap: 4 }}>
                        Finish
                        <span style={{ opacity: 0.8, fontSize: 12 }}>·))</span>
                    </span>
                </div>
            </div>
        </NodeWrapper>
    );
};

export default CollectNodeComponent;
