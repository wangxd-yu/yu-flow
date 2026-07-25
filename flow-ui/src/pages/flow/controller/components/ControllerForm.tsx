/**
 * ControllerForm.tsx
 * ─────────────────────────────────────────────────────────────────────────────
 * Yu Flow · API 动态编排平台 — 核心配置页面 (PageContainer 架构)
 *
 * 重构后的瘦编排器：所有 Tab 内容已拆分为独立的 Panel 组件，
 * 本文件仅负责：
 *   1. 顶级状态管理与生命周期（数据初始化 / 提交 Payload 组装）
 *   2. Header 区域（Method + URL + Name + 发布状态 + 保存/取消）
 *   3. Tab 路由（将状态通过 Props 下发到各 Panel）
 * ─────────────────────────────────────────────────────────────────────────────
 */
import React, { useState, useEffect, useCallback, useMemo } from 'react';
import {
  message, Button, Form, Input, Select, Modal, Alert,
  Space, Tag, Dropdown, Tooltip, Popover, Segmented,
} from 'antd';
import type { MenuProps } from 'antd';
import {
  SaveOutlined,
  CloseOutlined,
  CopyOutlined,
  CloudUploadOutlined,
  CloudDownloadOutlined,
  RollbackOutlined,
  FileTextOutlined,
  CodeOutlined,
  ImportOutlined,
  HistoryOutlined,
  DatabaseOutlined,
  ExperimentOutlined,
  DownOutlined,
  ToolOutlined,
} from '@ant-design/icons';
import { merge } from 'lodash';
import { PageContainer } from '@ant-design/pro-components';
import { history, request } from '@umijs/max';
import {
  addAutoApiConfig, updateAutoApiConfig, publishApi, unpublishApi, rollbackApi, republishApi,
  listApiVersions, restoreApiVersion, queryAutoApiConfigDetail, supportsApiDataView,
  probeHostApiNow, checkHostApiRouteExists, listHostApiRoutes,
  type HostApiRoute,
} from '@/services/flow/flowController';
import AssetVersionHistoryDrawer from '@/components/flow/AssetVersionHistoryDrawer';
import {
  AssetFormShell,
  ASSET_FORM_SHELL_CLASS,
  ASSET_FORM_FILL_CLASS,
} from '@/components/flow/ops';
import { buildApiCurl, copyText, openPublishedApiDocCenter } from '@/utils/apiDocsActions';
import CurlImportModal, { type CurlImportApplyPayload } from './CurlImportModal';

// ── Panel 子组件 ──
import ImplementationPanel, { getStaticJsonError, type EngineMode } from './panels/ImplementationPanel';
import { parseHostBinding, stringifyHostBinding, type HostWrapBinding } from './panels/HostWrapConfig';
import ReqSchemaPanel from './panels/ReqSchemaPanel';
import ResSchemaPanel from './panels/ResSchemaPanel';
import BasicInfoPanel from './panels/BasicInfoPanel';
import AssetRuntimePanel from '@/components/flow/AssetRuntimePanel';
import { confirmPublishWithGate } from '@/components/flow/release/confirmPublishWithGate';
import RegressionSuitePanel from '@/components/flow/release/RegressionSuitePanel';
import ApiDataViewDrawer from './ApiDataViewDrawer';
import type { SchemaNode, BodyType } from '@/components/flow/ApiContractDesigner/types';
import { buildApiTriggerPrefillFromContract } from '@/components/flow/debugger/apiTriggerPrefill';


// ═══════════════════════════════════════════════════════════════════════════
//  类型定义
// ═══════════════════════════════════════════════════════════════════════════

/** Tab Key 类型 */
type TabKey = 'implementation' | 'req-schema' | 'res-schema' | 'basic-info' | 'runtime';

/** 将 securityConfig JSON 还原为表单字段 */
function parseSecurityConfigToForm(raw?: string | object | null) {
  const defaults = {
    secAuthMode: 'INHERIT',
    secAntiReplayOverride: false,
    secAntiReplay: true,
    secRateLimitOverride: false,
    secRateLimitEnabled: false,
    secRateLimitQps: 100,
    secIpOverride: false,
    secIpAllowlist: '',
    secTimeoutOverride: false,
    secTimeoutMs: 30000,
  };
  if (!raw) return defaults;
  try {
    const cfg = typeof raw === 'string' ? JSON.parse(raw) : raw;
    const authMode = cfg?.authMode || 'INHERIT';
    return {
      secAuthMode: ['INHERIT', 'NONE', 'HOST', 'OPEN'].includes(authMode) ? authMode : 'INHERIT',
      secAntiReplayOverride: cfg?.antiReplay !== null && cfg?.antiReplay !== undefined,
      secAntiReplay: cfg?.antiReplay !== false,
      secRateLimitOverride:
        (cfg?.rateLimitEnabled !== null && cfg?.rateLimitEnabled !== undefined)
        || (cfg?.rateLimitQps !== null && cfg?.rateLimitQps !== undefined),
      secRateLimitEnabled: !!cfg?.rateLimitEnabled,
      secRateLimitQps: typeof cfg?.rateLimitQps === 'number' ? cfg.rateLimitQps : 100,
      secIpOverride: cfg?.ipAllowlist !== null && cfg?.ipAllowlist !== undefined,
      secIpAllowlist: typeof cfg?.ipAllowlist === 'string' ? cfg.ipAllowlist : '',
      secTimeoutOverride: cfg?.timeoutMs !== null && cfg?.timeoutMs !== undefined,
      secTimeoutMs: typeof cfg?.timeoutMs === 'number' ? cfg.timeoutMs : 30000,
    };
  } catch {
    return defaults;
  }
}

/** 由表单字段组装 securityConfig 对象 */
function buildSecurityConfigFromForm(formValues: Record<string, any>) {
  const authMode = formValues.secAuthMode || 'INHERIT';
  return {
    authMode,
    antiReplay: formValues.secAntiReplayOverride ? !!formValues.secAntiReplay : null,
    rateLimitEnabled: formValues.secRateLimitOverride ? !!formValues.secRateLimitEnabled : null,
    rateLimitQps: formValues.secRateLimitOverride
      ? (formValues.secRateLimitQps ?? 100)
      : null,
    ipAllowlist: formValues.secIpOverride
      ? (formValues.secIpAllowlist ?? '')
      : null,
    timeoutMs: formValues.secTimeoutOverride
      ? (typeof formValues.secTimeoutMs === 'number' ? formValues.secTimeoutMs : 30000)
      : null,
  };
}

export type ControllerFormV2Props = {
  onCancel: () => void;
  onSubmit: (success: boolean) => void;
  modalVisible: boolean;
  values?: Partial<any>;
  isEdit: boolean;
  /** 打开时默认 Tab（如运行中心深链） */
  initialTab?: TabKey;
};

/** HTTP Method → 主题色映射 */
const METHOD_COLORS: Record<string, string> = {
  GET: '#52c41a',
  POST: '#1677ff',
  PUT: '#faad14',
  DELETE: '#ff4d4f',
  PATCH: '#722ed1',
};

const METHOD_OPTIONS = ['GET', 'POST', 'PUT', 'DELETE', 'PATCH'];

// ═══════════════════════════════════════════════════════════════════════════
//  主组件（瘦编排器）
// ═══════════════════════════════════════════════════════════════════════════

// ── 高内聚组件：API 业务路径输入框 ──
const ApiPathInput = React.forwardRef<any, {
  value?: string;
  onChange?: (val: string) => void;
  disabled?: boolean;
  status?: "" | "error" | "warning";
  className?: string;
  [key: string]: any;
}>(({ value, onChange, disabled, status, className, ...rest }, ref) => {
  const [prefix, setPrefix] = useState<string>('');

  useEffect(() => {
    // 从后端接口获取系统级前缀
    request('/flow-api/sys-configs/key/SYSTEM_PREFIX', { method: 'GET' })
      .then((res: any) => {
        const data = typeof res === 'string' ? res : res?.data;
        setPrefix(data ? data : '/');
      })
      .catch(() => {
        setPrefix('/');
      });
  }, []);

  const handleCopy = () => {
    // 处理完整 URL，合并时去除可能会出现的双斜杠
    let fullUrl = `${prefix}${value || ''}`;
    fullUrl = fullUrl.replace(/(?<!:)\/\/+/g, '/');

    if (navigator.clipboard) {
      navigator.clipboard.writeText(fullUrl).then(() => {
        message.success('完整 URL 已复制');
      }).catch(() => {
        message.error('复制失败，请重试');
      });
    } else {
      const input = document.createElement('input');
      input.value = fullUrl;
      document.body.appendChild(input);
      input.select();
      document.execCommand('copy');
      document.body.removeChild(input);
      message.success('完整 URL 已复制');
    }
  };

  return (
    <>
      <style>{`
        .api-path-input-custom.ant-input-group-wrapper,
        .api-path-input-custom .ant-input-wrapper,
        .api-path-input-custom .ant-input-group {
          height: 32px;
          width: 100%;
          display: flex;
          align-items: stretch;
        }
        /* 前缀随文案自适应，禁止被 flex 挤扁裁切 */
        .api-path-input-custom .ant-input-group-addon {
          display: inline-flex !important;
          align-items: center !important;
          justify-content: center;
          flex: 0 0 auto !important;
          width: auto !important;
          max-width: none !important;
          height: 32px !important;
          box-sizing: border-box;
          padding: 0 11px !important;
          white-space: nowrap !important;
          overflow: visible !important;
          font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
        }
        .api-path-input-custom .ant-input-affix-wrapper {
          display: inline-flex !important;
          align-items: center !important;
          flex: 1 1 auto !important;
          min-width: 0 !important;
          height: 32px !important;
          box-sizing: border-box;
          padding-block: 0 !important;
        }
        .api-path-input-custom .ant-input-affix-wrapper > input.ant-input {
          height: 30px !important;
          line-height: 30px !important;
        }

        /* 全局正常状态（未发布）：前缀背景色置为白色，保持和主输入框一致 */
        .api-path-input-custom:not(.flow-api-path-disabled) .ant-input-group-addon {
          background-color: #ffffff !important;
        }

        /* 置灰状态（已发布）：覆盖外层 wrapper、input、前缀的背景色为灰色 */
        .flow-api-path-disabled, 
        .flow-api-path-disabled .ant-input-affix-wrapper,
        .flow-api-path-disabled input, 
        .flow-api-path-disabled .ant-input-group-addon {
          background-color: #f5f5f5 !important;
          color: rgba(0, 0, 0, 0.25) !important;
          cursor: pointer !important;
        }
        
        .flow-api-path-disabled:focus, 
        .flow-api-path-disabled:focus-within,
        .flow-api-path-disabled .ant-input-affix-wrapper:focus, 
        .flow-api-path-disabled .ant-input-affix-wrapper:focus-within {
           box-shadow: none !important;
           border-color: #d9d9d9 !important;
        }
      `}</style>
      <Input
        {...rest}
        ref={ref}
        className={`${className || ''} api-path-input-custom ${disabled ? 'flow-api-path-disabled' : ''}`.trim()}
        value={value}
        onChange={(e) => {
          if (!disabled) {
            onChange?.(e.target.value);
          }
        }}
        readOnly={disabled}
        onClick={(e) => {
          if (disabled) {
            message.info("当前接口已在线上运行，修改路径将导致现有调用方报错。若需修改，请先下线该接口。");
          }
          if (rest.onClick) {
            rest.onClick(e);
          }
        }}
        placeholder="请输入业务路径，例如: /user/info"
        addonBefore={prefix === '/' ? '/' : prefix}
        suffix={
          <Tooltip title="复制完整 URL">
            <CopyOutlined
              onClick={(e) => {
                e.stopPropagation();
                handleCopy();
              }}
              style={{ cursor: 'pointer', color: '#1677ff', transition: 'color 0.3s' }}
              onMouseEnter={(e) => (e.currentTarget.style.color = '#4096ff')}
              onMouseLeave={(e) => (e.currentTarget.style.color = '#1677ff')}
            />
          </Tooltip>
        }
        status={status}
        style={{ flex: 1, fontFamily: 'monospace', ...rest.style }}
      />
    </>
  );
});

const ControllerFormV2: React.FC<ControllerFormV2Props> = ({
  modalVisible, onCancel, onSubmit, values = {}, isEdit, initialTab,
}) => {
  // ─── Tab 状态 ──────────────────────────────────────────────────────
  const [activeTab, setActiveTab] = useState<TabKey>('implementation');

  // ─── 表单实例 ──────────────────────────────────────────────────────
  const [form] = Form.useForm();

  // ─── 预处理 Values ───────────────────────────────────────────────────
  const processedValues = useMemo(() => {
    if (!values) return {};
    const processed = { ...values };
    if (processed.tags && typeof processed.tags === 'string') {
      processed.tags = processed.tags.split(',').map((t: string) => t.trim()).filter(Boolean);
    }
    if (processed.flowService) {
      processed.serviceType = processed.flowService.type;
      processed.config = processed.flowService.script;
      processed.datasource = processed.flowService.datasource;
      processed.responseType = processed.flowService.dbType || processed.responseType || processed.type;
      delete processed.flowService;
    }
    if (processed.type && !processed.responseType) {
      processed.responseType = processed.type;
    }
    return processed;
  }, [values]);

  // ─── 地址栏状态 ────────────────────────────────────────────────────
  const [method, setMethod] = useState<string>('GET');
  const [url, setUrl] = useState<string>('');
  const [name, setName] = useState<string>('');
  const [submitAttempted, setSubmitAttempted] = useState<boolean>(false);
  const [urlConflictMsg, setUrlConflictMsg] = useState<string | null>(null);

  useEffect(() => {
    if (!url || !method) {
      setUrlConflictMsg(null);
      return;
    }
    // 编辑状态下未修改则跳过查重
    if (isEdit && url === processedValues?.url && method === processedValues?.method) {
      setUrlConflictMsg(null);
      return;
    }

    const timer = setTimeout(async () => {
      try {
        const res = await request<any>('/flow-api/api/check-exact', {
          method: 'GET',
          params: {
            method,
            url,
            excludeId: isEdit ? values?.id : undefined,
          },
        });
        if (res?.data === true || res === true) {
          setUrlConflictMsg('与已发布接口路径冲突');
        } else {
          setUrlConflictMsg(null);
        }
      } catch (e: any) {
        setUrlConflictMsg('接口路径查重失败');
      }
    }, 500);

    return () => clearTimeout(timer);
  }, [method, url, isEdit, processedValues, values?.id]);

  // 宿主是否存在同 method + path（决定「同名拦截」大提示是否展示）
  useEffect(() => {
    if (!modalVisible || !url?.trim() || !method) {
      setHostRouteExists(false);
      return;
    }
    let cancelled = false;
    const timer = setTimeout(async () => {
      try {
        const res: any = await checkHostApiRouteExists(method, url.trim());
        const exists = res?.exists === true || res?.data?.exists === true;
        if (!cancelled) setHostRouteExists(!!exists);
      } catch {
        if (!cancelled) setHostRouteExists(false);
      }
    }, 400);
    return () => {
      cancelled = true;
      clearTimeout(timer);
    };
  }, [modalVisible, method, url]);

  // WRAP：加载宿主路由供地址下拉
  useEffect(() => {
    if (!modalVisible || interceptMode !== 'WRAP') {
      return;
    }
    let cancelled = false;
    setHostRoutesLoading(true);
    listHostApiRoutes()
      .then((res: any) => {
        if (cancelled) return;
        const list = Array.isArray(res) ? res : (res?.data ?? []);
        setHostRoutes(list);
      })
      .catch(() => {
        if (!cancelled) setHostRoutes([]);
      })
      .finally(() => {
        if (!cancelled) setHostRoutesLoading(false);
      });
    return () => {
      cancelled = true;
    };
  }, [modalVisible, interceptMode]);

  // ─── 从 URL 自动提取 Path 参数 ─────────────────────────────
  useEffect(() => {
    if (!url) {
      setPathParams([]);
      return;
    }
    // 匹配 {paramName} 或 :paramName 两种风格
    const matches = Array.from(url.matchAll(/\{([^}]+)\}|:([A-Za-z_][A-Za-z0-9_]*)/g));
    const paramNames = matches.map((m) => m[1] || m[2]).filter(Boolean);

    if (paramNames.length === 0) {
      setPathParams([]);
      return;
    }

    setPathParams((prev) => {
      const existingMap = new Map(prev.map((n) => [n.name, n]));
      return paramNames.map((name) => {
        // 保留已有节点（用户可能修改了 type / description 等）
        if (existingMap.has(name)) return existingMap.get(name)!;
        return {
          id: `path_${name}_${Date.now()}`,
          name,
          type: 'string' as const,
          required: true,
          description: '',
        };
      });
    });
  }, [url]);

  // ─── 发布状态 ──────────────────────────────────────────────────────
  const [publishStatus, setPublishStatus] = useState<0 | 1>(0);
  const [historyOpen, setHistoryOpen] = useState(false);

  // ─── 保持 Form Store 与 React State 基础字段同步，防止右侧全局配置表单校验失败 ───
  useEffect(() => {
    form.setFieldsValue({
      name,
      url,
      method,
      publishStatus,
    });
  }, [name, url, method, publishStatus, form]);

  // ─── 服务实现: 引擎模式 / 同名拦截 ─────────────────────────────────
  const [engineMode, setEngineMode] = useState<EngineMode>('FLOW');
  const [interceptMode, setInterceptMode] = useState<'REPLACE' | 'WRAP'>('REPLACE');
  const [hostBinding, setHostBinding] = useState<HostWrapBinding>(() => parseHostBinding());
  const [probing, setProbing] = useState(false);
  /** 宿主 MVC 是否存在同 method+path（决定是否展示「同名拦截」提示） */
  const [hostRouteExists, setHostRouteExists] = useState(false);
  /** WRAP：宿主路由列表，供路径下拉选择 */
  const [hostRoutes, setHostRoutes] = useState<HostApiRoute[]>([]);
  const [hostRoutesLoading, setHostRoutesLoading] = useState(false);

  // ─── 4 个隔离的内容 State（状态绝对隔离） ────────────────────────
  const [dslContent, setDslContent] = useState<string>('');
  const [sqlContent, setSqlContent] = useState<string>('');
  const [jsonContent, setJsonContent] = useState<string>('');
  const [textContent, setTextContent] = useState<string>('');

  // ─── 请求参数契约 ─────────────────────────────────────────────────
  const [queryParams, setQueryParams] = useState<SchemaNode[]>([]);
  const [pathParams, setPathParams] = useState<SchemaNode[]>([]);
  const [headers, setHeaders] = useState<SchemaNode[]>([]);
  const [bodyNodes, setBodyNodes] = useState<SchemaNode[]>([]);
  const [bodyType, setBodyType] = useState<BodyType>('none');
  const [rawBody, setRawBody] = useState<string>('');

  // ─── 响应契约 ─────────────────────────────────────────────────────
  const [responseBody, setResponseBody] = useState<SchemaNode[]>([]);
  const [responseDesc, setResponseDesc] = useState<string>('成功');
  const [statusCode, setStatusCode] = useState<number>(200);



  // ─── DB 模式相关 ──────────────────────────────────────────────────
  const [dbDatasource, setDbDatasource] = useState<string | undefined>(undefined);
  const [responseType, setResponseType] = useState<string | undefined>(undefined);

  /** 静态 JSON 模式：非法时禁止保存 / 发布 */
  const staticJsonError = useMemo(
    () => (engineMode === 'JSON' ? getStaticJsonError(jsonContent) : null),
    [engineMode, jsonContent],
  );

  /** 契约 → 调试触发器预填 + 完整契约 JSON（可选校验） */
  const triggerPrefill = useMemo(
    () => buildApiTriggerPrefillFromContract({
      query: queryParams,
      pathParams,
      headers,
      body: bodyNodes,
      bodyType,
      rawBody,
    }),
    [queryParams, pathParams, headers, bodyNodes, bodyType, rawBody],
  );

  const draftContractJson = useMemo(
    () => JSON.stringify({
      request: { query: queryParams, pathParams, headers, body: bodyNodes, bodyType, rawBody },
      responses: {
        '200': { body: responseBody, description: responseDesc, statusCode },
      },
    }),
    [queryParams, pathParams, headers, bodyNodes, bodyType, rawBody, responseBody, responseDesc, statusCode],
  );

  // ═══════════════════════════════════════════════════════════════════
  //  初始化：Drawer 打开时还原数据
  // ═══════════════════════════════════════════════════════════════════


  useEffect(() => {
    if (modalVisible) {
      form.resetFields();

      // 还原缓存配置
      let cacheEnabled = false;
      let cacheTtlSeconds = 300;
      let cacheIncludePageable = true;
      let cacheKeyParams: Array<{ source: string; name: string }> = [];
      if (processedValues.cacheConfig) {
        try {
          const cfg = typeof processedValues.cacheConfig === 'string'
            ? JSON.parse(processedValues.cacheConfig)
            : processedValues.cacheConfig;
          cacheEnabled = !!cfg?.enabled;
          cacheTtlSeconds = cfg?.ttlSeconds ?? 300;
          cacheIncludePageable = cfg?.includePageable !== false;
          cacheKeyParams = Array.isArray(cfg?.keyParams) ? cfg.keyParams : [];
        } catch { /* ignore */ }
      }

      const secFields = parseSecurityConfigToForm(processedValues.securityConfig);

      form.setFieldsValue({
        ...processedValues,
        isCustomSuccess: !!processedValues.customSuccessWrapper,
        isCustomPage: !!processedValues.customPageWrapper,
        isCustomFail: !!processedValues.customFailWrapper,
        cacheEnabled,
        cacheTtlSeconds,
        cacheIncludePageable,
        cacheKeyParams,
        ...secFields,
      });
      setMethod(processedValues.method || 'GET');
      setUrl(processedValues.url || '');
      setName(processedValues.name || '');
      setSubmitAttempted(false);
      setDslContent(processedValues.dslContent || processedValues.config || '');
      setSqlContent(processedValues.sqlContent || '');
      setJsonContent(processedValues.jsonContent || '');
      setTextContent(processedValues.textContent || '');
      {
        const st = (processedValues.serviceType || 'FLOW') as EngineMode;
        const im = (processedValues.interceptMode === 'WRAP' || st === 'HOST')
          ? 'WRAP'
          : 'REPLACE';
        setInterceptMode(im);
        setEngineMode(im === 'WRAP' ? 'HOST' : (st === 'HOST' ? 'FLOW' : st));
        setHostBinding(parseHostBinding(processedValues.hostBinding));
      }
      setDbDatasource(processedValues.datasource);
      setResponseType(processedValues.responseType);
      setPublishStatus(processedValues.publishStatus ?? 0);

      setActiveTab(
        initialTab && (initialTab !== 'runtime' || !!processedValues.id)
          ? initialTab
          : 'implementation',
      );

      // 还原契约数据：从 contract 字段解析（持久化）
      let contract = null;
      if (processedValues.contract) {
        try {
          contract = JSON.parse(processedValues.contract);
        } catch { /* contract 解析失败则忽略 */ }
      }
      if (contract) {
        setQueryParams(contract.request?.query ?? []);
        setPathParams(contract.request?.pathParams ?? []);
        setHeaders(contract.request?.headers ?? []);
        setBodyNodes(contract.request?.body ?? []);
        setBodyType(contract.request?.bodyType ?? 'none');
        setRawBody(contract.request?.rawBody ?? '');
        setResponseBody(contract.responses?.['200']?.body ?? []);
        setResponseDesc(contract.responses?.['200']?.description ?? '成功');
        setStatusCode(contract.responses?.['200']?.statusCode ?? 200);
      } else {
        setQueryParams([]);
        setPathParams([]);
        setHeaders([]);
        setBodyNodes([]);
        setBodyType('none');
        setRawBody('');
        setResponseBody([]);
        setResponseDesc('成功');
        setStatusCode(200);
      }
    }
  }, [processedValues, form, modalVisible, initialTab]);

  // ═══════════════════════════════════════════════════════════════════
  //  提交逻辑
  // ═══════════════════════════════════════════════════════════════════

  const handleSubmit = useCallback(async (
    externalScript?: any,
    options: { notify?: boolean; closeOnSuccess?: boolean } = {},
  ) => {
    const { notify = true, closeOnSuccess = true } = options;
    setSubmitAttempted(true);
    if (!url?.trim() || !name?.trim()) {
      message.warning('请完善 API 路径和接口名称等必填项');
      return { success: false };
    }
    if (urlConflictMsg) {
      message.warning('接口路径存在冲突，请修改后再保存');
      return { success: false };
    }
    if (engineMode === 'JSON') {
      const jsonErr = getStaticJsonError(jsonContent);
      if (jsonErr) {
        message.error(jsonErr);
        return { success: false };
      }
    }
    let hide = null;
    try {
      const formValues = await form.validateFields();
      const finalDsl = typeof externalScript === 'string' ? externalScript : dslContent;

      const contractSnapshot = {
        request: { query: queryParams, pathParams, headers, body: bodyNodes, bodyType, rawBody },
        responses: {
          [String(statusCode)]: { statusCode, description: responseDesc, body: responseBody },
        },
      };

      // Flow 校验
      if (engineMode === 'FLOW' && finalDsl) {
        try {
          const flowDef = JSON.parse(finalDsl);
          if (flowDef.nodes) {
            for (const node of flowDef.nodes) {
              if (node.type === 'database') {
                const sqlType = node.data?.sqlType;
                const sqlText = node.data?.sql || '';
                const cleanSql = sqlText.replace(/\/\*[\s\S]*?\*\//g, '').replace(/--.*/g, '').trim().toUpperCase();
                let actualType: string | null = null;
                if (cleanSql.startsWith('INSERT')) actualType = 'INSERT';
                else if (cleanSql.startsWith('UPDATE')) actualType = 'UPDATE';
                else if (cleanSql.startsWith('DELETE')) actualType = 'DELETE';
                else if (cleanSql.startsWith('SELECT')) actualType = 'SELECT';
                if (actualType && sqlType !== actualType) {
                  message.error(`校验失败: 数据库节点 [${node.label || node.id}] SQL 类型不匹配`);
                  return { success: false };
                }
              }
            }
          }
        } catch { /* parse error → not blocking */ }
      }

      const effectiveIntercept = interceptMode === 'WRAP' || engineMode === 'HOST' ? 'WRAP' : 'REPLACE';
      const effectiveServiceType = effectiveIntercept === 'WRAP' ? 'HOST' : engineMode;
      const payload = {
        ...formValues,
        name,
        url,
        method,
        publishStatus,
        responseType,
        serviceType: effectiveServiceType,
        interceptMode: effectiveIntercept,
        hostBinding: effectiveIntercept === 'WRAP'
          ? stringifyHostBinding(hostBinding)
          : undefined,
        datasource: dbDatasource,
        dslContent: finalDsl,
        sqlContent,
        jsonContent,
        textContent,
        templateId: formValues.templateId,
        customSuccessWrapper: formValues.isCustomSuccess ? formValues.customSuccessWrapper : undefined,
        customPageWrapper: formValues.isCustomPage ? formValues.customPageWrapper : undefined,
        customFailWrapper: formValues.isCustomFail ? formValues.customFailWrapper : undefined,
        tags: formValues.tags && Array.isArray(formValues.tags) ? formValues.tags.join(',') : formValues.tags,
        // 将契约数据序列化为 JSON 字符串存入 contract 字段，后端用于入参校验
        contract: JSON.stringify(contractSnapshot),
        // 查询响应缓存配置（WRAP 透传宿主，强制关闭）
        cacheConfig: JSON.stringify({
          enabled: effectiveIntercept !== 'WRAP' && !!formValues.cacheEnabled,
          ttlSeconds: formValues.cacheTtlSeconds ?? 300,
          includePageable: formValues.cacheIncludePageable !== false,
          keyParams: Array.isArray(formValues.cacheKeyParams)
            ? formValues.cacheKeyParams.filter((p: any) => p?.source && p?.name)
            : [],
        }),
        securityConfig: JSON.stringify(buildSecurityConfigFromForm(formValues)),
      };

      hide = message.loading(isEdit ? '正在更新...' : '正在添加...');

      let savedRecord: any;
      if (isEdit) {
        savedRecord = await updateAutoApiConfig(values.id!, merge({}, values, payload));
      } else {
        savedRecord = await addAutoApiConfig(payload);
      }

      hide();
      if (notify) {
        message.success(isEdit ? '更新成功' : '添加成功');
      }
      if (closeOnSuccess) {
        onSubmit(true);
      }
      return { success: true, id: savedRecord?.id || values?.id };
    } catch (error: any) {
      hide?.();
      if (error?.errorFields) {
        message.warning('表单校验失败，请检查必填项');
      } else {
        message.error(isEdit ? '更新失败' : '添加失败');
      }
      onSubmit(false);
      return { success: false };
    }
  }, [
    form, dslContent, sqlContent, jsonContent, textContent,
    name, url, method, publishStatus, isEdit, values,
    queryParams, pathParams, headers, bodyNodes, bodyType, rawBody,
    responseBody, responseDesc, statusCode, onSubmit,
    dbDatasource, engineMode, interceptMode, hostBinding, responseType,
    urlConflictMsg,
  ]);

  const [regressionOpen, setRegressionOpen] = useState(false);
  const [dataViewOpen, setDataViewOpen] = useState(false);
  const [curlImportOpen, setCurlImportOpen] = useState(false);

  const handleCurlImportApply = useCallback((payload: CurlImportApplyPayload) => {
    const apply = () => {
      setMethod(payload.method);
      setUrl(payload.path);
      setQueryParams(payload.query);
      setHeaders(payload.headers);
      setBodyType(payload.bodyType);
      setBodyNodes(payload.body);
      setRawBody(payload.rawBody ?? '');
      setActiveTab('req-schema');
      setCurlImportOpen(false);
      message.success('已从 cURL 填入请求定义（未改服务实现）');
    };
    const hasContract =
      queryParams.length > 0
      || headers.length > 0
      || bodyNodes.length > 0
      || bodyType !== 'none'
      || !!rawBody?.trim();
    if (hasContract || (url?.trim() && url.trim() !== payload.path)) {
      Modal.confirm({
        title: '用 cURL 覆盖当前请求定义？',
        content: '将覆盖 Method / Path、Query、Header 与 Body 契约；服务实现（FLOW/DB 等）保持不变。',
        okText: '覆盖填入',
        cancelText: '取消',
        onOk: apply,
      });
      return;
    }
    apply();
  }, [queryParams, headers, bodyNodes, bodyType, rawBody, url]);

  const handlePublishCurrentDraft = useCallback(async () => {
    const effectiveIntercept = interceptMode === 'WRAP' || engineMode === 'HOST' ? 'WRAP' : 'REPLACE';
    // 仅当宿主确有同 method+path 时强警告（避免无冲突路径也弹危险确认）
    if (effectiveIntercept === 'REPLACE' && hostRouteExists) {
      const ok = await new Promise<boolean>((resolve) => {
        Modal.confirm({
          title: '确认发布「同名替换」？',
          content: (
            <div>
              <p>检测到宿主已注册 <code>{method} {url}</code>。</p>
              <p>发布后，Yu Flow 将<strong>接管</strong>该路径，宿主同名 Controller <strong>不再被调用</strong>。</p>
              <p style={{ color: 'rgba(0,0,0,0.45)', marginBottom: 0 }}>若只需监控/限流原接口，请改用「同名包裹」模式。</p>
            </div>
          ),
          okText: '确认替换并发布',
          okButtonProps: { danger: true },
          cancelText: '取消',
          onOk: () => resolve(true),
          onCancel: () => resolve(false),
        });
      });
      if (!ok) return;
    }

    const saved = await handleSubmit(undefined, { notify: false, closeOnSuccess: false });
    if (!saved.success || !saved.id) {
      return;
    }

    const envCode = await confirmPublishWithGate({
      assetType: 'API',
      assetId: saved.id,
      assetName: name || saved.id,
    });
    if (!envCode) {
      return;
    }

    const isRepublish = isEdit && publishStatus === 1;
    const hide = message.loading(isRepublish ? '正在发布更新...' : '正在发布...');
    try {
      if (isRepublish) {
        await republishApi(saved.id, envCode);
      } else {
        await publishApi(saved.id, envCode);
      }
      hide();
      message.success(isRepublish ? '发布更新成功' : '发布成功');
      onSubmit(true);
    } catch (e) {
      hide();
    }
  }, [handleSubmit, isEdit, publishStatus, onSubmit, name, interceptMode, engineMode, method, url, hostRouteExists]);

  const handleInterceptModeChange = useCallback((next: 'REPLACE' | 'WRAP') => {
    if (next === interceptMode) return;
    if (next === 'REPLACE') {
      Modal.confirm({
        title: '切换为「同名替换」？',
        content: '替换模式下需配置 FLOW/DB 等实现；发布后宿主同名接口将不可达。',
        okText: '切换为替换',
        okButtonProps: { danger: true },
        onOk: () => {
          setInterceptMode('REPLACE');
          setEngineMode((m) => (m === 'HOST' ? 'FLOW' : m));
        },
      });
      return;
    }
    Modal.confirm({
      title: '切换为「同名包裹」？',
      content: '包裹模式将转发至宿主原接口，Yu Flow 仅做增强（日志/计量/可选防护）。现有引擎实现内容不会用于线上。',
      okText: '切换为包裹',
      onOk: () => {
        setInterceptMode('WRAP');
        setEngineMode('HOST');
        setHostBinding((prev) => ({
          ...parseHostBinding(),
          ...prev,
          probeEnabled: prev.probeEnabled ?? true,
          logMode: prev.logMode || 'ERROR_ONLY',
        }));
      },
    });
  }, [interceptMode]);

  const handleProbeNow = useCallback(async () => {
    if (!values?.id) {
      message.warning('请先保存接口后再探测');
      return;
    }
    setProbing(true);
    try {
      const res: any = await probeHostApiNow(values.id);
      const r = res?.data || res;
      if (r?.status === 'ok') {
        message.success(r.message || '探活成功：宿主已注册该路由');
      } else if (r?.status === 'skip') {
        message.info(r.message || '探活已跳过');
      } else {
        message.warning(r?.message || '探活失败：宿主未注册该路由');
      }
    } catch {
      message.error('探活请求失败');
    } finally {
      setProbing(false);
    }
  }, [values?.id]);

  // ═══════════════════════════════════════════════════════════════════
  //  Header 区域配置
  // ═══════════════════════════════════════════════════════════════════

  // 顶栏控件统一高度，避免 Tag / Button / Compact 混用导致高低不齐
  const headerCtrlSize = 'middle' as const;
  const headerCtrlHeight = 32;

  const hostRouteSelectValue = url?.trim() ? `${method} ${url.trim()}` : undefined;
  const hostRouteOptions = useMemo(() => {
    const opts = hostRoutes.map((r) => {
      const value = `${r.method} ${r.path}`;
      const takenByOther = !!r.managed && !!r.managedApiId && r.managedApiId !== values?.id;
      return {
        value,
        disabled: takenByOther,
        label: (
          <Space size={6} wrap={false}>
            <span style={{ color: METHOD_COLORS[r.method] || undefined, fontWeight: 700, fontFamily: 'monospace' }}>
              {r.method}
            </span>
            <span style={{ fontFamily: 'ui-monospace, Menlo, Consolas, monospace' }}>{r.path}</span>
            {takenByOther ? (
              <Tag color="default" style={{ margin: 0 }}>已纳管</Tag>
            ) : r.managed ? (
              <Tag color="blue" style={{ margin: 0 }}>当前</Tag>
            ) : null}
          </Space>
        ),
        searchText: `${r.method} ${r.path} ${r.handlerClass || ''} ${r.handlerMethod || ''} ${r.managedApiName || ''}`,
      };
    });
    // 当前值不在列表中时仍展示（兼容历史手输草稿）
    if (hostRouteSelectValue && !opts.some((o) => o.value === hostRouteSelectValue)) {
      opts.unshift({
        value: hostRouteSelectValue,
        disabled: false,
        label: (
          <Space size={6}>
            <span style={{ fontFamily: 'monospace' }}>{hostRouteSelectValue}</span>
            <Tag style={{ margin: 0 }}>未在宿主扫描中</Tag>
          </Space>
        ),
        searchText: hostRouteSelectValue,
      });
    }
    return opts;
  }, [hostRoutes, hostRouteSelectValue, values?.id]);

  const headerTitle = (
    <Space.Compact
      className="yf-header-title-compact"
      style={{ display: 'flex', width: '100%', height: headerCtrlHeight }}
      size={headerCtrlSize}
    >
      {interceptMode === 'WRAP' ? (
        <Tooltip title="包裹模式请从宿主已有接口中选择路径（方法随选项带入）">
          <div className="yf-header-path-wrap" style={{ flex: 1, minWidth: 360, width: '100%' }}>
            <Select
              size={headerCtrlSize}
              showSearch
              allowClear
              loading={hostRoutesLoading}
              value={hostRouteSelectValue}
              placeholder="选择宿主接口，例如 GET /yu-demo/host-ping"
              status={submitAttempted && !url?.trim() ? 'error' : (urlConflictMsg ? 'error' : undefined)}
              style={{ width: '100%', height: headerCtrlHeight }}
              options={hostRouteOptions}
              optionFilterProp="searchText"
              filterOption={(input, option) => {
                const text = String((option as any)?.searchText || option?.value || '').toLowerCase();
                return text.includes(input.trim().toLowerCase());
              }}
              onChange={(v) => {
                if (!v) {
                  setUrl('');
                  return;
                }
                const idx = String(v).indexOf(' ');
                if (idx <= 0) {
                  setUrl(String(v));
                  return;
                }
                const nextMethod = String(v).slice(0, idx).trim().toUpperCase();
                const nextPath = String(v).slice(idx + 1).trim();
                setMethod(nextMethod);
                setUrl(nextPath);
                if (!name?.trim()) {
                  const short = nextPath.replace(/\//g, '_').replace(/^_|_$/g, '').slice(0, 14);
                  setName(`${nextMethod}_${short || 'host'}`.slice(0, 20));
                }
              }}
              notFoundContent={hostRoutesLoading ? '加载宿主路由…' : '未扫描到可纳管的宿主接口'}
            />
          </div>
        </Tooltip>
      ) : (
        <>
          <Select
            size={headerCtrlSize}
            value={method}
            onChange={setMethod}
            style={{ width: 116, height: headerCtrlHeight, flexShrink: 0 }}
            popupMatchSelectWidth={false}
          >
            {METHOD_OPTIONS.map((m) => (
              <Select.Option key={m} value={m}>
                <span style={{ color: METHOD_COLORS[m], fontWeight: 700, fontFamily: 'monospace' }}>{m}</span>
              </Select.Option>
            ))}
          </Select>
          <Popover
            content={
              urlConflictMsg ||
              (publishStatus === 1
                ? '可修改草稿路径；重新发布后线上路由才会切换'
                : undefined)
            }
            open={!!urlConflictMsg}
            placement="bottomLeft"
            overlayInnerStyle={urlConflictMsg ? { color: '#ff4d4f' } : undefined}
          >
            <div className="yf-header-path-wrap" style={{ flex: 1, minWidth: 280, width: '100%' }}>
              <ApiPathInput
                size={headerCtrlSize}
                value={url}
                onChange={setUrl}
                status={submitAttempted && !url?.trim() ? 'error' : (urlConflictMsg ? 'error' : undefined)}
                style={{ flex: 1, width: '100%', height: headerCtrlHeight, minWidth: 0 }}
              />
            </div>
          </Popover>
        </>
      )}
      <Input
        size={headerCtrlSize}
        value={name}
        onChange={(e) => setName(e.target.value)}
        placeholder="接口名称"
        style={{ width: 280, height: headerCtrlHeight, flexShrink: 0 }}
        status={submitAttempted && !name?.trim() ? 'error' : undefined}
      />
    </Space.Compact>
  );

  const dataViewSupported = supportsApiDataView({ serviceType: engineMode, responseType });

  const toolMenuItems: MenuProps['items'] = [
    ...(isEdit
      ? [
          {
            key: 'history',
            icon: <HistoryOutlined />,
            label: '历史版本',
            disabled: !values?.id,
            onClick: () => setHistoryOpen(true),
          },
        ]
      : []),
    {
      key: 'docs',
      icon: <FileTextOutlined />,
      label: 'API 文档',
      onClick: () => {
        const r = openPublishedApiDocCenter({
          apiId: values?.id,
          publishStatus,
        });
        if (r.reason === 'unpublished') {
          message.warning('请先发布该接口后再查看文档');
        } else if (r.reason === 'missing_id') {
          message.warning('请先保存接口后再查看文档');
        }
      },
    },
    {
      key: 'curl-import',
      icon: <ImportOutlined />,
      label: '从 cURL 导入',
      onClick: () => setCurlImportOpen(true),
    },
    {
      key: 'curl',
      icon: <CodeOutlined />,
      label: '复制 cURL',
      onClick: async () => {
        const curl = buildApiCurl(method, url);
        const ok = await copyText(curl);
        if (ok) message.success('cURL 已复制');
        else message.error('复制失败');
      },
    },
    ...(isEdit && values?.id
      ? [
          { type: 'divider' as const },
          {
            key: 'data-view',
            icon: <DatabaseOutlined />,
            label: '数据查看 / Excel',
            disabled: !dataViewSupported,
            title: dataViewSupported
              ? undefined
              : '仅 DB 模式且响应类型为 PAGE / LIST / OBJECT 可用',
            onClick: () => setDataViewOpen(true),
          },
          {
            key: 'regression',
            icon: <ExperimentOutlined />,
            label: '回归测试',
            onClick: () => setRegressionOpen(true),
          },
        ]
      : []),
    ...(isEdit && publishStatus === 1 && processedValues?.hasUnpublishedChanges
      ? [
          { type: 'divider' as const },
          {
            key: 'rollback',
            icon: <RollbackOutlined />,
            label: '回滚草稿到线上',
            danger: true,
            onClick: async () => {
              if (!values?.id) return;
              const hide = message.loading('正在回滚...');
              try {
                await rollbackApi(values.id);
                hide();
                message.success('已回滚到线上版本');
                onSubmit(true);
              } catch {
                hide();
              }
            },
          },
        ]
      : []),
    ...(isEdit && publishStatus === 1
      ? [
          { type: 'divider' as const },
          {
            key: 'unpublish',
            icon: <CloudDownloadOutlined />,
            label: '下线',
            danger: true,
            onClick: async () => {
              if (!values?.id) return;
              const hide = message.loading('正在下线...');
              try {
                await unpublishApi(values.id);
                hide();
                message.success('下线成功');
                onSubmit(true);
              } catch {
                hide();
              }
            },
          },
        ]
      : []),
  ];

  const headerExtra = (
    <Space
      className="yf-header-extra-actions"
      size={8}
      align="center"
      wrap={false}
      style={{ height: headerCtrlHeight }}
    >
      <Tag
        color={publishStatus === 1 ? 'success' : 'default'}
        style={{
          margin: 0,
          height: headerCtrlHeight,
          lineHeight: `${headerCtrlHeight}px`,
          paddingInline: 10,
          fontSize: 13,
          borderRadius: 6,
          display: 'inline-flex',
          alignItems: 'center',
          boxSizing: 'border-box',
        }}
      >
        {publishStatus === 1 ? '● 已发布' : '○ 未发布'}
      </Tag>

      <Dropdown menu={{ items: toolMenuItems }}>
        <Button size={headerCtrlSize} icon={<ToolOutlined />}>
          工具 <DownOutlined style={{ fontSize: 10 }} />
        </Button>
      </Dropdown>

      <Tooltip title={staticJsonError || undefined}>
        <span style={{ display: 'inline-flex', alignItems: 'center', height: headerCtrlHeight }}>
          <Button
            size={headerCtrlSize}
            icon={<SaveOutlined />}
            disabled={!!staticJsonError}
            onClick={() => handleSubmit()}
          >
            保存草稿
          </Button>
        </span>
      </Tooltip>
      {(publishStatus === 0 || isEdit) && (
        <Tooltip title={staticJsonError || undefined}>
          <span style={{ display: 'inline-flex', alignItems: 'center', height: headerCtrlHeight }}>
            <Button
              size={headerCtrlSize}
              type="primary"
              icon={<CloudUploadOutlined />}
              disabled={!!staticJsonError}
              onClick={handlePublishCurrentDraft}
            >
              {publishStatus === 1 ? '保存并发布' : '发布上线'}
            </Button>
          </span>
        </Tooltip>
      )}

      <Tooltip title="关闭">
        <Button
          size={headerCtrlSize}
          type="text"
          icon={<CloseOutlined />}
          onClick={onCancel}
          aria-label="关闭"
          style={{ width: headerCtrlHeight, paddingInline: 0 }}
        />
      </Tooltip>
    </Space>
  );

  // ═══════════════════════════════════════════════════════════════════
  //  Tab 内容路由 — 声明式分发到各独立 Panel
  // ═══════════════════════════════════════════════════════════════════

  const renderTabContent = () => {
    switch (activeTab) {
      case 'implementation':
        return (
          <div className={ASSET_FORM_FILL_CLASS}>
            <ImplementationPanel
              engineMode={engineMode}
              onEngineModeChange={setEngineMode}
              interceptMode={interceptMode}
              hostBinding={hostBinding}
              onHostBindingChange={setHostBinding}
              onProbeNow={handleProbeNow}
              probing={probing}
              dslContent={dslContent}
              onDslContentChange={setDslContent}
              sqlContent={sqlContent}
              onSqlContentChange={setSqlContent}
              jsonContent={jsonContent}
              onJsonContentChange={setJsonContent}
              textContent={textContent}
              onTextContentChange={setTextContent}
              dbDatasource={dbDatasource}
              onDbDatasourceChange={setDbDatasource}
              responseType={responseType}
              onResponseTypeChange={setResponseType}
              form={form}
              isEdit={isEdit}
              onSave={handleSubmit}
              onCancel={onCancel}
              apiUrl={url}
              apiMethod={method}
              apiId={values?.id}
              apiName={name}
              defaultTriggerHeaders={triggerPrefill.headers}
              defaultTriggerQueryParams={triggerPrefill.queryParams}
              defaultTriggerBody={triggerPrefill.body}
              contractJson={draftContractJson}
            />
          </div>
        );
      case 'req-schema':
        return (
          <ReqSchemaPanel
            method={method}
            url={url}
            queryParams={queryParams}
            onQueryParamsChange={setQueryParams}
            pathParams={pathParams}
            onPathParamsChange={setPathParams}
            headers={headers}
            onHeadersChange={setHeaders}
            bodyNodes={bodyNodes}
            onBodyNodesChange={setBodyNodes}
            bodyType={bodyType}
            onBodyTypeChange={setBodyType}
            rawBody={rawBody}
            onRawBodyChange={setRawBody}
          />
        );
      case 'res-schema':
        return (
          <ResSchemaPanel
            responseBody={responseBody}
            onResponseBodyChange={setResponseBody}
            responseDesc={responseDesc}
            onResponseDescChange={setResponseDesc}
            statusCode={statusCode}
            onStatusCodeChange={setStatusCode}
          />
        );
      case 'basic-info': {
        const paramSuggestions = [
          ...queryParams.map((n) => ({ source: 'query', name: n.name })),
          ...pathParams.map((n) => ({ source: 'path', name: n.name })),
          ...headers.map((n) => ({ source: 'header', name: n.name })),
          ...bodyNodes.map((n) => ({ source: 'body', name: n.name })),
        ].filter((p) => !!p.name);
        return (
          <BasicInfoPanel
            form={form}
            paramSuggestions={paramSuggestions}
            interceptMode={interceptMode}
          />
        );
      }
      case 'runtime':
        return (
          <div style={{ overflow: 'auto', height: '100%' }}>
            <AssetRuntimePanel assetType="API" assetId={values?.id} />
          </div>
        );
      default:
        return null;
    }
  };

  // ═══════════════════════════════════════════════════════════════════
  //  渲染：主体 — Drawer 包裹 PageContainer
  // ═══════════════════════════════════════════════════════════════════

  return (
    <AssetFormShell open={modalVisible} onClose={onCancel}>
      {/* 顶部 URL 信息栏 + Tab 固定；滚动落在 .ant-pro-grid-content（当前版无 children-content） */}
      <PageContainer
        className={ASSET_FORM_SHELL_CLASS}
        header={{
          title: headerTitle,
          extra: headerExtra,
          style: { paddingBottom: 0 },
          breadcrumb: {},
        }}
        tabActiveKey={activeTab}
        onTabChange={(key) => setActiveTab(key as TabKey)}
        tabList={[
          { tab: '服务实现', key: 'implementation' },
          { tab: 'API 文档定义 · 请求', key: 'req-schema' },
          { tab: 'API 文档定义 · 响应', key: 'res-schema' },
          { tab: '基础信息 / 缓存', key: 'basic-info' },
          ...(values?.id ? [{ tab: '运行', key: 'runtime' }] : []),
        ]}
        tabBarExtraContent={
          <Space size={6} style={{ marginRight: 4 }} wrap={false}>
            <span style={{ color: 'rgba(0,0,0,0.45)', fontSize: 12, whiteSpace: 'nowrap' }}>
              拦截模式
            </span>
            <Segmented
              size="small"
              value={interceptMode}
              onChange={(v) => handleInterceptModeChange(v as 'REPLACE' | 'WRAP')}
              options={[
                { label: '替换', value: 'REPLACE' },
                { label: '包裹', value: 'WRAP' },
              ]}
            />
          </Space>
        }
        style={{ height: '100%', overflow: 'hidden' }}
      >
        {/* 仅宿主存在同名 path 时提示风险，模式切换已在 Tab 行右侧 */}
        {hostRouteExists && (
          <div style={{ padding: '0 4px 8px', flexShrink: 0 }}>
            <Alert
              type={interceptMode === 'WRAP' ? 'info' : 'warning'}
              showIcon
              style={{ marginBottom: 0 }}
              message={
                <Space size={6}>
                  <span>同名拦截</span>
                  <Tag color={interceptMode === 'WRAP' ? 'cyan' : 'orange'} style={{ margin: 0 }}>
                    {interceptMode === 'WRAP' ? '包裹' : '替换'}
                  </Tag>
                </Space>
              }
              description={
                interceptMode === 'WRAP'
                  ? `宿主已有 ${method} ${url}：请求仍由宿主执行，Yu Flow 叠加计量 / 可选日志 / 可选防护。`
                  : `宿主已有 ${method} ${url}：发布后 Yu Flow 将接管该 path，宿主同名接口不再被调用。`
              }
            />
          </div>
        )}
        {renderTabContent()}
      </PageContainer>

      {values?.id && (
        <AssetVersionHistoryDrawer
          open={historyOpen}
          onClose={() => setHistoryOpen(false)}
          title={name || values.name}
          loadVersions={async () => {
            const res = await listApiVersions(values.id!);
            return (Array.isArray(res) ? res : (res as any)?.data) || [];
          }}
          restoreVersion={async (versionId) => {
            await restoreApiVersion(values.id!, versionId);
          }}
          onRestored={async () => {
            // 重新拉详情刷新表单（实现内容 + 契约 + 基础/缓存等配置）
            try {
              const res: any = await queryAutoApiConfigDetail(values.id!);
              const detail = res?.data || res;
              if (!detail) return;
              setDslContent(detail.dslContent || '');
              setSqlContent(detail.sqlContent || '');
              setJsonContent(detail.jsonContent || '');
              setTextContent(detail.textContent || '');
              {
                const st = (detail.serviceType || 'FLOW') as EngineMode;
                const im = (detail.interceptMode === 'WRAP' || st === 'HOST') ? 'WRAP' : 'REPLACE';
                setInterceptMode(im);
                setEngineMode(im === 'WRAP' ? 'HOST' : (st === 'HOST' ? 'FLOW' : st));
                setHostBinding(parseHostBinding(detail.hostBinding));
              }
              setDbDatasource(detail.datasource);
              setResponseType(detail.responseType);
              setPublishStatus(detail.publishStatus === 1 ? 1 : 0);
              if (detail.name) setName(detail.name);
              if (detail.url) setUrl(detail.url);
              if (detail.method) setMethod(detail.method);

              let cacheEnabled = false;
              let cacheTtlSeconds = 300;
              let cacheIncludePageable = true;
              let cacheKeyParams: Array<{ source: string; name: string }> = [];
              if (detail.cacheConfig) {
                try {
                  const cfg = typeof detail.cacheConfig === 'string'
                    ? JSON.parse(detail.cacheConfig)
                    : detail.cacheConfig;
                  cacheEnabled = !!cfg?.enabled;
                  cacheTtlSeconds = cfg?.ttlSeconds ?? 300;
                  cacheIncludePageable = cfg?.includePageable !== false;
                  cacheKeyParams = Array.isArray(cfg?.keyParams) ? cfg.keyParams : [];
                } catch { /* ignore */ }
              }

              const secFields = parseSecurityConfigToForm(detail.securityConfig);

              form.setFieldsValue({
                ...form.getFieldsValue(),
                name: detail.name,
                url: detail.url,
                method: detail.method,
                info: detail.info,
                tags: detail.tags
                  ? (typeof detail.tags === 'string'
                    ? detail.tags.split(',').map((t: string) => t.trim()).filter(Boolean)
                    : detail.tags)
                  : undefined,
                version: detail.version,
                level: detail.level,
                logEnabled: detail.logEnabled,
                templateId: detail.templateId,
                customSuccessWrapper: detail.customSuccessWrapper,
                customPageWrapper: detail.customPageWrapper,
                customFailWrapper: detail.customFailWrapper,
                isCustomSuccess: !!detail.customSuccessWrapper,
                isCustomPage: !!detail.customPageWrapper,
                isCustomFail: !!detail.customFailWrapper,
                cacheEnabled,
                cacheTtlSeconds,
                cacheIncludePageable,
                cacheKeyParams,
                ...secFields,
                responseType: detail.responseType,
                datasource: detail.datasource,
                serviceType: detail.serviceType,
              });

              let contract: any = null;
              if (detail.contract) {
                try {
                  contract = typeof detail.contract === 'string'
                    ? JSON.parse(detail.contract)
                    : detail.contract;
                } catch { /* ignore */ }
              }
              if (contract) {
                setQueryParams(contract.request?.query ?? []);
                setPathParams(contract.request?.pathParams ?? []);
                setHeaders(contract.request?.headers ?? []);
                setBodyNodes(contract.request?.body ?? []);
                setBodyType(contract.request?.bodyType ?? 'none');
                setRawBody(contract.request?.rawBody ?? '');
                setResponseBody(contract.responses?.['200']?.body ?? []);
                setResponseDesc(contract.responses?.['200']?.description ?? '成功');
                setStatusCode(contract.responses?.['200']?.statusCode ?? 200);
              }
            } catch {
              // 拉详情失败时仍保持历史抽屉打开
            }
          }}
        />
      )}

      {values?.id && (
        <RegressionSuitePanel
          open={regressionOpen}
          onClose={() => setRegressionOpen(false)}
          assetType="API"
          assetId={values.id}
          assetName={name || values.name}
        />
      )}

      {values?.id && (
        <ApiDataViewDrawer
          open={dataViewOpen}
          onClose={() => setDataViewOpen(false)}
          apiId={values.id}
          apiName={name || values.name}
          onPublished={() => setPublishStatus(1)}
        />
      )}

      <CurlImportModal
        open={curlImportOpen}
        onCancel={() => setCurlImportOpen(false)}
        onApply={handleCurlImportApply}
      />
    </AssetFormShell>
  );
};

export default ControllerFormV2;
