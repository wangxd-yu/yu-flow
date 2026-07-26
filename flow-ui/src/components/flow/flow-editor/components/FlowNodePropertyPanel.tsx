import React from 'react';
import { Tooltip } from 'antd';
import type { Graph, Node as X6Node } from '@antv/x6';
import type { FormInstance } from 'antd';
import { LeftOutlined, RightOutlined } from '@ant-design/icons';
import NodePropertyDrawer from './NodePropertyDrawer';

export interface FlowNodePropertyPanelProps {
  visible: boolean;
  collapsed: boolean;
  onToggleCollapsed: () => void;
  width: number;
  onResizeStart: (e: React.MouseEvent) => void;
  selectedNodeId: string | null;
  graph: Graph | null;
  globalForm?: FormInstance;
  isEdit: boolean;
  breakpoints: string[];
  onToggleBreakpoint: (nodeId: string) => void;
}

const FlowNodePropertyPanel: React.FC<FlowNodePropertyPanelProps> = ({
  visible,
  collapsed,
  onToggleCollapsed,
  width,
  onResizeStart,
  selectedNodeId,
  graph,
  globalForm,
  isEdit,
  breakpoints,
  onToggleBreakpoint,
}) => {
  if (!visible) return null;

  const selectedNode =
    selectedNodeId && graph ? (graph.getCellById(selectedNodeId) as X6Node | null) : null;

  return (
    <div
      style={{
        position: 'relative',
        display: 'flex',
        height: '100%',
        flexShrink: 0,
      }}
    >
      <Tooltip
        title={collapsed ? '展开属性面板' : '收起属性面板'}
        placement="left"
      >
        <div
          onClick={onToggleCollapsed}
          style={{
            position: 'absolute',
            left: -16,
            top: '50%',
            transform: 'translateY(-50%)',
            width: 16,
            height: 48,
            background: '#fff',
            border: '1px solid #e5e6eb',
            borderRight: 'none',
            borderRadius: '4px 0 0 4px',
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            cursor: 'pointer',
            zIndex: 10,
            color: '#8c8c8c',
            fontSize: 10,
            transition: 'color 0.2s, background 0.2s',
          }}
          onMouseEnter={(e) => {
            (e.currentTarget as HTMLDivElement).style.background = '#f5f5f5';
            (e.currentTarget as HTMLDivElement).style.color = '#1677ff';
          }}
          onMouseLeave={(e) => {
            (e.currentTarget as HTMLDivElement).style.background = '#fff';
            (e.currentTarget as HTMLDivElement).style.color = '#8c8c8c';
          }}
        >
          {collapsed ? <LeftOutlined /> : <RightOutlined />}
        </div>
      </Tooltip>
      <div
        style={{
          position: 'relative',
          width: collapsed ? 0 : width,
          minWidth: collapsed ? 0 : width,
          height: '100%',
          overflow: collapsed ? 'hidden' : 'auto',
          borderLeft: collapsed ? 'none' : '1px solid #e5e6eb',
          background: '#fff',
          transition: collapsed ? 'width 0.25s ease, min-width 0.25s ease' : undefined,
        }}
      >
        {!collapsed && (
          <div
            onMouseDown={onResizeStart}
            title="拖拽调整宽度"
            style={{
              position: 'absolute',
              left: 0,
              top: 0,
              bottom: 0,
              width: 5,
              cursor: 'col-resize',
              zIndex: 11,
              background: 'transparent',
            }}
            onMouseEnter={(e) => {
              (e.currentTarget as HTMLDivElement).style.background = 'rgba(22, 119, 255, 0.12)';
            }}
            onMouseLeave={(e) => {
              (e.currentTarget as HTMLDivElement).style.background = 'transparent';
            }}
          />
        )}
        <NodePropertyDrawer
          node={selectedNode}
          onDataChange={(node, changes) => {
            const prev = (node.getData?.() as Record<string, any>) || {};
            node.setData({ ...prev, ...changes }, { overwrite: true });
          }}
          globalForm={globalForm}
          isEdit={isEdit}
          isBreakpoint={selectedNodeId ? breakpoints.includes(selectedNodeId) : false}
          onToggleBreakpoint={onToggleBreakpoint}
        />
      </div>
    </div>
  );
};

export default FlowNodePropertyPanel;
