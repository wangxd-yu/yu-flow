import React from 'react';
import { Button, Space, Tooltip } from 'antd';
import type { Graph } from '@antv/x6';

export type ToolbarProps = {
  graph: Graph | null;
  canUndo: boolean;
  canRedo: boolean;
  onUndo: () => void;
  onRedo: () => void;
  isFullscreen: boolean;
  onToggleFullscreen: () => void;
  minimapVisible: boolean;
  onToggleMinimap: () => void;
  onReloadFromJson: () => void;
  onCopyJson: () => void;
};

export default function Toolbar(props: ToolbarProps) {
  const {
    graph,
    canUndo,
    canRedo,
    onUndo,
    onRedo,
    isFullscreen,
    onToggleFullscreen,
    minimapVisible,
    onToggleMinimap,
    onReloadFromJson,
    onCopyJson,
  } = props;

  return (
    <div
      style={{
        padding: '8px 12px',
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'space-between',
        borderBottom: '1px solid #e5e6eb',
        background: '#ffffff',
      }}
    >
      <Space size={8}>
        <Tooltip title="撤销（Ctrl+Z）">
          <Button size="small" disabled={!canUndo} onClick={onUndo}>
            撤销
          </Button>
        </Tooltip>
        <Tooltip title="重做（Ctrl+Shift+Z）">
          <Button size="small" disabled={!canRedo} onClick={onRedo}>
            重做
          </Button>
        </Tooltip>
        <Button
          size="small"
          onClick={() => {
            graph?.zoomToFit({ padding: 16, maxScale: 1 });
          }}
        >
          适配
        </Button>
        <Button
          size="small"
          onClick={() => {
            graph?.zoom(0.1);
          }}
        >
          放大
        </Button>
        <Button
          size="small"
          onClick={() => {
            graph?.zoom(-0.1);
          }}
        >
          缩小
        </Button>
      </Space>

      <Space size={8}>
        <Button size="small" onClick={onReloadFromJson}>
          从 JSON 刷新
        </Button>
        <Button size="small" onClick={onCopyJson}>
          复制 JSON
        </Button>
        <Button size="small" onClick={onToggleMinimap}>
          {minimapVisible ? '隐藏导航' : '显示导航'}
        </Button>
        <Button size="small" onClick={onToggleFullscreen}>
          {isFullscreen ? '退出全屏' : '全屏'}
        </Button>
      </Space>
    </div>
  );
}

