/**
 * ImplementationPanel.tsx
 * ─────────────────────────────────────────────────────────────────────────────
 * 「服务实现」面板 — 从 ControllerForm God Component 中提取
 *
 * 职责：引擎模式切换 (FLOW / DB / JSON / STRING) + 各引擎编辑器的按需渲染
 * ─────────────────────────────────────────────────────────────────────────────
 */
import React, { useCallback, useEffect, useRef, useState } from 'react';
import { Alert, Button, Flex, Typography, message, Select, Segmented, Space, Tooltip } from 'antd';
import type { FormInstance } from 'antd';
import {
  FullscreenOutlined, FullscreenExitOutlined,
  FileTextOutlined, CodeOutlined, DatabaseOutlined,
  ApartmentOutlined, AlignLeftOutlined,
} from '@ant-design/icons';
import { format } from 'sql-formatter';
import FlowEditor from '@/components/flow/FlowEditor';
import CodeEditor from '@/components/flow/flow-editor/components/CodeEditor';
import { DbDebugger } from '@/components/flow/debugger';
import { debugRunDbApiConfig } from '@/services/flow/flowController';
import { queryDataSourceList } from '@/services/flow/dataSource';

const { Text } = Typography;

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
  apiUrl?: string;
  apiMethod?: string;
  apiId?: string;
  apiName?: string;
  /** 契约样例预填（Headers） */
  defaultTriggerHeaders?: Record<string, string>;
  /** 契约样例预填（Query + Path） */
  defaultTriggerQueryParams?: Record<string, string>;
  /** 契约样例预填 Body */
  defaultTriggerBody?: string;
  /** 完整契约 JSON（调试可选校验） */
  contractJson?: string;
}

const ENGINE_MODE_OPTIONS: { label: string; value: EngineMode; icon: React.ReactNode }[] = [
  { label: '逻辑编排', value: 'FLOW', icon: <ApartmentOutlined /> },
  { label: '数据库', value: 'DB', icon: <DatabaseOutlined /> },
  { label: '静态 JSON', value: 'JSON', icon: <CodeOutlined /> },
  { label: '静态文本', value: 'STRING', icon: <FileTextOutlined /> },
];

/** 校验静态 JSON；合法返回 null，否则返回错误文案 */
export function getStaticJsonError(content?: string): string | null {
  const raw = content ?? '';
  if (!raw.trim()) {
    return '静态 JSON 内容不能为空';
  }
  try {
    JSON.parse(raw);
    return null;
  } catch (e: any) {
    const detail = typeof e?.message === 'string' ? e.message : '语法错误';
    return `JSON 格式不正确：${detail}`;
  }
}

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
  form, isEdit, onSave, onCancel, apiUrl, apiMethod, apiId, apiName,
  defaultTriggerHeaders, defaultTriggerQueryParams, defaultTriggerBody, contractJson,
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

  const jsonError = engineMode === 'JSON' ? getStaticJsonError(jsonContent) : null;

  // ─── Segmented 选项 ────────────────────────────────────────────────
  const engineSegmentedOptions = ENGINE_MODE_OPTIONS.map((opt) => ({
    label: (
      <Space size={2}>
        {opt.icon}
        <span>{opt.label}</span>
      </Space>
    ),
    value: opt.value,
  }));

  const engineModeSwitcher = (
    <Segmented
      size="small"
      value={engineMode}
      onChange={(val) => onEngineModeChange(val as EngineMode)}
      options={engineSegmentedOptions}
    />
  );

  // ─── 渲染 ──────────────────────────────────────────────────────────
  return (
    <div style={{ display: 'flex', flexDirection: 'column', height: '100%', minHeight: 0 }}>
      {/* 非 FLOW 模式：引擎切换单独一行；FLOW 模式并入画布工具条，少占一行 */}
      {engineMode !== 'FLOW' && (
        <div style={{ flexShrink: 0, padding: '4px 0' }}>
          {engineModeSwitcher}
        </div>
      )}

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
              apiUrl={apiUrl}
              apiMethod={apiMethod}
              apiId={apiId}
              apiName={apiName}
              defaultTriggerHeaders={defaultTriggerHeaders}
              defaultTriggerQueryParams={defaultTriggerQueryParams}
              defaultTriggerBody={defaultTriggerBody}
              contractJson={contractJson}
              toolbarLeadingExtra={engineModeSwitcher}
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
              <Text type="secondary" style={{ fontSize: 12, maxWidth: 420 }}>
                动态参数请用 <Text code>${'{name}'}</Text>；数组 Body 可展开为
                {' '}<Text code>IN (?, ?, ?)</Text>
                。勿使用 MyBatis 风格的 <Text code>#{'{name}'}</Text>。
              </Text>
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
              <DbDebugger
                sqlContent={sqlContent}
                datasource={dbDatasource}
                responseType={responseType}
                apiUrl={apiUrl}
                apiMethod={apiMethod}
                defaultTriggerHeaders={defaultTriggerHeaders}
                defaultTriggerQueryParams={defaultTriggerQueryParams}
                defaultTriggerBody={defaultTriggerBody}
                contractJson={contractJson}
                onRun={async (payload) => {
                  const result = await debugRunDbApiConfig({
                    ...payload,
                    sourceRef: apiId,
                    sourceName: apiName,
                  });
                  if (result?.code === 0 && result.data) {
                    return result.data;
                  }
                  if (result?.data) {
                    return result.data;
                  }
                  if (result?.traceId) {
                    return result;
                  }
                  throw new Error(result?.msg || 'Run failed');
                }}
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
            <div style={{ flexShrink: 0 }}>
              {jsonError ? (
                <Alert type="error" showIcon banner message={jsonError} style={{ padding: '4px 12px' }} />
              ) : (
                <Text type="secondary" style={{ fontSize: 12 }}>JSON 格式校验通过</Text>
              )}
            </div>
            <div
              style={{
                flex: 1,
                minHeight: 0,
                position: 'relative',
                border: jsonError ? '1px solid #ff4d4f' : '1px solid transparent',
                borderRadius: 6,
                overflow: 'hidden',
              }}
            >
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
