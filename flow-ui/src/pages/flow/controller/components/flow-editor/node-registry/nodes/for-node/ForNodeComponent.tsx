// ============================================================================
// ForNodeComponent.tsx — For (Scatter 分发) 节点  V5.1
//
// 修复：
//   · 实时配对检测（不依赖 autoBindLoopNodes 的 save-time 注入）
//   · Badge 放在 NodeWrapper 外层，避免被 overflow:hidden 裁剪
//   · 保留原生 outlineCss 边框，extraStyle 不覆盖 border
//   · 配对成功后用 pairColor 渲染左侧装饰条 + 顶部 Badge
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

export const FOR_COLOR = '#7c3aed';

// ── 配对安全冷色板 ──────────────────────────────────────────────────────────
const PAIR_COLORS = [
    '#6366f1', '#3b82f6', '#0ea5e9', '#8b5cf6',
    '#a855f7', '#d946ef', '#06b6d4', '#14b8a6',
];

// ── 布局常量 —— 严格两行，每行高 30px ─────────────────────────────────────
export const FOR_LAYOUT = {
    width: 160,
    height: 100,   // 40(header) + 60(body: row1=30 + row2=30)
    headerHeight: 40,
    portY: {
        list: 55,   // 行1 中心：header(40) + 15
        item: 55,   // 行1 中心
        start: 85,  // 行2 中心：header(40) + 30 + 15
    },
} as const;

const ICON_LOOP = (
    <svg viewBox="0 0 1024 1024" width="14" height="14" fill="currentColor">
        <path d="M289.6 237.6C349.6 181.6 428.8 150.4 512 150.4c172.8 0 313.6 140.8 313.6 313.6v16H928l-128 160-128-160h108.8V464c0-148-120-268-268.8-268-73.6 0-139.2 28.8-188.8 76.8L289.6 237.6zM734.4 786.4C674.4 842.4 595.2 873.6 512 873.6c-172.8 0-313.6-140.8-313.6-313.6v-16H96l128-160 128 160H243.2V560c0 148 120 268 268.8 268 73.6 0 139.2-28.8 188.8-76.8l44.8 35.2z" />
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
// 通过监听 graph 事件（节点增删 + 边增删 + data变化），实时计算配对序号与颜色
interface PairState {
    pairIndex: number | null;   // 1-based 序号
    pairColor: string;          // 配对色
    collectNodeId: string | null;
}

function useForPairState(node: Node): PairState {
    const [state, setState] = React.useState<PairState>({
        pairIndex: null, pairColor: FOR_COLOR, collectNodeId: null,
    });

    React.useEffect(() => {
        const graph = node.model?.graph;
        if (!graph) return;

        const compute = () => {
            // 1. 收集所有 For 节点，按 ID（含创建时间戳）排序，保证序号稳定递增
            const forNodes = graph.getNodes()
                .filter((n) => (n.getData() as any)?.__dslType === 'for')
                .sort((a, b) => a.id.localeCompare(b.id));

            const myData = node.getData() as any;
            const myIndex = forNodes.findIndex((n) => n.id === node.id);
            if (myIndex < 0) { setState({ pairIndex: null, pairColor: myData?.themeColor || FOR_COLOR, collectNodeId: null }); return; }

            const pairIndex = myIndex + 1;
            const pairColor = PAIR_COLORS[myIndex % PAIR_COLORS.length];

            // 2. 实时 BFS 下游搜索配对 Collect（scopeDepth 感知嵌套循环）
            type Q = { nodeId: string; scopeDepth: number };
            const visited = new Set<string>();
            const queue: Q[] = [];

            // 从 For 的所有出边入队（排除 start 控制流）
            const initEdges = (graph.getOutgoingEdges(node) || []).filter((e) => {
                const port = (e.getSource() as any)?.port as string | undefined;
                return port !== 'start';
            });
            for (const e of initEdges) {
                const tid = e.getTargetCellId();
                if (tid && !visited.has(tid)) { visited.add(tid); queue.push({ nodeId: tid, scopeDepth: 0 }); }
            }

            let foundCollectId: string | null = null;
            let foundCollectNode: Node | null = null;
            while (queue.length > 0) {
                const { nodeId: nid, scopeDepth } = queue.shift()!;
                const cell = graph.getCellById(nid);
                if (!cell?.isNode()) continue;
                const n = cell as Node;
                const nType = (n.getData() as any)?.__dslType;

                if (nType === 'collect') {
                    if (scopeDepth === 0) { foundCollectId = n.id; foundCollectNode = n; break; }
                    // 子循环的 Collect，退出一层后继续
                    const oe = graph.getOutgoingEdges(n) || [];
                    for (const e of oe) { const t = e.getTargetCellId(); if (t && !visited.has(t)) { visited.add(t); queue.push({ nodeId: t, scopeDepth: scopeDepth - 1 }); } }
                    continue;
                }
                if (nType === 'for') {
                    // 嵌套 For，深度 +1
                    const oe = (graph.getOutgoingEdges(n) || []).filter((e) => (e.getSource() as any)?.port !== 'start');
                    for (const e of oe) { const t = e.getTargetCellId(); if (t && !visited.has(t)) { visited.add(t); queue.push({ nodeId: t, scopeDepth: scopeDepth + 1 }); } }
                    continue;
                }
                // 普通节点，继续向下
                const oe = (graph.getOutgoingEdges(n) || []).filter((e) => {
                    const p = (e.getSource() as any)?.port as string | undefined;
                    return p !== 'start' && p !== 'finish';
                });
                for (const e of oe) { const t = e.getTargetCellId(); if (t && !visited.has(t)) { visited.add(t); queue.push({ nodeId: t, scopeDepth }); } }
            }

            const collectData = foundCollectNode ? (foundCollectNode as Node).getData() as any : null;
            const finalPairColor = myData?.themeColor || collectData?.themeColor || pairColor;

            setState({ pairIndex, pairColor: finalPairColor, collectNodeId: foundCollectId });
        };

        compute();

        // ── 延迟补偿：adapter.ts 的边在 100ms 后添加，forceEdgeRefresh 在 200~900ms ──
        // 在这些时间点之后重新计算，确保边已经稳定连接
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

// ── ForNodeComponent ──────────────────────────────────────────────────────────
export const ForNodeComponent = ({ node }: { node: Node }) => {
    const [data, setData] = React.useState<any>(node.getData());

    React.useEffect(() => {
        const onD = () => setData({ ...node.getData() });
        node.on('change:data', onD);
        return () => { node.off('change:data', onD); };
    }, [node]);

    const { pairIndex, pairColor, collectNodeId } = useForPairState(node);

    // 配对成功时用 pairColor 作为主题色，与 Collect 节点同步；孤立则恢复灰色
    const effectiveColor = collectNodeId ? pairColor : '#8c8c8c';
    const theme = resolveTheme(effectiveColor);

    const { outlineCss, selected } = useNodeSelection(node, {
        defaultColor: theme.primary,
        selectedColor: theme.primary,
        borderRadius: 10,
    });

    const nodeLabel = data?.__label || 'For';

    // ── 悬停联动 ────────────────────────────────────────────────────────
    const handleMouseEnter = React.useCallback(() => {
        if (!collectNodeId) return;
        const graph = node.model?.graph;
        if (!graph) return;
        graph.trigger('loop-pair:hover-enter', { sourceId: node.id, peerId: collectNodeId });
    }, [node, collectNodeId]);

    const handleMouseLeave = React.useCallback(() => {
        if (!collectNodeId) return;
        const graph = node.model?.graph;
        if (!graph) return;
        graph.trigger('loop-pair:hover-leave', { sourceId: node.id, peerId: collectNodeId });
    }, [node, collectNodeId]);

    // ── 接收来自 Collect 节点的联动高亮 ──────────────────────────────────
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
    if (pairIndex != null) {
        if (collectNodeId) {
            inlineTag = (
                <Tooltip title="已配对汇聚点" placement="top">
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
        } else {
            inlineTag = (
                <Tooltip title="发后即忘模式（未绑定汇聚点）" placement="top">
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
                boxShadow: peerHighlight && collectNodeId
                    ? `0 0 0 3px ${pairColor}55, 0 2px 12px ${pairColor}33`
                    : undefined,
                transition: 'box-shadow .25s ease',
            }}
        >
            <NodeHeader
                icon={ICON_LOOP}
                title={nodeLabel}
                theme={theme}
                height={FOR_LAYOUT.headerHeight}
                extra={inlineTag}
                onTitleChange={(t) => node.setData({ ...node.getData(), __label: t })}
            />

            {/* ── 端口标签层：严格两行 Flex ── */}
            <div
                style={{ flex: 1, display: 'flex', flexDirection: 'column' }}
                onMouseEnter={handleMouseEnter}
                onMouseLeave={handleMouseLeave}
            >
                {/* 行1：数据流 list ↔ item */}
                <div style={{
                    display: 'flex', justifyContent: 'space-between', alignItems: 'center',
                    height: 30, paddingLeft: 10, paddingRight: 10,
                }}>
                    <span style={{ fontSize: 11, color: '#595959', userSelect: 'none', pointerEvents: 'none', lineHeight: 1, display: 'flex', alignItems: 'center', gap: 4 }}>
                        <span style={{ opacity: 0.6, fontSize: 12 }}>[ ]</span>
                        List
                    </span>
                    <span style={{ fontSize: 11, color: '#595959', userSelect: 'none', pointerEvents: 'none', lineHeight: 1, display: 'flex', alignItems: 'center', gap: 4 }}>
                        Item
                        <span style={{ opacity: 0.6 }}>?</span>
                    </span>
                </div>

                {/* 行2：控制流 start ↔ 空 */}
                <div style={{
                    display: 'flex', justifyContent: 'space-between', alignItems: 'center',
                    height: 30, paddingLeft: 10, paddingRight: 10,
                }}>
                    <span style={{ fontSize: 10, color: '#8b5cf6', userSelect: 'none', pointerEvents: 'none', lineHeight: 1, display: 'flex', alignItems: 'center', gap: 4 }}>
                        <span style={{ opacity: 0.8, fontSize: 12 }}>·))</span>
                        Start
                    </span>
                    <span />
                </div>
            </div>
        </NodeWrapper>
    );
};

export default ForNodeComponent;
