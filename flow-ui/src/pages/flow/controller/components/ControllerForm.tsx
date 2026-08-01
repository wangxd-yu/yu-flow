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

import { merge } from 'lodash';
import { PageContainer } from '@ant-design/pro-components';
import { history, request } from '@umijs/max';
import {
  listApiVersions, restoreApiVersion, queryAutoApiConfigDetail,
  probeHostApiNow, checkHostApiRouteExists, listHostApiRoutes,
  type HostApiRoute,
} from '@/services/flow/flowController';
import AssetVersionHistoryDrawer from '@/components/flow/AssetVersionHistoryDrawer';
import {
  AssetFormShell,
  ASSET_FORM_SHELL_CLASS,
} from '@/components/flow/ops';
import CurlImportModal, { type CurlImportApplyPayload } from './CurlImportModal';
import { parseSecurityConfigToForm } from './securityConfig';

// ── Panel 子组件 ──
import { getStaticJsonError, type EngineMode } from './panels/ImplementationPanel';
import { parseHostBinding, stringifyHostBinding, type HostWrapBinding } from './panels/HostWrapConfig';
import { useControllerFormInit } from './useControllerFormInit';
import { useControllerFormSubmit } from './useControllerFormSubmit';
import { useCurlImportApply } from './useCurlImportApply';
import { useInterceptModeChange } from './useInterceptModeChange';
import { usePublishCurrentDraft } from './usePublishCurrentDraft';
import RegressionSuitePanel from '@/components/flow/release/RegressionSuitePanel';
import ApiDataViewDrawer from './ApiDataViewDrawer';
import ControllerFormHeader from './ControllerFormHeader';
import ControllerFormTabs, { type TabKey as ControllerFormTabKey } from './ControllerFormTabs';
import type { SchemaNode, BodyType } from '@/components/flow/ApiContractDesigner/types';
import { buildApiTriggerPrefillFromContract } from '@/components/flow/debugger/apiTriggerPrefill';


// ═══════════════════════════════════════════════════════════════════════════
//  类型定义
// ═══════════════════════════════════════════════════════════════════════════

/** Tab Key 类型 */
type TabKey = ControllerFormTabKey;

export type ControllerFormV2Props = {
  onCancel: () => void;
  onSubmit: (success: boolean) => void;
  modalVisible: boolean;
  values?: Partial<any>;
  isEdit: boolean;
  /** 打开时默认 Tab（如运行中心深链） */
  initialTab?: TabKey;
};

// ═══════════════════════════════════════════════════════════════════════════
//  主组件（瘦编排器）
// ═══════════════════════════════════════════════════════════════════════════

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

  useControllerFormInit({
    modalVisible,
    processedValues,
    form,
    initialTab,
    setMethod,
    setUrl,
    setName,
    setSubmitAttempted,
    setDslContent,
    setSqlContent,
    setJsonContent,
    setTextContent,
    setInterceptMode,
    setEngineMode,
    setHostBinding,
    setDbDatasource,
    setResponseType,
    setPublishStatus,
    setActiveTab,
    setQueryParams,
    setPathParams,
    setHeaders,
    setBodyNodes,
    setBodyType,
    setRawBody,
    setResponseBody,
    setResponseDesc,
    setStatusCode,
  });

  // ═══════════════════════════════════════════════════════════════════
  //  提交逻辑
  // ═══════════════════════════════════════════════════════════════════

  const handleSubmit = useControllerFormSubmit({
    form,
    name,
    url,
    method,
    publishStatus,
    responseType,
    engineMode,
    interceptMode,
    hostBinding,
    dbDatasource,
    dslContent,
    sqlContent,
    jsonContent,
    textContent,
    urlConflictMsg,
    isEdit,
    values,
    onSubmit,
    queryParams,
    pathParams,
    headers,
    bodyNodes,
    bodyType,
    rawBody,
    responseBody,
    responseDesc,
    statusCode,
  });

  const [regressionOpen, setRegressionOpen] = useState(false);
  const [dataViewOpen, setDataViewOpen] = useState(false);
  const [curlImportOpen, setCurlImportOpen] = useState(false);

  const handleCurlImportApply = useCurlImportApply({
    url,
    queryParams,
    headers,
    bodyNodes,
    bodyType,
    rawBody,
    setMethod,
    setUrl,
    setQueryParams,
    setHeaders,
    setBodyType,
    setBodyNodes,
    setRawBody,
    setActiveTab,
    setCurlImportOpen,
  });

  const handlePublishCurrentDraft = usePublishCurrentDraft({
    isEdit,
    publishStatus,
    name,
    interceptMode,
    engineMode,
    method,
    url,
    hostRouteExists,
    onSubmit,
    handleSubmit,
  });

  const handleInterceptModeChange = useInterceptModeChange({
    interceptMode,
    setInterceptMode,
    setEngineMode,
    setHostBinding,
  });

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
  //  渲染：主体 — Drawer 包裹 PageContainer
  // ═══════════════════════════════════════════════════════════════════

  return (
    <AssetFormShell open={modalVisible} onClose={onCancel}>
      {/* 顶部 URL 信息栏 + Tab 固定；滚动落在 .ant-pro-grid-content（当前版无 children-content） */}
      <ControllerFormHeader
        interceptMode={interceptMode}
        method={method}
        onMethodChange={setMethod}
        url={url}
        onUrlChange={setUrl}
        name={name}
        onNameChange={setName}
        submitAttempted={submitAttempted}
        urlConflictMsg={urlConflictMsg}
        publishStatus={publishStatus}
        hostRoutes={hostRoutes}
        hostRoutesLoading={hostRoutesLoading}
        valuesId={values?.id}
        isEdit={isEdit}
        hasUnpublishedChanges={processedValues?.hasUnpublishedChanges}
        engineMode={engineMode}
        responseType={responseType}
        staticJsonError={staticJsonError}
        onSubmit={handleSubmit}
        onPublish={handlePublishCurrentDraft}
        onCancel={onCancel}
        onOpenHistory={() => setHistoryOpen(true)}
        onOpenDataView={() => setDataViewOpen(true)}
        onOpenRegression={() => setRegressionOpen(true)}
        onOpenCurlImport={() => setCurlImportOpen(true)}
        onSubmitSuccess={onSubmit}
      >
        {(headerTitle, headerExtra) => (
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
            <ControllerFormTabs
              activeTab={activeTab}
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
              url={url}
              method={method}
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
              onCurlImport={() => setCurlImportOpen(true)}
              responseBody={responseBody}
              onResponseBodyChange={setResponseBody}
              responseDesc={responseDesc}
              onResponseDescChange={setResponseDesc}
              statusCode={statusCode}
              onStatusCodeChange={setStatusCode}
              valuesId={values?.id}
            />
          </PageContainer>
        )}
      </ControllerFormHeader>

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
                logRetentionDays: detail.logRetentionDays,
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
