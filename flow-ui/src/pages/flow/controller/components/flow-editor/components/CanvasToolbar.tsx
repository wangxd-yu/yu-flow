import React from 'react';
import { Button, Tooltip, Dropdown } from 'antd';
import {
  ZoomInOutlined,
  ZoomOutOutlined,
  CompressOutlined,
  AimOutlined,
  ApartmentOutlined,
} from '@ant-design/icons';
import type { Graph, Node, Edge } from '@antv/x6';

export type CanvasToolbarProps = {
  graph: Graph | null;
};

// ============================================================================
// 自动排版算法
// 基于连线方向的拓扑排序 → 分层 → 瀑布式排列
// 端口模型：输入在左，输出在右 → 流向从左到右 (LR)
// ============================================================================

interface LayoutOptions {
  /** 同层节点排列方式: 'flow' = 按原位置排序, 'tree' = 子节点靠近父节点 */
  mode: 'flow' | 'tree';
  /** 列间距（水平方向，层与层之间） */
  rankSep: number;
  /** 行间距（垂直方向，同层节点之间） */
  nodeSep: number;
}

interface NodeInfo {
  id: string;
  node: Node;
  width: number;
  height: number;
  /** 所在层级（根据连线方向拓扑排序得出） */
  rank: number;
  /** 同层内的排列序号 */
  order: number;
  /** 下游节点 ID（从此节点的输出端口连出去的节点） */
  children: string[];
  /** 上游节点 ID（连线连入此节点的节点） */
  parents: string[];
}

/**
 * 对画布中所有节点进行自动排版（水平方向，从左到右）。
 * 核心算法：
 * 1. 根据连线（edge）的 source→target 建立拓扑关系
 * 2. 用最长路径算法为每个节点分配层级（rank）
 * 3. 没有入边的节点为第 0 层（最左侧），依次向右排列
 * 4. 同层内节点垂直排列，居中对齐
 */
function autoLayout(graph: Graph, options: LayoutOptions) {
  const nodes = graph.getNodes();
  const edges = graph.getEdges();

  if (nodes.length === 0) return;

  const { rankSep, nodeSep, mode } = options;

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

  // 根据连线建立 parent→child 关系
  for (const edge of edges) {
    const src = (edge.getSource() as any)?.cell;
    const tgt = (edge.getTarget() as any)?.cell;
    if (!src || !tgt) continue;
    if (src === tgt) continue; // 跳过自环
    const srcInfo = nodeMap.get(src);
    const tgtInfo = nodeMap.get(tgt);
    if (srcInfo && tgtInfo) {
      if (!srcInfo.children.includes(tgt)) srcInfo.children.push(tgt);
      if (!tgtInfo.parents.includes(src)) tgtInfo.parents.push(src);
    }
  }

  // ── 2. 拓扑分层：最长路径算法 ──
  // rank = 从根节点到此节点的最长路径长度
  // 这保证了：如果 A→B→C，则 rank(A) < rank(B) < rank(C)
  const computing = new Set<string>(); // 防环

  function assignRank(id: string): number {
    const info = nodeMap.get(id);
    if (!info) return 0;
    if (info.rank >= 0) return info.rank;
    if (computing.has(id)) return 0; // 检测到环，中断
    computing.add(id);

    if (info.parents.length === 0) {
      // 没有上游 → 第 0 层（最左侧）
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

  // 孤立节点（无连线）放在最左列
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
      // 树模式：子节点按其父节点的 order 均值排列，使子节点垂直方向靠近父节点
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
      // 流模式：按节点当前 Y 坐标排序（保持用户原有上下顺序）
      group.sort((a, b) => {
        const posA = a.node.getPosition();
        const posB = b.node.getPosition();
        return posA.y - posB.y;
      });
    }

    group.forEach((info, idx) => { info.order = idx; });
  }

  // ── 5. 计算位置（从左到右排列） ──
  // X 轴表示层级（从左到右），Y 轴表示同层内的排列（从上到下）

  // 每层的最大宽度（用于水平间距计算）
  const rankMaxWidth: number[] = [];
  for (let r = 0; r <= maxRank; r++) {
    const group = rankGroups.get(r) || [];
    rankMaxWidth.push(group.reduce((m, i) => Math.max(m, i.width), 0));
  }

  // 每层的 X 起始位置
  const startX = 60;
  const rankX: number[] = [];
  let xOffset = startX;
  for (let r = 0; r <= maxRank; r++) {
    rankX.push(xOffset);
    xOffset += rankMaxWidth[r] + rankSep;
  }

  // 每层内节点的 Y 位置（居中排列）
  // 先计算所有层中节点数最多的层的总高度，用于全局垂直居中
  let maxGroupHeight = 0;
  for (let r = 0; r <= maxRank; r++) {
    const group = rankGroups.get(r) || [];
    let totalH = 0;
    for (const info of group) totalH += info.height;
    totalH += Math.max(0, group.length - 1) * nodeSep;
    if (totalH > maxGroupHeight) maxGroupHeight = totalH;
  }

  const startY = 60;

  for (let r = 0; r <= maxRank; r++) {
    const group = rankGroups.get(r) || [];
    if (group.length === 0) continue;

    // 计算本层总高度
    let totalH = 0;
    for (const info of group) totalH += info.height;
    totalH += Math.max(0, group.length - 1) * nodeSep;

    // 垂直居中：相对于最大层高度居中
    let yOffset = startY + (maxGroupHeight - totalH) / 2;

    for (const info of group) {
      // 水平方向：层内居中对齐
      const x = rankX[r] + (rankMaxWidth[r] - info.width) / 2;
      const y = yOffset;

      info.node.setPosition(x, y);
      yOffset += info.height + nodeSep;
    }
  }

  // ── 6. 排版完成，适配画布 ──
  setTimeout(() => {
    graph.zoomToFit({ padding: 40, maxScale: 1 });
    graph.centerContent();
  }, 50);
}

// ============================================================================
// 布局菜单选项
// ============================================================================

const LAYOUT_ITEMS = [
  {
    key: 'lr-flow',
    label: '水平排版',
    icon: '→',
    options: { mode: 'flow' as const, rankSep: 80, nodeSep: 40 },
  },
  {
    key: 'lr-tree',
    label: '树状排版',
    icon: '🌳',
    options: { mode: 'tree' as const, rankSep: 80, nodeSep: 40 },
  },
];

export default function CanvasToolbar({ graph }: CanvasToolbarProps) {
  if (!graph) return null;

  const handleLayout = React.useCallback(
    (key: string) => {
      const item = LAYOUT_ITEMS.find(i => i.key === key);
      if (!item) return;
      autoLayout(graph, item.options);
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

      {/* 分隔线 */}
      <div style={{ height: 1, background: '#e8e8e8', margin: '0 4px' }} />

      {/* 自动排版 */}
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
    </div>
  );
}
