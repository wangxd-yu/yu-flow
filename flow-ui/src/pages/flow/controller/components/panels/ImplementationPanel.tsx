/**
 * ImplementationPanel.tsx
 * ─────────────────────────────────────────────────────────────────────────────
 * 「服务实现」面板 — 从 ControllerForm God Component 中提取
 *
 * 职责：引擎模式切换 (FLOW / DB / JSON / STRING) + 各引擎编辑器的按需渲染
 * ─────────────────────────────────────────────────────────────────────────────
 */
import React, { useCallback, useEffect, useRef, useState } from 'react';
import { Button, Flex, message, Select, Segmented, Space, Tooltip } from 'antd';
import type { FormInstance } from 'antd';
import {
  FullscreenOutlined, FullscreenExitOutlined,
  FileTextOutlined, CodeOutlined, DatabaseOutlined,
  ApartmentOutlined, AlignLeftOutlined,
} from '@ant-design/icons';
import { format } from 'sql-formatter';
import FlowEditor from '../FlowEditor';
import CodeEditor from '../flow-editor/components/CodeEditor';
import { queryDataSourceList } from '@/pages/flow/dataSource/services/dataSource';

// ═══════════════════════════════════════════════════════════════════════════
//  类型定义
// ═══════════════════════════════════════════════════════════════════════════

export type EngineMode = 'FLOW' | 'DB' | 'JSON' | 'STRING';

export interface ImplementationPanelProps {
  // ── 引擎模式 ──
  engineMode: EngineMode;
  onEngineModeChange: (mode: EngineMode) => void;

  // ── 4 个隔离的内容 State ──
  dslContent: string;
  onDslContentChange: (v: string) => void;
  sqlContent: string;
  onSqlContentChange: (v: string) => void;
  jsonContent: string;
  onJsonContentChange: (v: string) => void;
  textContent: string;
  onTextContentChange: (v: string) => void;

  // ── DB 模式特有 ──
  dbDatasource: string | undefined;
  onDbDatasourceChange: (v: string | undefined) => void;
  responseType: string | undefined;
  onResponseTypeChange: (v: string | undefined) => void;

  // ── FlowEditor 相关 ──
  form: FormInstance;
  isEdit: boolean;
  onSave: (script?: any) => void;
  onCancel: () => void;
  addonDebugger?: React.ReactNode;
}

const ENGINE_MODE_OPTIONS: { label: string; value: EngineMode; icon: React.ReactNode }[] = [
  { label: '逻辑编排', value: 'FLOW', icon: <ApartmentOutlined /> },
  { label: '数据库', value: 'DB', icon: <DatabaseOutlined /> },
  { label: '静态 JSON', value: 'JSON', icon: <CodeOutlined /> },
  { label: '静态文本', value: 'STRING', icon: <FileTextOutlined /> },
];

// ═══════════════════════════════════════════════════════════════════════════
//  组件实现
// ═══════════════════════════════════════════════════════════════════════════

const ImplementationPanel: React.FC<ImplementationPanelProps> = ({
  engineMode, onEngineModeChange,
  dslContent, onDslContentChange,
  sqlContent, onSqlContentChange,
  jsonContent, onJsonContentChange,
  textContent, onTextContentChange,
  dbDatasource, onDbDatasourceChange,
  responseType, onResponseTypeChange,
  form, isEdit, onSave, onCancel, addonDebugger,
}) => {
  // ─── 全屏状态 ──────────────────────────────────────────────────────
  const [isFullscreen, setIsFullscreen] = useState(false);
  const flowContainerRef = useRef<HTMLDivElement>(null);

  // ─── 数据源列表 ────────────────────────────────────────────────────
  const [dataSources, setDataSources] = useState<{ label: string; value: string }[]>([]);

  useEffect(() => {
    queryDataSourceList()
      .then((res) => {
        const mapped = res.map((item: any) => ({ label: item.name, value: item.code }));
        setDataSources(mapped);
      })
      .catch(() => { });
  }, []);

  // ─── 全屏逻辑 ──────────────────────────────────────────────────────
  const toggleFullscreen = useCallback(() => {
    const el = flowContainerRef.current;
    if (!el) return;
    if (!document.fullscreenElement) {
      el.requestFullscreen?.().catch(() => setIsFullscreen(true));
    } else {
      document.exitFullscreen?.();
    }
  }, []);

  useEffect(() => {
    const handleFullscreenChange = () => setIsFullscreen(!!document.fullscreenElement);
    document.addEventListener('fullscreenchange', handleFullscreenChange);
    return () => document.removeEventListener('fullscreenchange', handleFullscreenChange);
  }, []);

  // ─── 格式化操作 ────────────────────────────────────────────────────
  const formatContent = useCallback((type: 'DB' | 'JSON') => {
    if (type === 'JSON') {
      try {
        if (!jsonContent) return;
        const parsed = JSON.parse(jsonContent);
        onJsonContentChange(JSON.stringify(parsed, null, 2));
        message.success('JSON 格式化成功');
      } catch (e) {
        console.error('JSON 格式化失败:', e);
        message.error('JSON 格式拼写有误，无法格式化');
      }
    } else if (type === 'DB') {
      if (!sqlContent) return;
      try {
        const formatted = format(sqlContent, {
          language: 'sql',
          tabWidth: 2,
          paramTypes: {
            custom: [{ regex: String.raw`\$\{[^}]+\}` }]
          }
        });
        onSqlContentChange(formatted);
        message.success('SQL 格式化完成');
      } catch (e) {
        console.error('SQL 格式化失败:', e);
        message.error('SQL 语法有误，无法格式化');
      }
    }
  }, [jsonContent, sqlContent, onJsonContentChange, onSqlContentChange]);

  // ─── Segmented 选项 ────────────────────────────────────────────────
  const engineSegmentedOptions = ENGINE_MODE_OPTIONS.map((opt) => ({
    label: (
      <Space size={4}>
        {opt.icon}
        <span>{opt.label}</span>
      </Space>
    ),
    value: opt.value,
  }));

  // ─── 渲染 ──────────────────────────────────────────────────────────
  return (
    <div style={{ display: 'flex', flexDirection: 'column', height: 'calc(100vh - 180px)' }}>
      {/* 引擎模式切换器 */}
      <Segmented
        block
        size="large"
        value={engineMode}
        onChange={(val) => onEngineModeChange(val as EngineMode)}
        options={engineSegmentedOptions}
        style={{ marginBottom: 8, flexShrink: 0 }}
      />

      {/* 引擎内容区 — 按需受控渲染 */}
      <div style={{ flex: 1, minHeight: 0, position: 'relative' }}>

        {/* ── 逻辑编排模式 ── */}
        {engineMode === 'FLOW' && (
          <div
            ref={flowContainerRef}
            style={{
              height: '100%',
              position: 'absolute',
              top: 0, left: 0, right: 0, bottom: 0,
              overflow: 'hidden',
              background: '#fff',
              zIndex: 10,
              ...(isFullscreen && !document.fullscreenElement ? {
                position: 'fixed',
                top: 0, left: 0,
                width: '100vw', height: '100vh',
                zIndex: 9999,
              } : {}),
            }}
          >

            <FlowEditor
              value={dslContent}
              onChange={onDslContentChange}
              globalForm={form}
              onSave={onSave}
              onCancel={onCancel}
              isEdit={isEdit}
              height={'100%' as any}
              addonDebugger={addonDebugger}
            />
          </div>
        )}

        {/* ── 数据库模式 ── */}
        {engineMode === 'DB' && (
          <Flex
            vertical
            style={{
              height: '100%', position: 'absolute',
              top: 0, left: 0, right: 0, bottom: 0,
              background: '#fff', zIndex: 10,
            }}
            gap={8}
          >
            <Flex gap={12} align="center" style={{ flexShrink: 0 }}>
              <Select
                placeholder="请选择数据源"
                options={dataSources}
                value={dbDatasource || undefined}
                onChange={onDbDatasourceChange}
                allowClear={false}
                showSearch
                optionFilterProp="label"
                style={{ width: 240 }}
              />
              <Select
                placeholder="响应类型"
                value={responseType || undefined}
                onChange={onResponseTypeChange}
                allowClear={false}
                style={{ width: 200 }}
                options={[
                  { label: '分页 (PAGE)', value: 'PAGE' },
                  { label: '列表 (LIST)', value: 'LIST' },
                  { label: '对象 (OBJECT)', value: 'OBJECT' },
                  { label: '更新 (UPDATE)', value: 'UPDATE' },
                ]}
              />
              <div style={{ flex: 1 }} />
              <Tooltip title="一键格式化 SQL 代码">
                <Button icon={<AlignLeftOutlined />} onClick={() => formatContent('DB')}>格式化</Button>
              </Tooltip>
            </Flex>
            <div style={{ flex: 1, minHeight: 0, position: 'relative' }}>
              <CodeEditor
                value={sqlContent}
                onChange={onSqlContentChange}
                language="sql"
                height="100%"
                style={{ position: 'absolute', top: 0, left: 0, right: 0, bottom: 0 }}
              />
            </div>
          </Flex>
        )}

        {/* ── 静态 JSON 模式 ── */}
        {engineMode === 'JSON' && (
          <Flex
            vertical gap={8}
            style={{
              height: '100%', position: 'absolute',
              top: 0, left: 0, right: 0, bottom: 0,
              background: '#fff', zIndex: 10,
            }}
          >
            <Flex justify="flex-end" style={{ flexShrink: 0 }}>
              <Tooltip title="格式化并校验 JSON">
                <Button icon={<AlignLeftOutlined />} onClick={() => formatContent('JSON')}>格式化</Button>
              </Tooltip>
            </Flex>
            <div style={{ flex: 1, minHeight: 0, position: 'relative' }}>
              <CodeEditor
                value={jsonContent}
                onChange={onJsonContentChange}
                language="json"
                height="100%"
                style={{ position: 'absolute', top: 0, left: 0, right: 0, bottom: 0 }}
              />
            </div>
          </Flex>
        )}

        {/* ── 静态文本模式 ── */}
        {engineMode === 'STRING' && (
          <div style={{
            height: '100%', position: 'absolute',
            top: 0, left: 0, right: 0, bottom: 0,
            background: '#fff', zIndex: 10,
          }}>
            <CodeEditor
              value={textContent}
              onChange={onTextContentChange}
              language="text"
              height="100%"
              style={{ position: 'absolute', top: 0, left: 0, right: 0, bottom: 0 }}
            />
          </div>
        )}
      </div>
    </div>
  );
};

export default React.memo(ImplementationPanel);
