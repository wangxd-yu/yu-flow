import React from 'react';
import { Tooltip } from 'antd';
import { LeftOutlined, RightOutlined } from '@ant-design/icons';
import DslPalette from './DslPalette';
import type { DslNodeType } from '../types';

export interface FlowPalettePanelProps {
  visible: boolean;
  collapsed: boolean;
  onToggleCollapsed: () => void;
  graphRef: React.MutableRefObject<any>;
  onAddNode: (type: DslNodeType, position?: { x: number; y: number }) => void;
  canCreate: (type: DslNodeType) => { ok: boolean; reason?: string };
  editorContext: 'api' | 'task' | 'service' | 'mq';
}

const FlowPalettePanel: React.FC<FlowPalettePanelProps> = ({
  visible,
  collapsed,
  onToggleCollapsed,
  graphRef,
  onAddNode,
  canCreate,
  editorContext,
}) => {
  if (!visible) return null;

  return (
    <div style={{ position: 'relative', display: 'flex', height: '100%', flexShrink: 0 }}>
      <div
        style={{
          width: collapsed ? 0 : 280,
          minWidth: collapsed ? 0 : 280,
          height: '100%',
          overflow: collapsed ? 'hidden' : 'auto',
          borderRight: collapsed ? 'none' : '1px solid #e5e6eb',
          background: '#fff',
          transition: 'width 0.25s ease, min-width 0.25s ease',
        }}
      >
        <DslPalette
          graphRef={graphRef}
          onAddNode={onAddNode}
          canCreate={canCreate}
          editorContext={editorContext}
        />
      </div>
      <Tooltip
        title={collapsed ? '展开组件面板' : '收起组件面板'}
        placement="right"
      >
        <div
          onClick={onToggleCollapsed}
          style={{
            position: 'absolute',
            right: -16,
            top: '50%',
            transform: 'translateY(-50%)',
            width: 16,
            height: 48,
            background: '#fff',
            border: '1px solid #e5e6eb',
            borderLeft: 'none',
            borderRadius: '0 4px 4px 0',
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
          {collapsed ? <RightOutlined /> : <LeftOutlined />}
        </div>
      </Tooltip>
    </div>
  );
};

export default FlowPalettePanel;
