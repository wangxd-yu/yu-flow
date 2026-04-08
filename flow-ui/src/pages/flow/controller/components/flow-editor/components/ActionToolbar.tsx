import React from 'react';
import { Button, Tooltip, Divider, Space, Radio } from 'antd';
import {
  UndoOutlined,
  RedoOutlined,
  DeleteOutlined,
  ReloadOutlined,
  CopyOutlined,
  EyeOutlined,
  EyeInvisibleOutlined,
  CodeOutlined,
  NodeIndexOutlined,
  SaveOutlined,
  FormatPainterOutlined,
  FullscreenOutlined,
  FullscreenExitOutlined
} from '@ant-design/icons';

export type ActionToolbarProps = {
  canUndo: boolean;
  canRedo: boolean;
  onUndo: () => void;
  onRedo: () => void;
  onDelete: () => void;
  isFullscreen: boolean;
  onToggleFullscreen: () => void;
  minimapVisible: boolean;
  onToggleMinimap: () => void;
  onReloadFromJson: () => void;
  onCopyJson: () => void;
  mode: 'design' | 'code';
  onModeChange: (mode: 'design' | 'code') => void;
  onSave?: () => void;
  onCancel?: () => void;
  onFormat?: () => void;
};

export default function ActionToolbar(props: ActionToolbarProps) {
  const {
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
    mode,
    onModeChange,
    onSave,
    onFormat
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
      <Radio.Group
        value={mode}
        onChange={(e) => onModeChange(e.target.value)}
        optionType="button"
        buttonStyle="solid"
        size="small"
      >
        <Radio.Button value="design">
          <Space size={4}>
            <NodeIndexOutlined />
            设计模式
          </Space>
        </Radio.Button>
        <Radio.Button value="code">
          <Space size={4}>
            <CodeOutlined />
            代码模式
          </Space>
        </Radio.Button>
      </Radio.Group>

      <Space>
        {mode === 'code' && (
          <Tooltip title="格式化文档">
            <Button type="text" icon={<FormatPainterOutlined />} onClick={onFormat} />
          </Tooltip>
        )}
        <Tooltip title="从 JSON 刷新">
          <Button type="text" icon={<ReloadOutlined />} onClick={onReloadFromJson} />
        </Tooltip>
        <Tooltip title="复制 JSON">
          <Button type="text" icon={<CopyOutlined />} onClick={onCopyJson} />
        </Tooltip>
        <Tooltip title={minimapVisible ? "隐藏导航" : "显示导航"}>
          <Button type="text" icon={minimapVisible ? <EyeInvisibleOutlined /> : <EyeOutlined />} onClick={onToggleMinimap} />
        </Tooltip>
        <Divider type="vertical" />
        <Tooltip title="撤销 (Ctrl+Z)">
          <Button type="text" icon={<UndoOutlined />} disabled={!canUndo} onClick={onUndo} />
        </Tooltip>
        <Tooltip title="重做 (Ctrl+Shift+Z)">
          <Button type="text" icon={<RedoOutlined />} disabled={!canRedo} onClick={onRedo} />
        </Tooltip>

        {/* ── 全屏模式下显示保存按钮 ── */}
        {isFullscreen && (
          <>
            <Divider type="vertical" />
            <Button type="primary" onClick={onSave} icon={<SaveOutlined />}>保存</Button>
          </>
        )}
        <Tooltip title={isFullscreen ? "退出全屏" : "全屏"}>
          <Button type="text" style={{
            background: 'rgba(255,255,255,0.9)',
            boxShadow: '0 2px 8px rgba(0,0,0,0.1)',
            borderRadius: 6,
          }} icon={isFullscreen ? <FullscreenExitOutlined /> : <FullscreenOutlined />} onClick={onToggleFullscreen} />
        </Tooltip>
      </Space>
    </div>
  );
}
