import React from 'react';
import { Input, Button, Typography, Space } from 'antd';
import { Node, NodeMetadata } from '@antv/x6';
import { register } from '@antv/x6-react-shape';
import type { NodeData, ConditionStep, ConditionVar } from '../../types';
import type { NodeDefinition } from '../nodeDefinition';
import { createId } from '../../utils/id';

const { Text } = Typography;

// 提取图标常量
const ICONS = {
  condition: (
    <svg viewBox="0 0 1024 1024" width="14" height="14" fill="currentColor">
      <path d="M512 64a448 448 0 1 1 0 896 448 448 0 0 1 0-896z m0 72a376 376 0 1 0 0 752 376 376 0 0 0 0-752z m0 480a48 48 0 1 1 0 96 48 48 0 0 1 0-96z m0-336a40 40 0 0 1 40 40v200a40 40 0 0 1-80 0V320a40 40 0 0 1 40-40z" />
    </svg>
  ),
  drag: (
    <svg viewBox="0 0 1024 1024" width="12" height="12" fill="currentColor">
      <path d="M384 256a64 64 0 1 1-128 0 64 64 0 0 1 128 0z m0 256a64 64 0 1 1-128 0 64 64 0 0 1 128 0z m0 256a64 64 0 1 1-128 0 64 64 0 0 1 128 0z m384-512a64 64 0 1 1-128 0 64 64 0 0 1 128 0z m0 256a64 64 0 1 1-128 0 64 64 0 0 1 128 0z m0 256a64 64 0 1 1-128 0 64 64 0 0 1 128 0z" />
    </svg>
  ),
  path: (
    <svg viewBox="0 0 1024 1024" width="14" height="14" fill="currentColor">
      <path d="M512 64C264.6 64 64 264.6 64 512s200.6 448 448 448 448-200.6 448-448S759.4 64 512 64z m0 824c-207.3 0-376-168.7-376-376s168.7-376 376-376 376 168.7 376 376-168.7 376-376 376z m166-401H547V321c0-19.3-15.7-35-35-35s-35 15.7-35 35v166H311c-19.3 0-35 15.7-35 35s15.7 35 35 35h166v166c0 19.3 15.7 35 35 35s35-15.7 35-35V522h131c19.3 0 35-15.7 35-35s-15.7-35-35-35z" />
    </svg>
  ),
  chevron: (
    <svg viewBox="0 0 1024 1024" width="12" height="12" fill="currentColor">
      <path d="M831.872 340.864 512 652.672 192.128 340.864a30.592 30.592 0 0 0-42.752 0 29.12 29.12 0 0 0 0 41.6L489.664 714.24a32 32 0 0 0 44.672 0l340.288-331.712a29.12 29.12 0 0 0 0-41.728 30.592 30.592 0 0 0-42.752 0z" />
    </svg>
  ),
  resize: (
    <svg viewBox="0 0 12 12" width="10" height="10" fill="currentColor">
      <path d="M8.5 8.5h1v1h-1zM6 8.5h1v1h-1zM3.5 8.5h1v1h-1zM8.5 6h1v1h-1zM6 6h1v1h-1zM8.5 3.5h1v1h-1z" />
    </svg>
  )
};

// =============================================================================
// 1. React Component for Node Visualization
// =============================================================================

export const ConditionNode = ({ node }: { node: Node }) => {
  const [data, setData] = React.useState<NodeData>(node.getData());
  const [attrs, setAttrs] = React.useState(node.getAttrs());
  const [size, setSize] = React.useState<{ width: number; height: number }>(node.getSize());

  React.useEffect(() => {
    const onDataChange = () => setData({ ...node.getData() } as NodeData);
    const onAttrsChange = () => setAttrs({ ...node.getAttrs() });
    const onSizeChange = () => setSize({ ...node.getSize() });

    node.on('change:data', onDataChange);
    node.on('change:attrs', onAttrsChange);
    node.on('change:size', onSizeChange);
    return () => {
      node.off('change:data', onDataChange);
      node.off('change:attrs', onAttrsChange);
      node.off('change:size', onSizeChange);
    };
  }, [node]);

  // 确保始终有一个空变量作为占位符 & 监听连接
  React.useEffect(() => {
    const graph = node.model?.graph;
    if (!graph) return;

    // 1. Ensure existing edges have tools
    try {
      const edges = graph.getConnectedEdges(node);
      edges.forEach(edge => {
        const t = edge.getTarget() as any;
        if (t && t.cell === node.id && t.port && String(t.port).startsWith('in:var:')) {
          if (!edge.hasTool('button-remove')) {
            edge.addTools({ name: 'button-remove', args: { distance: '50%' } });
          }
        }
      });
    } catch (e) { }

    // 2. Handle Connection to Placeholder (Last Variable)
    const onConnected = ({ edge }: any) => {
      const target = edge.getTarget();
      const currentVars = (node.getData() as any)?.step?.variables || [];
      const lastVar = currentVars[currentVars.length - 1];

      // 如果连接到了最后一个变量（即占位符）
      if (lastVar && target && target.cell === node.id && target.port === `in:var:${lastVar.id}`) {
        // 使用 Edge 实例锁
        if ((edge as any).__processingVar) return;
        (edge as any).__processingVar = true;

        const newId = createId('var');

        const updatedVars = [...currentVars];
        // 如果想给默认名
        if (!updatedVars[updatedVars.length - 1].name) {
          updatedVars[updatedVars.length - 1] = { ...lastVar, name: `var${updatedVars.length}` };
        }
        updatedVars.push({ id: newId, name: '', path: '' });

        node.setData({ ...node.getData() as any, step: { ...(node.getData() as any).step, variables: updatedVars } });

        // Add tool to the edge
        setTimeout(() => {
          edge.addTools({ name: 'button-remove', args: { distance: '50%' } });
          delete (edge as any).__processingVar;
        }, 50);
      }
    };

    graph.on('edge:connected', onConnected);
    return () => {
      graph.off('edge:connected', onConnected);
    };
  }, [node]);

  // Ensure there is at least one variable (placeholder) on mount
  React.useEffect(() => {
    const vars = (node.getData() as any)?.step?.variables || [];
    if (vars.length === 0) {
      const newId = createId('var');
      node.setData({ ...node.getData() as any, step: { ...(node.getData() as any).step, variables: [{ id: newId, name: '', path: '' }] } });
    }
  }, []);

  const step = (data as any)?.step as ConditionStep;
  const variables = step?.variables || [];
  const query = step?.query || '';
  const name = step?.name || 'If';

  // 简化后的拖拽状态
  const [dragState, setDragState] = React.useState<{
    index: number;
    startY: number;
    currentY: number;
  } | null>(null);

  const [hoverRowIndex, setHoverRowIndex] = React.useState<number | null>(null);
  const containerRef = React.useRef<HTMLDivElement>(null);

  const onAddVar = (e: React.MouseEvent) => {
    e.stopPropagation();
    const vars = [...variables];
    vars.push({ id: createId('var'), name: `var${vars.length + 1}`, path: '' });
    node.setData({ ...data, step: { ...step, variables: vars } });
  };

  const onUpdateVar = (id: string, patch: Partial<ConditionVar>) => {
    const vars = variables.map(v => v.id === id ? { ...v, ...patch } : v);
    node.setData({ ...data, step: { ...step, variables: vars } });
  };

  const onRemoveVar = (index: number) => {
    const graph = node.model?.graph;
    const varToRemove = variables[index];

    // 安全清理端口连接
    if (graph && varToRemove) {
      const portId = `in:var:${varToRemove.id}`;
      const edges = graph.getConnectedEdges(node);
      edges.forEach(edge => {
        const target = edge.getTarget();
        if (target && typeof target === 'object' && 'port' in target && target.port === portId) {
          graph.removeEdge(edge);
        }
      });
    }

    const vars = [...variables];
    vars.splice(index, 1);
    node.setData({ ...data, step: { ...step, variables: vars } });
  };

  const updateEdges = (portId: string) => {
    const graph = node.model?.graph;
    if (graph) {
      const edges = graph.getConnectedEdges(node);
      edges.forEach(edge => {
        const target = edge.getTarget() as any;
        if (target && typeof target === 'object' && target.port === portId) {
          (graph.findViewByCell(edge) as any)?.update();
        }
      });
    }
  };

  // Sync ports with variables
  React.useEffect(() => {
    const ports = node.getPorts();
    const existingPortIds = new Set(ports.map(p => p.id));
    const variablePortIds = new Set(variables.map(v => `in:var:${v.id}`));

    // Add missing ports & Update positions
    variables.forEach((v, idx) => {
      const pid = `in:var:${v.id}`;
      const standardY = 44 + idx * 40 + 20;

      if (!existingPortIds.has(pid)) {
        node.addPort({
          id: pid,
          group: 'manual',
          args: { x: 0, y: standardY },
          attrs: { circle: { r: 4, magnet: true, stroke: '#1677ff', strokeWidth: 1, fill: '#fff' } },
          zIndex: 1
        });
      } else if (!dragState) {
        // Update position if not dragging (handle list shifts)
        node.setPortProp(pid, 'args', { x: 0, y: standardY });
        updateEdges(pid);
      }
    });

    // Remove extra ports
    ports.forEach(p => {
      if (p.id && String(p.id).startsWith('in:var:') && !variablePortIds.has(p.id)) {
        node.removePort(p.id);
      }
    });

  }, [variables, node, dragState]);

  const handleDragStart = (index: number) => (e: React.MouseEvent) => {
    e.stopPropagation();
    e.preventDefault(); // 防止选中文本
    setDragState({
      index,
      startY: e.clientY,
      currentY: e.clientY
    });
  };

  // 全局拖拽逻辑
  React.useEffect(() => {
    if (!dragState) return;

    const rowHeight = 40;
    const startY = dragState.startY;

    const onMove = (e: MouseEvent) => {
      e.preventDefault();
      const currentY = e.clientY;
      const delta = currentY - startY;

      // 1. 更新 React 状态以触发重绘 (translateY)
      setDragState(prev => prev ? { ...prev, currentY } : null);

      // 2. 实时更新当前被拖拽行的端口位置 (跟随鼠标)
      const draggingVar = variables[dragState.index];
      if (draggingVar) {
        // 计算原本的基准 Y 坐标：Header(40) + Padding(4) + Index*RowHeight + Center(20)
        const baseTop = 44 + dragState.index * rowHeight + 20;
        const newY = baseTop + delta;
        node.setPortProp(`in:var:${draggingVar.id}`, 'args', { y: newY, x: 0 });
        updateEdges(`in:var:${draggingVar.id}`);
      }

      // 3. 检测是否需要交换行 (简单阈值判断)
      const moveDistance = delta;
      // 向下移动超过一半行高 -> 交换下一行
      if (moveDistance > rowHeight / 2 && dragState.index < variables.length - 1) {
        // 交换数据
        const vars = [...variables];
        const currentData = vars[dragState.index];
        vars[dragState.index] = vars[dragState.index + 1];
        vars[dragState.index + 1] = currentData;

        // 更新数据会触发 syncNodePorts 重置所有端口，这是一件好事，
        // 但我们必须更新 dragState 的 index 和 startY，因为行已经交换了位置，
        // 现在的 dragState.index 应该指向新的位置。
        const newIndex = dragState.index + 1;

        // 更新 StartY：因为我们交换了行，逻辑上的“原点”变了。
        // 原来在 index，现在在 index+1，所以基准 Y 增加了 rowHeight。
        // 为了保持 delta 计算正确，startY 需要增加 rowHeight。
        const newStartY = startY + rowHeight;

        node.setData({ ...data, step: { ...step, variables: vars } });
        setDragState({ index: newIndex, startY: newStartY, currentY });
      }
      // 向上移动超过一半行高 -> 交换上一行
      else if (moveDistance < -rowHeight / 2 && dragState.index > 0) {
        const vars = [...variables];
        const currentData = vars[dragState.index];
        vars[dragState.index] = vars[dragState.index - 1];
        vars[dragState.index - 1] = currentData;

        const newIndex = dragState.index - 1;
        const newStartY = startY - rowHeight;

        node.setData({ ...data, step: { ...step, variables: vars } });
        setDragState({ index: newIndex, startY: newStartY, currentY });
      }
    };

    const onUp = () => {
      // 拖拽结束，强制归位所有端口到标准位置 (清除 manual args)
      // syncNodePorts 已经处理了标准位置，这里只需触发一下确保对齐
      variables.forEach((v, idx) => {
        const standardY = 44 + idx * rowHeight + 20;
        node.setPortProp(`in:var:${v.id}`, 'args', { y: standardY, x: 0 });
        updateEdges(`in:var:${v.id}`);
      });
      setDragState(null);
    };

    document.addEventListener('mousemove', onMove);
    document.addEventListener('mouseup', onUp);
    return () => {
      document.removeEventListener('mousemove', onMove);
      document.removeEventListener('mouseup', onUp);
    };
  }, [dragState, variables, data, node, step]); // 依赖项要全，确保 setData 使用最新数据

  const onUpdateQuery = (val: string) => {
    node.setData({ ...data, step: { ...step, query: val } });
  };

  const [resizing, setResizing] = React.useState(false);

  // Content Height Logic
  // Header: 40, Vars: len*40, AddBtn: 32, Footer: 80.
  // Min Query Area: 60px (~2-3 rows)
  const minQueryHeight = 60;
  const contentHeightWithoutQuery = 40 + (variables.length * 40) + 32 + 80;
  const minTotalHeight = contentHeightWithoutQuery + minQueryHeight;

  // Auto-expand if content grows (and not resizing manually at the moment)
  React.useEffect(() => {
    if (resizing) return;
    const size = node.getSize();
    if (size.height < minTotalHeight) {
      node.resize(Math.max(size.width, 320), minTotalHeight);
    }
  }, [minTotalHeight, node, resizing]);

  const handleResizeStart = (e: React.MouseEvent) => {
    e.stopPropagation();
    e.preventDefault();
    setResizing(true);

    const startX = e.clientX;
    const startY = e.clientY;
    const startSize = node.getSize();

    const onMove = (evt: MouseEvent) => {
      const deltaX = evt.clientX - startX;
      const deltaY = evt.clientY - startY;
      const newW = Math.max(320, startSize.width + deltaX);
      const newH = Math.max(minTotalHeight, startSize.height + deltaY);
      node.resize(newW, newH);

      // 实时更新端口位置
      const footerTop = newH - 80;
      node.setPortProp('in:data', 'args', { x: 0, y: footerTop + 40 });
      node.setPortProp('out:then', 'args', { x: newW, y: footerTop + 20 });
      node.setPortProp('out:else', 'args', { x: newW, y: footerTop + 60 });

      // 更新连线
      updateEdges('in:data');
      updateEdges('out:then');
      updateEdges('out:else');
    };

    const onUp = () => {
      setResizing(false);
      window.removeEventListener('mousemove', onMove);
      window.removeEventListener('mouseup', onUp);
    };

    window.addEventListener('mousemove', onMove);
    window.addEventListener('mouseup', onUp);
  };

  const borderColor = attrs?.body?.stroke || '#1677ff';
  const backgroundColor = attrs?.body?.fill || '#ffffff';

  return (
    <div
      style={{
        width: '100%',
        height: '100%',
        backgroundColor: backgroundColor as string,
        border: `2px solid ${borderColor}`,
        borderRadius: 12,
        display: 'flex',
        flexDirection: 'column',
        overflow: 'hidden',
        boxSizing: 'border-box',
        pointerEvents: 'none',
        userSelect: 'none',
      }}
    >
      {/* Header */}
      <div style={{ height: 40, background: '#e6f4ff', display: 'flex', alignItems: 'center', padding: '0 12px', borderBottom: '1px solid #91caff', pointerEvents: 'auto' }}>
        <div style={{ color: '#1677ff', marginRight: 8, display: 'flex' }}>{ICONS.condition}</div>
        <Text strong style={{ flex: 1, color: '#0050b3' }}>{name}</Text>
        <Space size={4}>
          <Text style={{ fontSize: 11, color: '#1677ff' }}>FQL</Text>
          <div style={{ color: '#1677ff', display: 'flex' }}>{ICONS.chevron}</div>
        </Space>
      </div>

      {/* Variables List */}
      <div ref={containerRef} style={{ padding: '4px 0', pointerEvents: 'auto', position: 'relative' }}>
        {variables.map((v, idx) => {
          const isPlaceholder = idx === variables.length - 1;
          const isDragging = dragState?.index === idx;
          const isHovering = hoverRowIndex === idx;

          let transform = 'translateY(0)';
          let zIndex = 1;

          if (isDragging && dragState) {
            const delta = dragState.currentY - dragState.startY;
            transform = `translateY(${delta}px)`;
            zIndex = 100;
          }

          if (isPlaceholder) {
            return (
              <div
                key={v.id}
                onClick={onAddVar}
                style={{
                  height: 40,
                  display: 'flex',
                  alignItems: 'center',
                  padding: '0 4px 0 12px',
                  gap: 6,
                  position: 'relative',
                  cursor: 'pointer'
                }}
              >
                {/* 左侧连接柱 (蓝色，表示可连接) */}
                <div
                  style={{
                    position: 'absolute',
                    left: 0,
                    top: 20,
                    marginTop: -2,
                    width: 4,
                    height: 4,
                    borderRadius: '50%',
                    backgroundColor: '#1677ff',
                    border: '1px solid #fff',
                  }}
                />
                {/* Placeholder UI */}
                <Space size={8} style={{ paddingLeft: 4 }}>
                  <Text style={{ fontSize: 12, color: '#8c8c8c' }}>variable</Text>
                  <div style={{ fontSize: 18, color: '#1677ff', display: 'flex', alignItems: 'center', width: 20, height: 20, justifyContent: 'center', lineHeight: 1 }}>+</div>
                </Space>
              </div>
            );
          }

          return (
            <div
              key={v.id}
              onMouseEnter={() => !dragState && setHoverRowIndex(idx)}
              onMouseLeave={() => !dragState && setHoverRowIndex(null)}
              style={{
                height: 40,
                display: 'flex',
                alignItems: 'center',
                padding: '0 4px 0 12px',
                gap: 6,
                transform,
                zIndex,
                position: 'relative',
                backgroundColor: isDragging ? '#e6f4ff' : (isHovering ? '#f5f5f5' : 'transparent'),
                transition: isDragging ? 'none' : 'background-color 0.2s',
                borderRadius: 4
              }}
            >
              {/* 左侧连接柱 */}
              <div
                style={{
                  position: 'absolute',
                  left: 0,
                  top: 20,
                  marginTop: -2,
                  width: 4,
                  height: 4,
                  borderRadius: '50%',
                  backgroundColor: '#1677ff',
                  border: '1px solid #fff',
                }}
              />

              {/* 拖拽手柄 */}
              <div
                onMouseDown={handleDragStart(idx)}
                style={{
                  color: isDragging ? '#1677ff' : '#bfbfbf',
                  display: 'flex',
                  cursor: isDragging ? 'grabbing' : 'grab',
                  padding: '4px',
                  transition: 'color 0.2s',
                }}
              >
                {ICONS.drag}
              </div>

              <Input
                size="small"
                variant="filled"
                value={v.name}
                onChange={e => onUpdateVar(v.id, { name: e.target.value })}
                onMouseDown={e => e.stopPropagation()}
                style={{ flex: 1, fontSize: 12, height: 32, backgroundColor: '#f5f5f5', border: 'none' }}
              />
              <Input
                size="small"
                prefix={<div style={{ color: '#bfbfbf', display: 'flex' }}>{ICONS.path}</div>}
                placeholder="Enter path..."
                value={v.path}
                onChange={e => onUpdateVar(v.id, { path: e.target.value })}
                onMouseDown={e => e.stopPropagation()}
                style={{ flex: 1.5, fontSize: 12, height: 32 }}
              />

              {/* 删除按钮 */}
              {isHovering && !dragState && (
                <div
                  onClick={(e) => {
                    e.stopPropagation();
                    onRemoveVar(idx);
                  }}
                  style={{
                    color: '#ff4d4f',
                    cursor: 'pointer',
                    padding: '0 4px',
                    fontSize: 14,
                    display: 'flex'
                  }}
                >
                  ✕
                </div>
              )}
            </div>
          );
        })}
      </div>

      {/* Query TextArea - Flex Grow */}
      <div style={{ padding: '8px 12px', pointerEvents: 'auto', flex: 1, display: 'flex', flexDirection: 'column', minHeight: minQueryHeight }}>
        <Input.TextArea
          value={query}
          placeholder="Start writing FQL query"
          onChange={e => onUpdateQuery(e.target.value)}
          onMouseDown={e => e.stopPropagation()}
          style={{ fontSize: 12, borderRadius: 6, resize: 'none', height: '100%' }}
        />
      </div>



      {/* Footer Branches */}
      <div style={{ height: 80, position: 'relative', pointerEvents: 'auto' }}>
        <svg style={{ position: 'absolute', width: '100%', height: '100%', pointerEvents: 'none' }}>
          {/* Input Line to Fork Start (Center - 30) */}
          <path d={`M 55 40 H ${size.width / 2 - 30}`} fill="none" stroke="#d9d9d9" strokeWidth="1" />
          {/* Top Curve: Fork Start -> Center -> Label Left (Right - 120) */}
          <path d={`M ${size.width / 2 - 30} 40 C ${size.width / 2 - 15} 40, ${size.width / 2 - 15} 20, ${size.width / 2} 20 H ${size.width - 120}`} fill="none" stroke="#d9d9d9" strokeWidth="1" />
          {/* Bottom Curve */}
          <path d={`M ${size.width / 2 - 30} 40 C ${size.width / 2 - 15} 40, ${size.width / 2 - 15} 60, ${size.width / 2} 60 H ${size.width - 120}`} fill="none" stroke="#d9d9d9" strokeWidth="1" />
          {/* Output Lines: Label Right (Right - 75) -> End */}
          <path d={`M ${size.width - 75} 20 H ${size.width}`} fill="none" stroke="#d9d9d9" strokeWidth="1" />
          <path d={`M ${size.width - 75} 60 H ${size.width}`} fill="none" stroke="#d9d9d9" strokeWidth="1" />
        </svg>

        <div style={{ position: 'absolute', left: 12, top: 40, transform: 'translateY(-50%)' }}>
          <Text style={{ fontSize: 12, color: '#595959' }}>? Data</Text>
        </div>

        <div style={{ position: 'absolute', right: 75, top: 20, transform: 'translateY(-50%)', background: '#f0f0f0', padding: '2px 8px', borderRadius: 10 }}>
          <Text style={{ fontSize: 10, color: '#8c8c8c' }}>THEN</Text>
        </div>
        <div style={{ position: 'absolute', right: 75, top: 60, transform: 'translateY(-50%)', background: '#e6f4ff', padding: '2px 8px', borderRadius: 10 }}>
          <Text style={{ fontSize: 10, color: '#1677ff' }}>ELSE</Text>
        </div>
        <div style={{ position: 'absolute', right: 35, top: 20, transform: 'translateY(-50%)' }}>
          <Text style={{ fontSize: 12, color: '#bfbfbf' }}>?</Text>
        </div>
        <div style={{ position: 'absolute', right: 35, top: 60, transform: 'translateY(-50%)' }}>
          <Text style={{ fontSize: 12, color: '#bfbfbf' }}>?</Text>
        </div>
      </div>

      {/* Resize Handle */}
      <div
        onMouseDown={handleResizeStart}
        style={{
          position: 'absolute',
          bottom: 2,
          right: 2,
          width: 14,
          height: 14,
          cursor: 'nwse-resize',
          pointerEvents: 'auto',
          zIndex: 10,
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'center',
          color: '#1677ff' // Deep blue for better visibility
        }}
      >
        {ICONS.resize}
      </div>
    </div>
  );
};

// ... Properties Panel and Registration Code remains the same ...
function ConditionProperties(props: { data: NodeData; onChange: (next: NodeData) => void }) {
  const { data, onChange } = props;
  const step = (data as any)?.step as ConditionStep;

  const apply = (patch: Partial<ConditionStep>) => {
    if (data.kind === 'step') {
      const next: NodeData = {
        ...data,
        step: { ...data.step, ...patch } as any
      };
      onChange(next);
    }
  };

  const addVar = () => {
    const vars = [...(step.variables || [])];
    vars.push({ id: createId('var'), name: `var${vars.length + 1}`, path: '' });
    apply({ variables: vars });
  };

  const updateVar = (id: string, patch: Partial<ConditionVar>) => {
    const vars = (step.variables || []).map(v => v.id === id ? { ...v, ...patch } : v);
    apply({ variables: vars });
  };

  const removeVar = (id: string) => {
    const vars = (step.variables || []).filter(v => v.id !== id);
    apply({ variables: vars });
  };

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 16 }}>
      <div>
        <Text strong>名称</Text>
        <Input
          style={{ marginTop: 8 }}
          value={step.name}
          onChange={e => apply({ name: e.target.value })}
        />
      </div>

      <div>
        <Text strong>变量</Text>
        <div style={{ display: 'flex', flexDirection: 'column', gap: 8, marginTop: 8 }}>
          {(step.variables || []).map((v, idx) => (
            <div key={v.id} style={{ display: 'flex', gap: 4, alignItems: 'center' }}>
              <Input
                placeholder="变量名"
                value={v.name}
                onChange={e => updateVar(v.id, { name: e.target.value })}
              />
              <Input
                placeholder="路径"
                value={v.path}
                onChange={e => updateVar(v.id, { path: e.target.value })}
              />
              <Button size="small" type="link" danger onClick={() => removeVar(v.id)}>删除</Button>
            </div>
          ))}
          <Button type="dashed" onClick={addVar}>添加变量</Button>
        </div>
      </div>

      <div>
        <Text strong>FQL 查询</Text>
        <Input.TextArea
          style={{ marginTop: 8 }}
          rows={4}
          value={step.query}
          placeholder="例如：age > 18"
          onChange={e => apply({ query: e.target.value })}
        />
      </div>
    </div>
  );
}

export const conditionNodeDefinition: NodeDefinition<'condition'> = {
  type: 'condition',
  label: '条件 (If)',
  category: '高级组件',
  buildNodeConfig: (data, position) => {
    const step = (data as any).step as ConditionStep;
    return {
      id: step.id,
      shape: 'flow-condition-card',
      x: position?.x ?? 60,
      y: position?.y ?? 40,
      data,
      attrs: {
        body: {
          stroke: '#1677ff',
          strokeWidth: 2,
          fill: '#ffffff',
        },
      },
    } as NodeMetadata;
  },
  getDisplayLabel: (data) => {
    const step = (data as any).step as ConditionStep;
    return step.name || 'If';
  },
  renderProperties: (params) => <ConditionProperties data={params.data} onChange={params.onChange} />,
};

export function createConditionData(): NodeData {
  return {
    kind: 'step',
    nodeType: 'condition',
    step: {
      id: createId('condition'),
      type: 'condition',
      name: 'If',
      variables: [
        { id: createId('var'), name: '', path: '' } // Default Placeholder
      ],
      query: '',
      cases: []
    } as ConditionStep
  };
}

let registered = false;

export function registerConditionNode() {
  if (registered) return;
  registered = true;

  register({
    shape: 'flow-condition-card',
    width: 320,
    height: 300,
    component: ConditionNode,
    ports: {
      groups: {
        manual: {
          position: 'absolute',
          attrs: {
            circle: { r: 4, magnet: true, stroke: '#1677ff', strokeWidth: 1, fill: '#fff' },
          },
        },
      },
      items: [],
    },
  });
}
