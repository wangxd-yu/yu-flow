import React from 'react';
import { Button, Tooltip, Dropdown } from 'antd';
import {
  ZoomInOutlined,
  ZoomOutOutlined,
  CompressOutlined,
  AimOutlined,
  ApartmentOutlined,
  NodeIndexOutlined,
  AppstoreOutlined,
  BorderOuterOutlined,
} from '@ant-design/icons';
import type { Graph, Node } from '@antv/x6';
import {
  applyEdgeRouteStyle,
  getActiveEdgeRouteStyle,
  EDGE_ROUTE_PRESETS,
  type EdgeRouteStyleKey,
} from '../adapter';
import {
  useNodeViewModeControls,
  type NodeViewMode,
} from '../node-registry/shared/NodeViewMode';

export type CanvasToolbarProps = {
  graph: Graph | null;
};

// ============================================================================
// 自动排版算法
// 基于连线方向的拓扑排序 → 分层 → 瀑布式排列
// 端口模型：输入在左，输出在右 → 流向从左到右 (LR)
// style:
//   normal / compact — 单行 LR（紧凑仅缩小间距）
//   serpentine — 每行固定列数，满行折到下一行（仍左右连线）
// ============================================================================

interface LayoutOptions {
  /** 同层节点排列方式: 'flow' = 按原位置排序, 'tree' = 子节点靠近父节点 */
  mode: 'flow' | 'tree';
  /** 排版样式 */
  style?: 'normal' | 'compact' | 'serpentine';
  /** 列间距（水平方向，层与层之间） */
  rankSep: number;
  /** 行间距（垂直方向，同层节点之间） */
  nodeSep: number;
  /** 蛇形换行：每行最多层数（列数） */
  columns?: number;
  /** 蛇形换行：行与行之间的额外间距 */
  rowSep?: number;
}

interface NodeInfo {
  id: string;
  node: Node;
  width: number;
  height: number;
  rank: number;
  order: number;
  children: string[];
  parents: string[];
}

function fitGraph(graph: Graph) {
  setTimeout(() => {
    graph.zoomToFit({ padding: 40, maxScale: 1 });
    graph.centerContent();
  }, 50);
}

/**
 * 对画布中所有节点进行自动排版（水平方向，从左到右）。
 */
function autoLayout(graph: Graph, options: LayoutOptions) {
  const nodes = graph.getNodes();
  const edges = graph.getEdges();

  if (nodes.length === 0) return;

  const {
    rankSep,
    nodeSep,
    mode,
    style = 'normal',
    columns = 4,
    rowSep = 64,
  } = options;

  // ── 1. 构建拓扑关系 ──
  const nodeMap = new Map<string, NodeInfo>();
  for (const n of nodes) {
    const size = n.getSize();
    nodeMap.set(n.id, {
      id: n.id,
      node: n,
      width: size.width,
      height: size.height,
      rank: -1,
      order: 0,
      children: [],
      parents: [],
    });
  }

  for (const edge of edges) {
    const src = (edge.getSource() as any)?.cell;
    const tgt = (edge.getTarget() as any)?.cell;
    if (!src || !tgt || src === tgt) continue;
    const srcInfo = nodeMap.get(src);
    const tgtInfo = nodeMap.get(tgt);
    if (srcInfo && tgtInfo) {
      if (!srcInfo.children.includes(tgt)) srcInfo.children.push(tgt);
      if (!tgtInfo.parents.includes(src)) tgtInfo.parents.push(src);
    }
  }

  // ── 2. 拓扑分层：最长路径 ──
  const computing = new Set<string>();

  function assignRank(id: string): number {
    const info = nodeMap.get(id);
    if (!info) return 0;
    if (info.rank >= 0) return info.rank;
    if (computing.has(id)) return 0;
    computing.add(id);

    if (info.parents.length === 0) {
      info.rank = 0;
    } else {
      let maxParentRank = 0;
      for (const pid of info.parents) {
        maxParentRank = Math.max(maxParentRank, assignRank(pid));
      }
      info.rank = maxParentRank + 1;
    }

    computing.delete(id);
    return info.rank;
  }

  for (const id of nodeMap.keys()) {
    assignRank(id);
  }

  for (const info of nodeMap.values()) {
    if (info.rank < 0) info.rank = 0;
  }

  // ── 3. 按层分组 ──
  const rankGroups = new Map<number, NodeInfo[]>();
  for (const info of nodeMap.values()) {
    const arr = rankGroups.get(info.rank) || [];
    arr.push(info);
    rankGroups.set(info.rank, arr);
  }

  const maxRank = Math.max(...Array.from(rankGroups.keys()), 0);

  // ── 4. 同层排序 ──
  for (let r = 0; r <= maxRank; r++) {
    const group = rankGroups.get(r);
    if (!group) continue;

    if (mode === 'tree' && r > 0) {
      group.sort((a, b) => {
        const avgA = a.parents.reduce((sum, pid) => {
          const p = nodeMap.get(pid);
          return sum + (p ? p.order : 0);
        }, 0) / (a.parents.length || 1);
        const avgB = b.parents.reduce((sum, pid) => {
          const p = nodeMap.get(pid);
          return sum + (p ? p.order : 0);
        }, 0) / (b.parents.length || 1);
        return avgA - avgB;
      });
    } else {
      group.sort((a, b) => {
        const posA = a.node.getPosition();
        const posB = b.node.getPosition();
        return posA.y - posB.y;
      });
    }

    group.forEach((info, idx) => { info.order = idx; });
  }

  // 每层最大宽 / 层内总高
  const rankMaxWidth: number[] = [];
  const rankTotalHeight: number[] = [];
  for (let r = 0; r <= maxRank; r++) {
    const group = rankGroups.get(r) || [];
    rankMaxWidth.push(group.reduce((m, i) => Math.max(m, i.width), 0));
    let totalH = 0;
    for (const info of group) totalH += info.height;
    totalH += Math.max(0, group.length - 1) * nodeSep;
    rankTotalHeight.push(totalH);
  }

  const startX = 60;
  const startY = 60;

  if (style === 'serpentine') {
    // ── 蛇形：按 rank 折行，每行仍左→右，保持左右端口语义 ──
    const cols = Math.max(1, columns);
    const rowCount = Math.floor(maxRank / cols) + 1;
    let yCursor = startY;

    for (let row = 0; row < rowCount; row++) {
      const rankStart = row * cols;
      const rankEnd = Math.min(maxRank, rankStart + cols - 1);
      if (rankStart > maxRank) break;

      // 本行各层相对 X
      const localRankX: number[] = [];
      let xOffset = startX;
      for (let r = rankStart; r <= rankEnd; r++) {
        localRankX[r] = xOffset;
        xOffset += rankMaxWidth[r] + rankSep;
      }

      // 本行最大层高（用于行内垂直居中）
      let rowMaxH = 0;
      for (let r = rankStart; r <= rankEnd; r++) {
        rowMaxH = Math.max(rowMaxH, rankTotalHeight[r]);
      }

      for (let r = rankStart; r <= rankEnd; r++) {
        const group = rankGroups.get(r) || [];
        if (group.length === 0) continue;

        let yOffset = yCursor + (rowMaxH - rankTotalHeight[r]) / 2;
        for (const info of group) {
          const x = localRankX[r] + (rankMaxWidth[r] - info.width) / 2;
          info.node.setPosition(x, yOffset);
          yOffset += info.height + nodeSep;
        }
      }

      yCursor += rowMaxH + rowSep;
    }

    fitGraph(graph);
    return;
  }

  // ── 5. 单行 LR（normal / compact 仅间距不同，已在 options 传入） ──
  const rankX: number[] = [];
  let xOffset = startX;
  for (let r = 0; r <= maxRank; r++) {
    rankX.push(xOffset);
    xOffset += rankMaxWidth[r] + rankSep;
  }

  let maxGroupHeight = 0;
  for (let r = 0; r <= maxRank; r++) {
    maxGroupHeight = Math.max(maxGroupHeight, rankTotalHeight[r]);
  }

  for (let r = 0; r <= maxRank; r++) {
    const group = rankGroups.get(r) || [];
    if (group.length === 0) continue;

    let yOffset = startY + (maxGroupHeight - rankTotalHeight[r]) / 2;
    for (const info of group) {
      const x = rankX[r] + (rankMaxWidth[r] - info.width) / 2;
      info.node.setPosition(x, yOffset);
      yOffset += info.height + nodeSep;
    }
  }

  fitGraph(graph);
}

// ============================================================================
// 布局菜单选项
// ============================================================================

const LAYOUT_ITEMS = [
  {
    key: 'lr-serpentine',
    label: '蛇形换行',
    icon: '↩',
    options: {
      mode: 'flow' as const,
      style: 'serpentine' as const,
      rankSep: 64,
      nodeSep: 36,
      columns: 4,
      rowSep: 72,
    },
  },
  {
    key: 'lr-compact',
    label: '紧凑水平',
    icon: '⇉',
    options: {
      mode: 'flow' as const,
      style: 'compact' as const,
      rankSep: 48,
      nodeSep: 28,
    },
  },
  {
    key: 'lr-flow',
    label: '水平排版',
    icon: '→',
    options: {
      mode: 'flow' as const,
      style: 'normal' as const,
      rankSep: 80,
      nodeSep: 40,
    },
  },
  {
    key: 'lr-tree',
    label: '树状排版',
    icon: '🌳',
    options: {
      mode: 'tree' as const,
      style: 'normal' as const,
      rankSep: 80,
      nodeSep: 40,
    },
  },
];

const EDGE_ROUTE_ITEMS: { key: EdgeRouteStyleKey; icon: string; label: string }[] = [
  { key: 'er', icon: '┐', label: EDGE_ROUTE_PRESETS.er.label },
  { key: 'manhattan', icon: '⤴', label: EDGE_ROUTE_PRESETS.manhattan.label },
];

export default function CanvasToolbar({ graph }: CanvasToolbarProps) {
  const [edgeStyle, setEdgeStyle] = React.useState<EdgeRouteStyleKey>(getActiveEdgeRouteStyle);
  const { mode: viewMode, setMode: setViewMode } = useNodeViewModeControls();

  const handleViewMode = React.useCallback(
    (key: string) => {
      if (key === 'card' || key === 'compact') setViewMode(key as NodeViewMode);
    },
    [setViewMode],
  );

  const handleLayout = React.useCallback(
    (key: string) => {
      if (!graph) return;
      const item = LAYOUT_ITEMS.find(i => i.key === key);
      if (!item) return;
      autoLayout(graph, item.options);
    },
    [graph],
  );

  const handleEdgeRoute = React.useCallback(
    (key: string) => {
      if (!graph) return;
      const style = key as EdgeRouteStyleKey;
      if (!EDGE_ROUTE_PRESETS[style]) return;
      applyEdgeRouteStyle(graph, style);
      setEdgeStyle(style);
    },
    [graph],
  );

  const menuItems = LAYOUT_ITEMS.map(item => ({
    key: item.key,
    label: (
      <span style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
        <span style={{ width: 18, textAlign: 'center' }}>{item.icon}</span>
        <span>{item.label}</span>
      </span>
    ),
  }));

  const edgeMenuItems = EDGE_ROUTE_ITEMS.map(item => ({
    key: item.key,
    label: (
      <span style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
        <span style={{ width: 18, textAlign: 'center' }}>{item.icon}</span>
        <span>{item.label}</span>
        {edgeStyle === item.key ? (
          <span style={{ marginLeft: 'auto', color: '#1677ff', fontSize: 11 }}>✓</span>
        ) : null}
      </span>
    ),
  }));

  if (!graph) return null;

  return (
    <div
      style={{
        position: 'absolute',
        top: 20,
        right: 20,
        display: 'flex',
        flexDirection: 'column',
        gap: 8,
        background: '#fff',
        padding: 4,
        borderRadius: 4,
        boxShadow: '0 2px 8px rgba(0,0,0,0.15)',
        zIndex: 10,
      }}
    >
      <Tooltip title="放大" placement="left">
        <Button
          icon={<ZoomInOutlined />}
          type="text"
          onClick={() => graph.zoom(0.1)}
        />
      </Tooltip>
      <Tooltip title="缩小" placement="left">
        <Button
          icon={<ZoomOutOutlined />}
          type="text"
          onClick={() => graph.zoom(-0.1)}
        />
      </Tooltip>
      <Tooltip title="适配画布" placement="left">
        <Button
          icon={<CompressOutlined />}
          type="text"
          onClick={() => graph.zoomToFit({ padding: 20, maxScale: 1 })}
        />
      </Tooltip>
      <Tooltip title="定位中心" placement="left">
        <Button
          icon={<AimOutlined />}
          type="text"
          onClick={() => graph.centerContent()}
        />
      </Tooltip>

      <div style={{ height: 1, background: '#e8e8e8', margin: '0 4px' }} />

      <Dropdown
        menu={{
          items: [
            {
              key: 'card',
              label: (
                <span style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
                  <AppstoreOutlined />
                  <span>完整卡片</span>
                  {viewMode === 'card' ? (
                    <span style={{ marginLeft: 'auto', color: '#1677ff', fontSize: 11 }}>✓</span>
                  ) : null}
                </span>
              ),
            },
            {
              key: 'compact',
              label: (
                <span style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
                  <BorderOuterOutlined />
                  <span>极简标题</span>
                  {viewMode === 'compact' ? (
                    <span style={{ marginLeft: 'auto', color: '#1677ff', fontSize: 11 }}>✓</span>
                  ) : null}
                </span>
              ),
            },
          ],
          onClick: ({ key }) => handleViewMode(key),
          selectedKeys: [viewMode],
        }}
        placement="bottomRight"
        trigger={['click']}
      >
        <Tooltip
          title={viewMode === 'compact' ? '节点视图：极简' : '节点视图：完整'}
          placement="left"
        >
          <Button
            icon={viewMode === 'compact' ? <BorderOuterOutlined /> : <AppstoreOutlined />}
            type="text"
          />
        </Tooltip>
      </Dropdown>

      <Dropdown
        menu={{
          items: menuItems,
          onClick: ({ key }) => handleLayout(key),
        }}
        placement="bottomRight"
        trigger={['click']}
      >
        <Tooltip title="自动排版" placement="left">
          <Button
            icon={<ApartmentOutlined />}
            type="text"
          />
        </Tooltip>
      </Dropdown>

      <Dropdown
        menu={{
          items: edgeMenuItems,
          onClick: ({ key }) => handleEdgeRoute(key),
          selectedKeys: [edgeStyle],
        }}
        placement="bottomRight"
        trigger={['click']}
      >
        <Tooltip title="连线样式" placement="left">
          <Button
            icon={<NodeIndexOutlined />}
            type="text"
          />
        </Tooltip>
      </Dropdown>
    </div>
  );
}
