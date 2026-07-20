/**
 * FlowDebugger.tsx
 * ═══════════════════════════════════════════════════════════════════════════
 * Yu Flow · 沉浸式运行调试面板 (Run & Debug)
 *
 * 架构定位：内置于 FlowEditor 的调试器面板。
 * 渲染在 FlowEditor 画布区域的 position: relative 容器之上（absolute overlay）。
 *
 * 三大核心区域：
 *   1. FloatingToolbar   — 底部悬浮胶囊工具栏（缩放 / 撤销 / Run）
 *   2. TriggerPanel      — 右侧无遮罩悬浮卡片（Headers / Query / Body 输入）
 *   3. RunConsole         — 底部弹出式控制台（执行列表 + 节点详情 Inspector）
 *
 * 状态管理：完全内聚，不污染开源内核的状态树。
 * ═══════════════════════════════════════════════════════════════════════════
 */

import React, { useState, useCallback, useMemo, useRef, useEffect } from 'react';
import {
  Button, Tooltip, Badge, Tag, Tabs, Empty, Spin,
  Input, Select, Collapse, Space, Typography,
} from 'antd';
import {
  CaretRightOutlined, UndoOutlined, RedoOutlined,
  ZoomInOutlined, ZoomOutOutlined, CompressOutlined,
  SettingOutlined, CloseOutlined, ConsoleSqlOutlined,
  CheckCircleFilled, CloseCircleFilled, LoadingOutlined,
  ClockCircleOutlined, UpOutlined, DownOutlined,
  PlusOutlined, DeleteOutlined,
  FileTextOutlined, BugOutlined, StepForwardOutlined, StopOutlined,
} from '@ant-design/icons';
import './FlowDebugger.less';
import CodeEditor from '../flow-editor/components/CodeEditor';

const { Text, Title } = Typography;
const { TextArea } = Input;
const { TabPane } = Tabs;

// ═══════════════════════════════════════════════════════════════════════════
//  类型定义
// ═══════════════════════════════════════════════════════════════════════════

export type RunStatus = 'idle' | 'running' | 'finished' | 'error';

/** 单个节点的执行日志 */
export interface ExecutionLog {
  id: string;
  nodeId: string;
  nodeName: string;
  nodeType: string;
  status: 'success' | 'error' | 'running' | 'skipped';
  startTime: string;
  duration: number;       // ms
  inputs?: Record<string, any>;
  outputs?: Record<string, any>;
  error?: string;
}

/** 完整执行追踪快照 */
export interface FlowTrace {
  traceId: string;
  startTime: number;
  endTime: number;
  totalDurationMs: number;
  status: 'success' | 'error';
  errorMsg?: string;
  globalInputs?: Record<string, any>;
  globalOutputs?: any;
  stepLogs: ExecutionLog[];
}

/** 键值对条目（Headers / Query Params） */
interface KVEntry {
  key: string;
  value: string;
  enabled: boolean;
  id: string;
}

export interface FlowDebuggerProps {
  /** 当前画布的 DSL JSON 字符串 */
  dslContent?: string;
  /** 当前 API 的 URL（展示用） */
  apiUrl?: string;
  /** 当前 API 的 HTTP Method */
  apiMethod?: string;
  /** 画布操作回调 */
  onZoomIn?: () => void;
  onZoomOut?: () => void;
  onFitView?: () => void;
  onUndo?: () => void;
  onRedo?: () => void;
  canUndo?: boolean;
  canRedo?: boolean;
  /** 触发运行回调：将 trigger 参数发给后端执行 */
  onRun?: (payload: {
    dslContent: string;
    headers: Record<string, string>;
    queryParams: Record<string, string>;
    body: string;
  }) => Promise<FlowTrace>;
  /** 控制台打开/关闭回调，供父容器感知高度变化 */
  onConsoleOpenChange?: (open: boolean) => void;
  /** 选中日志条目改变时 */
  onSelectedLogChange?: (nodeId: string | null) => void;
  /** 运行日志列表更新时 */
  onExecutionLogsChange?: (logs: ExecutionLog[]) => void;
  /** 只读回放模式下的 Trace 数据，如果有传入，则表示当前为回放模式 */
  playbackTrace?: FlowTrace | null;
  /** 触发调试回调 */
  onDebugStart?: (payload: {
    dslContent: string;
    headers: Record<string, string>;
    queryParams: Record<string, string>;
    body: string;
    breakpoints: string[];
  }) => Promise<{ sessionId: string }>;
  onDebugStatus?: (sessionId: string) => Promise<any>;
  onDebugResume?: (sessionId: string, inputs?: any) => Promise<void>;
  onDebugCancel?: (sessionId: string) => Promise<void>;
  breakpoints?: string[];
}

// ═══════════════════════════════════════════════════════════════════════════
//  工具函数
// ═══════════════════════════════════════════════════════════════════════════

let _seqId = 0;
const uid = () => `kv_${Date.now()}_${++_seqId}`;

const createEmptyKV = (): KVEntry => ({
  key: '', value: '', enabled: true, id: uid(),
});

/** 安全格式化 JSON 字符串 */
const prettyJson = (obj: any): string => {
  if (obj === undefined || obj === null) return '';
  if (typeof obj === 'string') {
    try { return JSON.stringify(JSON.parse(obj), null, 2); }
    catch { return obj; }
  }
  try { return JSON.stringify(obj, null, 2); }
  catch { return String(obj); }
};

/** 格式化耗时 */
const formatDuration = (ms: number): string => {
  if (ms < 1) return '<1ms';
  if (ms < 1000) return `${Math.round(ms)}ms`;
  return `${(ms / 1000).toFixed(1)}s`;
};

// ═══════════════════════════════════════════════════════════════════════════
//  Mock 运行逻辑（演示骨架用，真实场景替换为 API 调用）
// ═══════════════════════════════════════════════════════════════════════════

const MOCK_EXECUTION: ExecutionLog[] = [
  {
    id: 'log_1', nodeId: 'req_1', nodeName: 'Request',
    nodeType: 'request', status: 'success',
    startTime: new Date().toLocaleTimeString(), duration: 2,
    inputs: { method: 'GET', url: '/api/v1/users' },
    outputs: { headers: { 'content-type': 'application/json' }, params: {}, body: {} },
  },
  {
    id: 'log_2', nodeId: 'eval_1', nodeName: 'Evaluate',
    nodeType: 'evaluate', status: 'success',
    startTime: new Date().toLocaleTimeString(), duration: 1,
    inputs: { expression: '$.request.params.id' },
    outputs: { result: 42 },
  },
  {
    id: 'log_3', nodeId: 'http_1', nodeName: 'HTTP Request',
    nodeType: 'httpRequest', status: 'error',
    startTime: new Date().toLocaleTimeString(), duration: 1243,
    inputs: { url: 'https://api.example.com/data', method: 'GET' },
    outputs: undefined,
    error: 'Connection timed out after 1243ms: ETIMEDOUT https://api.example.com/data',
  },
  {
    id: 'log_4', nodeId: 'db_1', nodeName: 'Get User By ID',
    nodeType: 'database', status: 'success',
    startTime: new Date().toLocaleTimeString(), duration: 3400,
    inputs: { sql: 'SELECT * FROM users WHERE id = ?', params: [42] },
    outputs: { rows: [{ id: 42, name: 'Alice', email: 'alice@example.com' }] },
  },
  {
    id: 'log_5', nodeId: 'tmpl_1', nodeName: 'Template',
    nodeType: 'template', status: 'success',
    startTime: new Date().toLocaleTimeString(), duration: 0,
    inputs: { template: 'Hello, {{name}}!' },
    outputs: { result: 'Hello, Alice!' },
  },
  {
    id: 'log_6', nodeId: 'resp_1', nodeName: 'Response',
    nodeType: 'response', status: 'success',
    startTime: new Date().toLocaleTimeString(), duration: 0,
    inputs: { body: { message: 'Hello, Alice!' } },
    outputs: { status: 200, body: { code: 0, data: { message: 'Hello, Alice!' } } },
  },
];

// ═══════════════════════════════════════════════════════════════════════════
//  子组件：KV 编辑器（Headers / Query Params 共用）
// ═══════════════════════════════════════════════════════════════════════════

interface KVEditorProps {
  entries: KVEntry[];
  onChange: (entries: KVEntry[]) => void;
  keyPlaceholder?: string;
  valuePlaceholder?: string;
}

const KVEditor: React.FC<KVEditorProps> = ({
  entries, onChange, keyPlaceholder = 'Key', valuePlaceholder = 'Value',
}) => {
  const update = (id: string, field: keyof KVEntry, val: any) => {
    onChange(entries.map((e) => (e.id === id ? { ...e, [field]: val } : e)));
  };
  const remove = (id: string) => onChange(entries.filter((e) => e.id !== id));
  const add = () => onChange([...entries, createEmptyKV()]);

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
            type="text" size="small" danger
            icon={<DeleteOutlined />}
            onClick={() => remove(entry.id)}
            className="pfd-kv-delete"
          />
        </div>
      ))}
      <Button
        type="dashed" size="small" block
        icon={<PlusOutlined />}
        onClick={add}
        className="pfd-kv-add"
      >
        添加
      </Button>
    </div>
  );
};

// ═══════════════════════════════════════════════════════════════════════════
//  子组件：节点执行状态图标
// ═══════════════════════════════════════════════════════════════════════════

const StatusIcon: React.FC<{ status: ExecutionLog['status'] }> = ({ status }) => {
  switch (status) {
    case 'success':
      return <CheckCircleFilled style={{ color: '#52c41a', fontSize: 16 }} />;
    case 'error':
      return <CloseCircleFilled style={{ color: '#ff4d4f', fontSize: 16 }} />;
    case 'running':
      return <LoadingOutlined style={{ color: '#1677ff', fontSize: 16 }} spin />;
    case 'skipped':
      return <ClockCircleOutlined style={{ color: '#bfbfbf', fontSize: 16 }} />;
    default:
      return null;
  }
};

// ═══════════════════════════════════════════════════════════════════════════
//  子组件：JSON 查看器（只读高亮展示）
// ═══════════════════════════════════════════════════════════════════════════

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
        readOnly={true}
        height="auto"
        maxHeight="300px"
      />
    </div>
  );
};

// ═══════════════════════════════════════════════════════════════════════════
//  主组件：ProFlowDebugger
// ═══════════════════════════════════════════════════════════════════════════

const FlowDebugger: React.FC<FlowDebuggerProps> = ({
  dslContent = '',
  apiUrl = '/api/v1/example',
  apiMethod = 'GET',
  onZoomIn,
  onZoomOut,
  onFitView,
  onUndo,
  onRedo,
  canUndo = false,
  canRedo = false,
  onRun,
  onConsoleOpenChange,
  onExecutionLogsChange,
  onSelectedLogChange,
  playbackTrace,
  onDebugStart,
  onDebugStatus,
  onDebugResume,
  onDebugCancel,
  breakpoints,
}) => {
  // ─── DOM 引用 ──────────────────────────────────────────────────────
  const rootRef = useRef<HTMLDivElement>(null);

  // ─── 核心交互状态 ──────────────────────────────────────────────────
  const [isTriggerPanelOpen, setIsTriggerPanelOpen] = useState(false);
  const [isConsoleOpen, setIsConsoleOpen] = useState(!!playbackTrace);
  const [runningStatus, setRunningStatus] = useState<RunStatus>(playbackTrace?.status || 'idle');
  const [executionLogs, setExecutionLogs] = useState<ExecutionLog[]>(playbackTrace?.stepLogs || []);
  const [selectedLogId, setSelectedLogId] = useState<string | null>(null);

  // 同步回放 trace 的日志/状态。
  // 仅在「新的一条回放」时自动展开控制台；不要依赖 onConsoleOpenChange 引用，
  // 否则父组件重渲染会导致用户刚收起的控制台立刻被重新打开。
  const playbackInitKeyRef = useRef<string | null>(null);
  const onConsoleOpenChangeRef = useRef(onConsoleOpenChange);
  onConsoleOpenChangeRef.current = onConsoleOpenChange;
  useEffect(() => {
    if (!playbackTrace) {
      playbackInitKeyRef.current = null;
      return;
    }
    setExecutionLogs(playbackTrace.stepLogs || []);
    setRunningStatus(playbackTrace.status);
    if (playbackTrace.startTime) {
      setRunTimestamp(new Date(playbackTrace.startTime).toLocaleTimeString());
    }
    const initKey = `${playbackTrace.startTime ?? ''}|${playbackTrace.stepLogs?.length ?? 0}|${playbackTrace.status ?? ''}`;
    if (playbackInitKeyRef.current !== initKey) {
      playbackInitKeyRef.current = initKey;
      setIsConsoleOpen(true);
      onConsoleOpenChangeRef.current?.(true);
    }
  }, [playbackTrace]);

  // 提取为统一的方法以触发外部回调
  const updateLogs = useCallback((logs: ExecutionLog[]) => {
    setExecutionLogs(logs);
    onExecutionLogsChange?.(logs);
  }, [onExecutionLogsChange]);

  const updateSelectedLog = useCallback((id: string | null) => {
    setSelectedLogId(id);
    const nodeIds = id && executionLogs.find(l => l.id === id)?.nodeId;
    onSelectedLogChange?.(nodeIds || null);
  }, [executionLogs, onSelectedLogChange]);

  // ─── Trigger Panel 的输入状态 ──────────────────────────────────────
  const [triggerHeaders, setTriggerHeaders] = useState<KVEntry[]>([createEmptyKV()]);
  const [triggerParams, setTriggerParams] = useState<KVEntry[]>([createEmptyKV()]);
  const [triggerBody, setTriggerBody] = useState<string>('{\n  \n}');
  const [triggerActiveTab, setTriggerActiveTab] = useState<string>(
    (apiMethod || 'GET').toUpperCase() === 'GET' ? 'params' : 'body',
  );
  const isGetMethod = (apiMethod || 'GET').toUpperCase() === 'GET';
  useEffect(() => {
    if (isGetMethod && triggerActiveTab === 'body') {
      setTriggerActiveTab('params');
    }
  }, [isGetMethod, triggerActiveTab]);

  // ─── Console 的 Inspector Tab ──────────────────────────────────────
  const [inspectorTab, setInspectorTab] = useState<string>('input');

  // ─── 运行时间戳 ────────────────────────────────────────────────────
  const [runTimestamp, setRunTimestamp] = useState<string>('');

  // ─── 调整高度相关状态 ──────────────────────────────────────────────
  const [consoleHeight, setConsoleHeight] = useState<number>(300);
  const [isResizing, setIsResizing] = useState<boolean>(false);

  const [debugSessionId, setDebugSessionId] = useState<string | null>(null);
  const [debugStatus, setDebugStatus] = useState<RunStatus | 'suspended'>('idle');

  // ─── 派生：选中的日志条目 ──────────────────────────────────────────
  const selectedLog = useMemo(
    () => executionLogs.find((log) => log.id === selectedLogId) ?? null,
    [executionLogs, selectedLogId],
  );

  // ─── 派生：统计信息 ────────────────────────────────────────────────
  const stats = useMemo(() => {
    const total = executionLogs.length;
    const success = executionLogs.filter((l) => l.status === 'success').length;
    const errors = executionLogs.filter((l) => l.status === 'error').length;
    const running = executionLogs.filter((l) => l.status === 'running').length;
    return { total, success, errors, running };
  }, [executionLogs]);

  // ═══════════════════════════════════════════════════════════════════
  //  事件处理
  // ═══════════════════════════════════════════════════════════════════

  /** KV 数组 → 扁平 Record */
  const kvToRecord = useCallback((entries: KVEntry[]): Record<string, string> => {
    const result: Record<string, string> = {};
    entries.forEach(({ key, value, enabled }) => {
      if (enabled && key.trim()) result[key.trim()] = value;
    });
    return result;
  }, []);

  const handleDebug = useCallback(async () => {
    if (!onDebugStart) return;
    setRunningStatus('running');
    setDebugStatus('running');
    setIsConsoleOpen(true);
    onConsoleOpenChange?.(true);
    updateLogs([]);
    updateSelectedLog(null);
    setRunTimestamp(new Date().toLocaleTimeString());

    const payload = {
      dslContent,
      headers: kvToRecord(triggerHeaders),
      queryParams: kvToRecord(triggerParams),
      body: isGetMethod ? '' : triggerBody,
      breakpoints: breakpoints || [],
    };

    try {
      const { sessionId } = await onDebugStart(payload);
      setDebugSessionId(sessionId);
    } catch (err: any) {
      setRunningStatus('error');
      setDebugStatus('error');
    }
  }, [dslContent, triggerHeaders, triggerParams, triggerBody, isGetMethod, onDebugStart, kvToRecord, breakpoints, onConsoleOpenChange, updateLogs, updateSelectedLog]);

  const pollStatus = useCallback(async () => {
    if (!debugSessionId || !onDebugStatus) return;
    try {
      const res = await onDebugStatus(debugSessionId);
      if (res && res.status) {
        const currentStatus = res.status.toLowerCase();
        setDebugStatus(currentStatus);
        
        if (res.trace && res.trace.stepLogs) {
          updateLogs(res.trace.stepLogs);
        } else if (currentStatus === 'suspended' && res.suspendedNodeId) {
          // 在挂起状态下，后端不返回完整 trace，只返回当前快照。
          // 我们伪造一条日志记录，以便在左侧列表中显示当前挂起的节点，并在右侧查看其上下文变量。
          updateLogs([{
            id: res.suspendedNodeId,
            nodeId: res.suspendedNodeId,
            nodeName: res.suspendedNodeName || res.suspendedNodeId,
            nodeType: 'unknown',
            status: 'running',
            startTime: new Date().toLocaleTimeString(),
            duration: 0,
            inputs: res.variables || {}, // 将上下文变量展示在 "输入" 面板中
            outputs: {},
            error: null,
          }]);
        }

        if (currentStatus === 'completed' || currentStatus === 'finished' || currentStatus === 'error') {
          setRunningStatus(currentStatus === 'error' ? 'error' : 'success');
          setDebugSessionId(null);
        } else if (currentStatus === 'suspended' && res.suspendedNodeId) {
          updateSelectedLog(res.suspendedNodeId);
        }
      }
    } catch (e) {
      // ignore
    }
  }, [debugSessionId, onDebugStatus, updateLogs, updateSelectedLog]);

  useEffect(() => {
    let timer: any;
    if (debugSessionId && (debugStatus === 'running' || debugStatus === 'suspended')) {
      timer = setInterval(pollStatus, 1000);
    }
    return () => clearInterval(timer);
  }, [debugSessionId, debugStatus, pollStatus]);

  const handleResume = useCallback(async () => {
    if (!debugSessionId || !onDebugResume) return;
    setDebugStatus('running');
    await onDebugResume(debugSessionId);
  }, [debugSessionId, onDebugResume]);

  const handleCancel = useCallback(async () => {
    if (!debugSessionId || !onDebugCancel) return;
    await onDebugCancel(debugSessionId);
    setDebugSessionId(null);
    setDebugStatus('error');
    setRunningStatus('error');
  }, [debugSessionId, onDebugCancel]);

  /** 点击 Run 按钮 */
  const handleRun = useCallback(async () => {
    setRunningStatus('running');
    setIsConsoleOpen(true);
    onConsoleOpenChange?.(true);
    updateLogs([]);
    updateSelectedLog(null);
    setRunTimestamp(new Date().toLocaleTimeString());

    const payload = {
      dslContent,
      headers: kvToRecord(triggerHeaders),
      queryParams: kvToRecord(triggerParams),
      body: isGetMethod ? '' : triggerBody,
    };

    try {
      let logs: ExecutionLog[] = [];
      let trace: FlowTrace | null = null;
      
      if (onRun) {
        trace = await onRun(payload);
        logs = trace.stepLogs || [];
      } else {
        // ── Mock 运行（开发/演示用） ──
        await new Promise((resolve) => setTimeout(resolve, 1500));
        logs = MOCK_EXECUTION.map((log) => ({
          ...log,
          startTime: new Date().toLocaleTimeString(),
        }));
        trace = {
          traceId: 'mock_1',
          startTime: Date.now(),
          endTime: Date.now(),
          totalDurationMs: logs.reduce((acc, l) => acc + l.duration, 0),
          status: logs.some(l => l.status === 'error') ? 'error' : 'success',
          stepLogs: logs,
        };
      }

      // 将触发器面板的入参注入到第一个节点（通常为 request）的输入中展示
      // 直接覆盖 headers / params / body，和后端 request 节点的字段结构保持一致
      if (logs.length > 0 && logs[0].nodeType === 'request') {
        let parsedBody: any = {};
        try {
          if (payload.body.trim()) {
            parsedBody = JSON.parse(payload.body);
          }
        } catch (e) {
          // body 非合法 JSON 时原样展示字符串
          parsedBody = payload.body;
        }

        logs[0] = {
          ...logs[0],
          inputs: {
            // 用触发器的真实入参覆盖后端返回的空占位，确保展示一致
            method: logs[0].inputs?.method,
            url: logs[0].inputs?.url,
            headers: Object.keys(payload.headers).length > 0 ? payload.headers : logs[0].inputs?.headers,
            params: Object.keys(payload.queryParams).length > 0 ? payload.queryParams : logs[0].inputs?.params,
            body: parsedBody && Object.keys(parsedBody).length > 0 ? parsedBody : logs[0].inputs?.body,
          },
        };
      }

      updateLogs(logs);
      setRunningStatus(trace ? trace.status : (logs.some((l) => l.status === 'error') ? 'error' : 'finished'));
      if (logs.length > 0) updateSelectedLog(logs[0].id);

    } catch (err: any) {
      setRunningStatus('error');
      const raw = err?.message || 'Unknown execution error';
      // axios 默认 10s：前端先断开时会被误标成 Global Error，并非节点引擎真实归属
      const isClientWaitTimeout = /timeout of \d+ms exceeded/i.test(raw)
        || err?.code === 'ECONNABORTED';
      const errorMsg = isClientWaitTimeout
        ? `调试请求等待超时（前端断开）：${raw}。节点内 HttpRequest 超时会记在对应节点；请确认调试接口 timeout 已拉长，或检查目标接口是否过慢。`
        : raw;
      updateLogs([{
        id: 'err_global',
        nodeId: '__global__',
        nodeName: 'Global Error',
        nodeType: 'error',
        status: 'error',
        startTime: new Date().toLocaleTimeString(),
        duration: 0,
        error: errorMsg,
      }]);
      updateSelectedLog('err_global');
    }
  }, [dslContent, triggerHeaders, triggerParams, triggerBody, isGetMethod, onRun, kvToRecord]);

  /** 切换 Trigger Panel */
  const toggleTriggerPanel = useCallback(() => {
    setIsTriggerPanelOpen((prev) => !prev);
  }, []);

  /** 切换控制台 */
  const toggleConsole = useCallback(() => {
    setIsConsoleOpen((prev) => {
      const next = !prev;
      onConsoleOpenChange?.(next);
      return next;
    });
  }, [onConsoleOpenChange]);

  /** 控制台拖拽调整高度 */
  const handleConsoleResize = useCallback((e: React.MouseEvent) => {
    e.preventDefault();
    setIsResizing(true);
    const startY = e.clientY;
    const startHeight = consoleHeight;

    const handleMouseMove = (moveEvent: MouseEvent) => {
      // 向上拖动，clientY 减小，高度增加
      const deltaY = startY - moveEvent.clientY;
      let newHeight = startHeight + deltaY;
      
      let maxHeight = window.innerHeight * 0.8;
      if (rootRef.current) {
        // 限制在当前画布区域内，预留 20px 缓冲以确保能拖得回来
        maxHeight = rootRef.current.clientHeight - 20;
      }
      
      if (newHeight > maxHeight) {
        newHeight = maxHeight;
      }
      setConsoleHeight(newHeight);
    };

    const handleMouseUp = (upEvent: MouseEvent) => {
      setIsResizing(false);
      const deltaY = startY - upEvent.clientY;
      let finalHeight = startHeight + deltaY;
      
      let maxHeight = window.innerHeight * 0.8;
      if (rootRef.current) {
        maxHeight = rootRef.current.clientHeight - 20;
      }
      
      if (finalHeight > maxHeight) {
        finalHeight = maxHeight;
      }
      
      if (finalHeight < 150) {
        // 向下拖动到一定高度自动隐藏
        setIsConsoleOpen(false);
        onConsoleOpenChange?.(false);
        setConsoleHeight(300); // 恢复默认高度
      } else {
        setConsoleHeight(finalHeight);
      }
      
      document.removeEventListener('mousemove', handleMouseMove);
      document.removeEventListener('mouseup', handleMouseUp);
    };

    document.addEventListener('mousemove', handleMouseMove);
    document.addEventListener('mouseup', handleMouseUp);
  }, [consoleHeight, onConsoleOpenChange]);

  // ═══════════════════════════════════════════════════════════════════
  //  渲染
  // ═══════════════════════════════════════════════════════════════════

  const shouldShowCollapsedConsole =
    !isConsoleOpen && (
      runningStatus !== 'idle'
      || executionLogs.length > 0
      || !!runTimestamp
      || !!playbackTrace
    );

  return (
    <div className="pfd-root" ref={rootRef}>

      {/* ══════════════════════════════════════════════════════════════
          1. 底部悬浮工具栏 (Floating Toolbar)
          ══════════════════════════════════════════════════════════════ */}
      <div
        className="pfd-floating-toolbar"
        style={{
          // 控制台打开时，工具栏上移避让
          bottom: isConsoleOpen ? consoleHeight + 24 : shouldShowCollapsedConsole ? 82 : 24,
        }}
      >
        {/* 左侧：画布操作按钮（快照模式保留缩放/适配，隐藏撤销重做） */}
        <div className="pfd-toolbar-left">
          {!playbackTrace && (
            <>
              <Tooltip title="撤销">
                <button
                  className="pfd-tool-btn"
                  disabled={!canUndo}
                  onClick={onUndo}
                >
                  <UndoOutlined />
                </button>
              </Tooltip>
              <Tooltip title="重做">
                <button
                  className="pfd-tool-btn"
                  disabled={!canRedo}
                  onClick={onRedo}
                >
                  <RedoOutlined />
                </button>
              </Tooltip>
              <div className="pfd-toolbar-divider" />
            </>
          )}

          <Tooltip title="放大">
            <button className="pfd-tool-btn" onClick={onZoomIn}>
              <ZoomInOutlined />
            </button>
          </Tooltip>
          <Tooltip title="缩小">
            <button className="pfd-tool-btn" onClick={onZoomOut}>
              <ZoomOutOutlined />
            </button>
          </Tooltip>
          <Tooltip title="适配视图">
            <button className="pfd-tool-btn" onClick={onFitView}>
              <CompressOutlined />
            </button>
          </Tooltip>
        </div>

        {/* 右侧：主操作按钮 (仅在非回放模式下显示) */}
        {!playbackTrace && (
          <div className="pfd-toolbar-right">
            <Tooltip title="触发器参数配置">
            <button
              className={`pfd-tool-btn ${isTriggerPanelOpen ? 'pfd-tool-btn--active' : ''}`}
              onClick={toggleTriggerPanel}
            >
              <SettingOutlined />
            </button>
          </Tooltip>

          {/* 调试操作栏 */}
          {(debugStatus === 'running' || debugStatus === 'suspended') ? (
            <div style={{ display: 'flex', gap: 8, marginLeft: 8, alignItems: 'center' }}>
              <Button 
                type="primary" 
                onClick={handleResume} 
                disabled={debugStatus === 'running'}
                icon={debugStatus === 'running' ? <LoadingOutlined /> : <StepForwardOutlined />}
              >
                {debugStatus === 'running' ? '执行中' : '单步跳过'}
              </Button>
              <Button danger onClick={handleCancel} icon={<StopOutlined />}>停止</Button>
            </div>
          ) : (
            <div style={{ display: 'flex', gap: 8, marginLeft: 8 }}>
              <button
                className={`pfd-run-btn`}
                style={{ background: '#722ed1', borderColor: '#722ed1', color: '#fff' }}
                onClick={handleDebug}
                disabled={runningStatus === 'running'}
              >
                <BugOutlined />
                <span style={{marginLeft: 4}}>调试</span>
              </button>
              {/* Run 按钮 */}
              <button
                className={`pfd-run-btn ${runningStatus === 'running' ? 'pfd-run-btn--running' : ''}`}
                onClick={handleRun}
                disabled={runningStatus === 'running'}
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
            </div>
          )}

          {/* 运行完成后的小指示器 */}
          {(runningStatus === 'finished' || runningStatus === 'error') && (
            <Tooltip title={`${stats.success} 成功 / ${stats.errors} 失败`}>
              <button
                className="pfd-tool-btn pfd-console-toggle"
                onClick={toggleConsole}
              >
                <ConsoleSqlOutlined />
                {stats.errors > 0 && (
                  <Badge
                    count={stats.errors}
                    size="small"
                    offset={[-2, -2]}
                    className="pfd-error-badge"
                  />
                )}
              </button>
            </Tooltip>
          )}
        </div>
        )}
      </div>

      {/* ══════════════════════════════════════════════════════════════
          2. 右侧触发器面板 (Trigger Panel - 无遮罩悬浮卡片)
          ══════════════════════════════════════════════════════════════ */}
      {isTriggerPanelOpen && (
        <div className={`pfd-trigger-panel ${isConsoleOpen ? 'pfd-trigger-panel--console-open' : ''}`}>
          <div className="pfd-trigger-header">
            <div className="pfd-trigger-title">
              <SettingOutlined style={{ marginRight: 8, color: '#1677ff' }} />
              <span>触发器配置</span>
            </div>
            <button
              className="pfd-close-btn"
              onClick={() => setIsTriggerPanelOpen(false)}
            >
              <CloseOutlined />
            </button>
          </div>

          {/* API 信息 */}
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
              {apiUrl}
            </Text>
          </div>

          {/* 输入区域 Tabs */}
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

          {/* 与底部悬浮「运行」同一逻辑 */}
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

      {/* ══════════════════════════════════════════════════════════════
          3. 底部运行控制台 (Run Logs Console)
          ══════════════════════════════════════════════════════════════ */}
      <div
        className={`pfd-console ${isConsoleOpen ? 'pfd-console--open' : ''} ${shouldShowCollapsedConsole ? 'pfd-console--collapsed' : ''} ${isResizing ? 'pfd-console--resizing' : ''}`}
        style={isConsoleOpen ? { height: consoleHeight } : undefined}
      >
        {/* 顶部拖拽调整高度 */}
        {isConsoleOpen && (
          <div
            className={`pfd-console-resizer ${isResizing ? 'active' : ''}`}
            onMouseDown={handleConsoleResize}
          />
        )}
        
        {/* 控制台顶部栏 */}
        <div className="pfd-console-header">
          <div className="pfd-console-header-left">
            <Text strong style={{ fontSize: 13 }}>运行日志</Text>
            {runTimestamp && (
              <Tag className="pfd-timestamp-tag">{runTimestamp}</Tag>
            )}

            {/* 过滤器 */}
            <Select
              size="small"
              defaultValue="all"
              style={{ width: 110 }}
              options={[
                { label: '全部节点', value: 'all' },
                { label: '✅ Success', value: 'success' },
                { label: '❌ Error', value: 'error' },
              ]}
              popupMatchSelectWidth={false}
              className="pfd-console-filter"
            />

            <Select
              size="small"
              defaultValue="all"
              style={{ width: 90 }}
              options={[
                { label: '状态', value: 'all' },
              ]}
              popupMatchSelectWidth={false}
              className="pfd-console-filter"
            />
          </div>

          <div className="pfd-console-header-right">
            {runningStatus === 'running' && (
              <Spin size="small" style={{ marginRight: 12 }} />
            )}
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
              <button className="pfd-close-btn" onClick={toggleConsole}>
                {isConsoleOpen ? <DownOutlined /> : <UpOutlined />}
              </button>
            </Tooltip>
            {!playbackTrace && (
              <Tooltip title="关闭控制台">
                <button
                  className="pfd-close-btn"
                  onClick={() => { setIsConsoleOpen(false); onConsoleOpenChange?.(false); }}
                  style={{ marginLeft: 4 }}
                >
                  <CloseOutlined />
                </button>
              </Tooltip>
            )}
          </div>
        </div>

        {/* 控制台主体：左右分栏 */}
        <div className="pfd-console-body">
          {/* 左侧：执行列表 (30%) */}
          <div className="pfd-exec-list">
            <div className="pfd-exec-list-header">
              <Text type="secondary" style={{ fontSize: 12 }}>全部节点</Text>
            </div>
            <div className="pfd-exec-list-scroll">
              {executionLogs.length === 0 && (
                <div className="pfd-exec-empty">
                  <Empty
                    image={Empty.PRESENTED_IMAGE_SIMPLE}
                    description={
                      runningStatus === 'running'
                        ? '正在执行中...'
                        : '点击 Run 开始调试'
                    }
                  />
                </div>
              )}
              {executionLogs.map((log) => (
                <div
                  key={log.id}
                  className={`pfd-exec-item ${selectedLogId === log.id ? 'pfd-exec-item--active' : ''}`}
                  onClick={() => updateSelectedLog(log.id)}
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
                    <Text type="secondary" className="pfd-exec-item-time">
                      {log.startTime}
                    </Text>
                    <Text type="secondary" className="pfd-exec-item-duration">
                      • {formatDuration(log.duration)}
                    </Text>
                  </div>
                </div>
              ))}
            </div>
          </div>

          {/* 右侧：节点详情 Inspector (70%) */}
          <div className="pfd-inspector">
            {!selectedLog ? (
              <div className="pfd-inspector-empty">
                <FileTextOutlined style={{ fontSize: 40, color: '#d9d9d9', marginBottom: 12 }} />
                <Text type="secondary">选择左侧节点查看详情</Text>
              </div>
            ) : (
              <>
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
                          <Text type="secondary" style={{ marginBottom: 8, display: 'block' }}>实际执行的 SQL 语句 (仅用于展示)：</Text>
                          <pre style={{ 
                            whiteSpace: 'pre-wrap', 
                            wordBreak: 'break-all', 
                            color: '#08979c', 
                            backgroundColor: '#e6fffb', 
                            border: '1px solid #87e8de', 
                            borderRadius: 6, 
                            padding: 12,
                            fontFamily: 'monospace'
                          }}>
                            {selectedLog.inputs?.actualSql || '未获取到实际执行的 SQL，请重新运行'}
                          </pre>
                        </div>
                      )
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
                            <Badge
                              dot
                              status="error"
                              style={{ marginLeft: 4 }}
                            />
                          )}
                        </span>
                      ),
                      children: (
                        <div className="pfd-details-content">
                          <div className="pfd-detail-row">
                            <Text type="secondary">节点 ID:</Text>
                            <Text code>{selectedLog.nodeId}</Text>
                          </div>
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
              </>
            )}
          </div>
        </div>
      </div>
    </div>
  );
};

export default React.memo(FlowDebugger);
