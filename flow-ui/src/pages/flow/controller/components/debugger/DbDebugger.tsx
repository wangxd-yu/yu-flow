/**
 * DbDebugger.tsx
 * 数据库模式运行面板 — 参照逻辑编排 FlowDebugger：
 *   1. 底部悬浮工具栏（参数配置 / 运行）
 *   2. 右侧触发器面板（Headers / Query / Body）
 *   3. 底部运行控制台（SQL + 结果）
 */
import React, { useCallback, useEffect, useMemo, useState } from 'react';
import {
  Badge, Button, Empty, Input, Spin, Switch, Tabs, Tag, Tooltip, Typography, message,
} from 'antd';
import {
  CaretRightOutlined, CheckCircleFilled, CloseCircleFilled, CloseOutlined,
  ConsoleSqlOutlined, DeleteOutlined, DownOutlined, FileTextOutlined,
  LoadingOutlined, PlusOutlined, SettingOutlined, UpOutlined,
} from '@ant-design/icons';
import CodeEditor from '../flow-editor/components/CodeEditor';
import type { ExecutionLog, FlowTrace, RunStatus } from './FlowDebugger';
import { recordToKvEntries } from './apiTriggerPrefill';
import './FlowDebugger.less';

const { Text } = Typography;

interface KVEntry {
  key: string;
  value: string;
  enabled: boolean;
  id: string;
}

export interface DbDebuggerProps {
  sqlContent?: string;
  datasource?: string;
  responseType?: string;
  apiUrl?: string;
  apiMethod?: string;
  /** 预填 Headers（契约样例） */
  defaultTriggerHeaders?: Record<string, string>;
  /** 预填 Query（可含 Path 样例合并） */
  defaultTriggerQueryParams?: Record<string, string>;
  /** 预填 Body JSON */
  defaultTriggerBody?: string;
  /** 完整契约 JSON：开启「按契约校验」时提交给后端 */
  contractJson?: string;
  onRun?: (payload: {
    sqlContent: string;
    datasource?: string;
    responseType?: string;
    headers: Record<string, string>;
    queryParams: Record<string, string>;
    body: string;
    rollbackTransaction: boolean;
    contract?: string;
  }) => Promise<FlowTrace>;
}

let _seqId = 0;
const uid = () => `kv_${Date.now()}_${++_seqId}`;
const createEmptyKV = (): KVEntry => ({ key: '', value: '', enabled: true, id: uid() });

const prettyJson = (obj: any): string => {
  if (obj === undefined || obj === null) return '';
  if (typeof obj === 'string') {
    try { return JSON.stringify(JSON.parse(obj), null, 2); }
    catch { return obj; }
  }
  try { return JSON.stringify(obj, null, 2); }
  catch { return String(obj); }
};

const formatDuration = (ms: number): string => {
  if (ms < 1) return '<1ms';
  if (ms < 1000) return `${Math.round(ms)}ms`;
  return `${(ms / 1000).toFixed(1)}s`;
};

const KVEditor: React.FC<{
  entries: KVEntry[];
  onChange: (entries: KVEntry[]) => void;
  keyPlaceholder?: string;
  valuePlaceholder?: string;
}> = ({ entries, onChange, keyPlaceholder = 'Key', valuePlaceholder = 'Value' }) => {
  const update = (id: string, field: keyof KVEntry, val: any) => {
    onChange(entries.map((e) => (e.id === id ? { ...e, [field]: val } : e)));
  };
  return (
    <div className="pfd-kv-editor">
      {entries.map((entry) => (
        <div key={entry.id} className="pfd-kv-row">
          <Input
            size="small"
            placeholder={keyPlaceholder}
            value={entry.key}
            onChange={(e) => update(entry.id, 'key', e.target.value)}
            className="pfd-kv-input"
          />
          <Input
            size="small"
            placeholder={valuePlaceholder}
            value={entry.value}
            onChange={(e) => update(entry.id, 'value', e.target.value)}
            className="pfd-kv-input"
          />
          <Button
            type="text"
            size="small"
            danger
            icon={<DeleteOutlined />}
            onClick={() => onChange(entries.filter((e) => e.id !== entry.id))}
            className="pfd-kv-delete"
          />
        </div>
      ))}
      <Button
        type="dashed"
        size="small"
        block
        icon={<PlusOutlined />}
        onClick={() => onChange([...entries, createEmptyKV()])}
        className="pfd-kv-add"
      >
        添加
      </Button>
    </div>
  );
};

const StatusIcon: React.FC<{ status: ExecutionLog['status'] }> = ({ status }) => {
  switch (status) {
    case 'success':
      return <CheckCircleFilled style={{ color: '#52c41a', fontSize: 16 }} />;
    case 'error':
      return <CloseCircleFilled style={{ color: '#ff4d4f', fontSize: 16 }} />;
    case 'running':
      return <LoadingOutlined style={{ color: '#1677ff', fontSize: 16 }} spin />;
    default:
      return null;
  }
};

const JsonViewer: React.FC<{ data: any; emptyText?: string }> = ({
  data, emptyText = 'No data received',
}) => {
  if (data === undefined || data === null) {
    return (
      <div className="pfd-json-empty">
        <Text type="secondary">{emptyText}</Text>
      </div>
    );
  }
  return (
    <div className="pfd-json-viewer-container" style={{ margin: '8px 0' }}>
      <CodeEditor
        value={prettyJson(data)}
        onChange={() => {}}
        language="json"
        readOnly
        height="auto"
        maxHeight="300px"
      />
    </div>
  );
};

const DbDebugger: React.FC<DbDebuggerProps> = ({
  sqlContent = '',
  datasource,
  responseType,
  apiUrl = '',
  apiMethod = 'GET',
  defaultTriggerHeaders,
  defaultTriggerQueryParams,
  defaultTriggerBody,
  contractJson,
  onRun,
}) => {
  const [isTriggerPanelOpen, setIsTriggerPanelOpen] = useState(false);
  const [isConsoleOpen, setIsConsoleOpen] = useState(false);
  const [runningStatus, setRunningStatus] = useState<RunStatus>('idle');
  const [runTimestamp, setRunTimestamp] = useState('');
  const [executionLogs, setExecutionLogs] = useState<ExecutionLog[]>([]);
  const [selectedLogId, setSelectedLogId] = useState<string | null>(null);
  const [inspectorTab, setInspectorTab] = useState('output');
  const [triggerActiveTab, setTriggerActiveTab] = useState('params');
  const [triggerHeaders, setTriggerHeaders] = useState<KVEntry[]>(() =>
    recordToKvEntries(defaultTriggerHeaders, uid),
  );
  const [triggerParams, setTriggerParams] = useState<KVEntry[]>(() => {
    const base: Record<string, string> = { ...(defaultTriggerQueryParams || {}) };
    if (responseType === 'PAGE') {
      if (base.page == null) base.page = '0';
      if (base.size == null) base.size = '10';
    }
    return recordToKvEntries(base, uid);
  });
  const [triggerBody, setTriggerBody] = useState(
    defaultTriggerBody && defaultTriggerBody.trim() ? defaultTriggerBody : '{\n  \n}',
  );
  const [rollbackTransaction, setRollbackTransaction] = useState(true);
  const [validateAgainstContract, setValidateAgainstContract] = useState(true);
  const [consoleHeight] = useState(300);

  const isGetMethod = (apiMethod || 'GET').toUpperCase() === 'GET';

  useEffect(() => {
    if (isGetMethod && triggerActiveTab === 'body') {
      setTriggerActiveTab('params');
    }
  }, [isGetMethod, triggerActiveTab]);

  useEffect(() => {
    if (defaultTriggerHeaders == null) return;
    setTriggerHeaders(recordToKvEntries(defaultTriggerHeaders, uid));
  }, [defaultTriggerHeaders]);

  useEffect(() => {
    if (defaultTriggerQueryParams == null && responseType !== 'PAGE') return;
    const base: Record<string, string> = { ...(defaultTriggerQueryParams || {}) };
    if (responseType === 'PAGE') {
      if (base.page == null) base.page = '0';
      if (base.size == null) base.size = '10';
    }
    setTriggerParams(recordToKvEntries(base, uid));
  }, [defaultTriggerQueryParams, responseType]);

  useEffect(() => {
    if (defaultTriggerBody == null) return;
    setTriggerBody(defaultTriggerBody.trim() ? defaultTriggerBody : '{\n  \n}');
  }, [defaultTriggerBody]);

  const kvToRecord = useCallback((entries: KVEntry[]): Record<string, string> => {
    const result: Record<string, string> = {};
    entries.forEach((e) => {
      if (e.enabled && e.key.trim()) result[e.key.trim()] = e.value;
    });
    return result;
  }, []);

  const selectedLog = useMemo(
    () => executionLogs.find((l) => l.id === selectedLogId) || null,
    [executionLogs, selectedLogId],
  );

  const stats = useMemo(() => {
    const success = executionLogs.filter((l) => l.status === 'success').length;
    const errors = executionLogs.filter((l) => l.status === 'error').length;
    return { success, errors, total: executionLogs.length };
  }, [executionLogs]);

  const handleRun = useCallback(async () => {
    if (!sqlContent?.trim()) {
      message.warning('请先编写 SQL');
      return;
    }
    if (!datasource) {
      message.warning('请选择数据源');
      return;
    }
    if (!responseType) {
      message.warning('请选择响应类型');
      return;
    }

    setRunningStatus('running');
    setIsConsoleOpen(true);
    setExecutionLogs([]);
    setSelectedLogId(null);
    setRunTimestamp(new Date().toLocaleTimeString());
    setInspectorTab('output');

    const payload = {
      sqlContent,
      datasource,
      responseType,
      headers: kvToRecord(triggerHeaders),
      queryParams: kvToRecord(triggerParams),
      body: isGetMethod ? '' : triggerBody,
      rollbackTransaction,
      contract: (validateAgainstContract && contractJson) ? contractJson : undefined,
    };

    try {
      if (!onRun) {
        throw new Error('未配置运行回调');
      }
      const trace = await onRun(payload);
      const logs = trace?.stepLogs || [];
      setExecutionLogs(logs);
      setRunningStatus(trace?.status === 'error' ? 'error' : 'finished');
      if (logs.length > 0) {
        setSelectedLogId(logs[0].id);
        setInspectorTab(logs[0].status === 'error' ? 'details' : 'output');
      }
    } catch (err: any) {
      setRunningStatus('error');
      const status = err?.response?.status ?? err?.status;
      const raw = err?.message || 'Unknown execution error';
      // 调试走 /flow-api/api/debug/db/run，与是否发布无关；404 多为后端未重启加载新接口
      const errorMsg = status === 404 || /status code 404/i.test(raw)
        ? '调试接口不存在（404）：请确认后端已重启并包含 POST /flow-api/api/debug/db/run。未发布接口也可以测试运行，不依赖发布状态。'
        : raw;
      const errLog: ExecutionLog = {
        id: 'err_global',
        nodeId: '__global__',
        nodeName: 'Global Error',
        nodeType: 'error',
        status: 'error',
        startTime: new Date().toLocaleTimeString(),
        duration: 0,
        error: errorMsg,
      };
      setExecutionLogs([errLog]);
      setSelectedLogId('err_global');
      setInspectorTab('details');
    }
  }, [
    sqlContent, datasource, responseType, isGetMethod, rollbackTransaction,
    validateAgainstContract, contractJson,
    triggerHeaders, triggerParams, triggerBody, onRun, kvToRecord,
  ]);

  const toolbarBottom = isConsoleOpen ? consoleHeight + 16 : 24;

  return (
    <div className="pfd-root">
      {/* 底部悬浮工具栏 */}
      <div className="pfd-floating-toolbar" style={{ bottom: toolbarBottom }}>
        <div className="pfd-toolbar-right" style={{ marginLeft: 0 }}>
          <Tooltip title="触发器参数配置">
            <button
              className={`pfd-tool-btn ${isTriggerPanelOpen ? 'pfd-tool-btn--active' : ''}`}
              onClick={() => setIsTriggerPanelOpen((v) => !v)}
            >
              <SettingOutlined />
            </button>
          </Tooltip>
          <button
            className={`pfd-run-btn ${runningStatus === 'running' ? 'pfd-run-btn--running' : ''}`}
            onClick={handleRun}
            disabled={runningStatus === 'running'}
            style={{ marginLeft: 8 }}
          >
            {runningStatus === 'running' ? (
              <>
                <LoadingOutlined spin />
                <span>运行中...</span>
              </>
            ) : (
              <>
                <CaretRightOutlined />
                <span>运行</span>
              </>
            )}
          </button>
          {(runningStatus === 'finished' || runningStatus === 'error') && (
            <Tooltip title={`${stats.success} 成功 / ${stats.errors} 失败`}>
              <button
                className="pfd-tool-btn pfd-console-toggle"
                onClick={() => setIsConsoleOpen((v) => !v)}
              >
                <ConsoleSqlOutlined />
                {stats.errors > 0 && (
                  <Badge count={stats.errors} size="small" offset={[-2, -2]} className="pfd-error-badge" />
                )}
              </button>
            </Tooltip>
          )}
        </div>
      </div>

      {/* 触发器面板 */}
      {isTriggerPanelOpen && (
        <div className={`pfd-trigger-panel ${isConsoleOpen ? 'pfd-trigger-panel--console-open' : ''}`}>
          <div className="pfd-trigger-header">
            <div className="pfd-trigger-title">
              <SettingOutlined style={{ marginRight: 8, color: '#1677ff' }} />
              <span>触发器配置</span>
            </div>
            <button className="pfd-close-btn" onClick={() => setIsTriggerPanelOpen(false)}>
              <CloseOutlined />
            </button>
          </div>

          <div className="pfd-trigger-api-info">
            <Tag
              color={
                apiMethod === 'GET' ? 'blue'
                  : apiMethod === 'POST' ? 'green'
                    : apiMethod === 'PUT' ? 'orange'
                      : apiMethod === 'DELETE' ? 'red'
                        : 'default'
              }
              style={{ fontWeight: 700, fontFamily: 'monospace', fontSize: 11 }}
            >
              {apiMethod}
            </Tag>
            <Text
              code
              style={{ fontSize: 12, maxWidth: 220, overflow: 'hidden', textOverflow: 'ellipsis' }}
            >
              {apiUrl || '未设置路径'}
            </Text>
            {responseType && <Tag style={{ marginLeft: 4 }}>{responseType}</Tag>}
          </div>

          <Tabs
            activeKey={triggerActiveTab}
            onChange={setTriggerActiveTab}
            size="small"
            className="pfd-trigger-tabs"
            items={[
              {
                key: 'headers',
                label: (
                  <span>
                    Headers
                    {triggerHeaders.filter((h) => h.key.trim()).length > 0 && (
                      <Badge
                        count={triggerHeaders.filter((h) => h.key.trim()).length}
                        size="small"
                        style={{ marginLeft: 6, backgroundColor: '#e6f4ff', color: '#1677ff' }}
                      />
                    )}
                  </span>
                ),
                children: (
                  <KVEditor
                    entries={triggerHeaders}
                    onChange={setTriggerHeaders}
                    keyPlaceholder="Header Name"
                    valuePlaceholder="Header Value"
                  />
                ),
              },
              {
                key: 'params',
                label: (
                  <span>
                    Query Params
                    {triggerParams.filter((p) => p.key.trim()).length > 0 && (
                      <Badge
                        count={triggerParams.filter((p) => p.key.trim()).length}
                        size="small"
                        style={{ marginLeft: 6, backgroundColor: '#e6f4ff', color: '#1677ff' }}
                      />
                    )}
                  </span>
                ),
                children: (
                  <KVEditor
                    entries={triggerParams}
                    onChange={setTriggerParams}
                    keyPlaceholder="Param Key"
                    valuePlaceholder="Param Value"
                  />
                ),
              },
              ...(!isGetMethod ? [{
                key: 'body',
                label: 'Body',
                children: (
                  <CodeEditor
                    value={triggerBody}
                    onChange={setTriggerBody}
                    language="json"
                    height="auto"
                    maxHeight="400px"
                    className="pfd-body-editor"
                  />
                ),
              }] : []),
            ]}
          />

          <div className="pfd-trigger-option">
            <div className="pfd-trigger-option-row">
              <span>事务回退</span>
              <Switch
                size="small"
                checked={rollbackTransaction}
                onChange={setRollbackTransaction}
              />
            </div>
            <Text className="pfd-trigger-option-desc">
              默认开启：调试执行结束后自动回滚事务，INSERT / UPDATE / DELETE 不会真正写入数据库，便于安全试跑。
              关闭后按正式接口逻辑提交，请谨慎使用。
            </Text>
          </div>

          {!!contractJson && (
            <div className="pfd-trigger-option">
              <div className="pfd-trigger-option-row">
                <span>按契约校验</span>
                <Switch
                  size="small"
                  checked={validateAgainstContract}
                  onChange={setValidateAgainstContract}
                />
              </div>
              <Text className="pfd-trigger-option-desc">
                开启后，运行前按当前草稿契约校验 Headers / Query / Path / Body（与网关一致）。
              </Text>
            </div>
          )}

          <div className="pfd-trigger-footer">
            <Button
              type="primary"
              icon={<CaretRightOutlined />}
              block
              onClick={handleRun}
              loading={runningStatus === 'running'}
              className="pfd-trigger-send-btn"
            >
              运行
            </Button>
          </div>
        </div>
      )}

      {/* 运行控制台 */}
      <div
        className={`pfd-console ${isConsoleOpen ? 'pfd-console--open' : ''}`}
        style={isConsoleOpen ? { height: consoleHeight } : undefined}
      >
        <div className="pfd-console-header">
          <div className="pfd-console-header-left">
            <Text strong style={{ fontSize: 13 }}>运行日志</Text>
            {runTimestamp && <Tag className="pfd-timestamp-tag">{runTimestamp}</Tag>}
          </div>
          <div className="pfd-console-header-right">
            {runningStatus === 'running' && <Spin size="small" style={{ marginRight: 12 }} />}
            {runningStatus === 'finished' && (
              <Tag color="success" style={{ marginRight: 8 }}>
                ✅ {stats.success}/{stats.total} 成功
              </Tag>
            )}
            {runningStatus === 'error' && (
              <Tag color="error" style={{ marginRight: 8 }}>
                ❌ {stats.errors} 失败
              </Tag>
            )}
            <Tooltip title={isConsoleOpen ? '收起控制台' : '展开控制台'}>
              <button className="pfd-close-btn" onClick={() => setIsConsoleOpen((v) => !v)}>
                {isConsoleOpen ? <DownOutlined /> : <UpOutlined />}
              </button>
            </Tooltip>
            <Tooltip title="关闭控制台">
              <button
                className="pfd-close-btn"
                onClick={() => setIsConsoleOpen(false)}
                style={{ marginLeft: 4 }}
              >
                <CloseOutlined />
              </button>
            </Tooltip>
          </div>
        </div>

        <div className="pfd-console-body">
          <div className="pfd-exec-list">
            <div className="pfd-exec-list-header">
              <Text type="secondary" style={{ fontSize: 12 }}>执行步骤</Text>
            </div>
            <div className="pfd-exec-list-scroll">
              {executionLogs.length === 0 && (
                <div className="pfd-exec-empty">
                  <Empty
                    image={Empty.PRESENTED_IMAGE_SIMPLE}
                    description={runningStatus === 'running' ? '正在执行中...' : '点击运行开始调试'}
                  />
                </div>
              )}
              {executionLogs.map((log) => (
                <div
                  key={log.id}
                  className={`pfd-exec-item ${selectedLogId === log.id ? 'pfd-exec-item--active' : ''}`}
                  onClick={() => setSelectedLogId(log.id)}
                >
                  <div className="pfd-exec-item-left">
                    <StatusIcon status={log.status} />
                    <div className="pfd-exec-item-info">
                      <Text
                        strong
                        className="pfd-exec-item-name"
                        style={log.status === 'error' ? { color: '#ff4d4f' } : undefined}
                      >
                        {log.nodeName}
                      </Text>
                    </div>
                  </div>
                  <div className="pfd-exec-item-right">
                    <Text type="secondary" className="pfd-exec-item-time">{log.startTime}</Text>
                    <Text type="secondary" className="pfd-exec-item-duration">
                      • {formatDuration(log.duration)}
                    </Text>
                  </div>
                </div>
              ))}
            </div>
          </div>

          <div className="pfd-inspector">
            {!selectedLog ? (
              <div className="pfd-inspector-empty">
                <FileTextOutlined style={{ fontSize: 40, color: '#d9d9d9', marginBottom: 12 }} />
                <Text type="secondary">选择左侧节点查看详情</Text>
              </div>
            ) : (
              <Tabs
                activeKey={inspectorTab}
                onChange={setInspectorTab}
                size="small"
                className="pfd-inspector-tabs"
                items={[
                  {
                    key: 'input',
                    label: '输入',
                    children: <JsonViewer data={selectedLog.inputs} />,
                  },
                  ...(selectedLog.nodeType === 'database' ? [{
                    key: 'sql',
                    label: 'SQL',
                    children: (
                      <div className="pfd-details-content" style={{ padding: '12px' }}>
                        <Text type="secondary" style={{ marginBottom: 8, display: 'block' }}>
                          实际执行的 SQL 语句（仅用于展示）：
                        </Text>
                        <pre style={{
                          whiteSpace: 'pre-wrap',
                          wordBreak: 'break-all',
                          color: '#08979c',
                          backgroundColor: '#e6fffb',
                          border: '1px solid #87e8de',
                          borderRadius: 6,
                          padding: 12,
                          fontFamily: 'monospace',
                        }}>
                          {selectedLog.inputs?.actualSql || '未获取到实际执行的 SQL，请重新运行'}
                        </pre>
                      </div>
                    ),
                  }] : []),
                  {
                    key: 'output',
                    label: '输出',
                    children: <JsonViewer data={selectedLog.outputs} />,
                  },
                  {
                    key: 'details',
                    label: (
                      <span>
                        详情
                        {selectedLog.error && (
                          <Badge dot status="error" style={{ marginLeft: 4 }} />
                        )}
                      </span>
                    ),
                    children: (
                      <div className="pfd-details-content">
                        <div className="pfd-detail-row">
                          <Text type="secondary">类型:</Text>
                          <Tag>{selectedLog.nodeType}</Tag>
                        </div>
                        <div className="pfd-detail-row">
                          <Text type="secondary">状态:</Text>
                          <StatusIcon status={selectedLog.status} />
                          <Text style={{ marginLeft: 4 }}>{selectedLog.status}</Text>
                        </div>
                        <div className="pfd-detail-row">
                          <Text type="secondary">耗时:</Text>
                          <Text>{formatDuration(selectedLog.duration)}</Text>
                        </div>
                        {selectedLog.error && (
                          <div className="pfd-error-block">
                            <Text type="danger" strong style={{ display: 'block', marginBottom: 4 }}>
                              错误:
                            </Text>
                            <pre className="pfd-error-text">{selectedLog.error}</pre>
                          </div>
                        )}
                      </div>
                    ),
                  },
                ]}
              />
            )}
          </div>
        </div>
      </div>
    </div>
  );
};

export default React.memo(DbDebugger);
