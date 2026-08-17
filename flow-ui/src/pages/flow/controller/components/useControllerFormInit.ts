/**
 * useControllerFormInit
 * ─────────────────────────────────────────────────────────────────────────────
 * ControllerForm 的 Drawer 打开时数据还原逻辑。
 */
import { useEffect } from 'react';
import type { FormInstance } from 'antd/es/form';
import { parseSecurityConfigToForm } from './securityConfig';
import { parsePrivacyConfigToForm } from './privacyConfig';
import { parseHostBinding } from './panels/HostWrapConfig';
import type { EngineMode } from './panels/ImplementationPanel';
import type { SchemaNode, BodyType } from '@/components/flow/ApiContractDesigner/types';

export interface UseControllerFormInitOptions {
  modalVisible: boolean;
  processedValues: Record<string, any>;
  form: FormInstance;
  initialTab?: 'implementation' | 'req-schema' | 'res-schema' | 'basic-info' | 'runtime';
  setMethod: (v: string) => void;
  setUrl: (v: string) => void;
  setName: (v: string) => void;
  setSubmitAttempted: (v: boolean) => void;
  setDslContent: (v: string) => void;
  setSqlContent: (v: string) => void;
  setJsonContent: (v: string) => void;
  setTextContent: (v: string) => void;
  setInterceptMode: (v: 'REPLACE' | 'WRAP') => void;
  setEngineMode: (v: EngineMode) => void;
  setHostBinding: (v: any) => void;
  setDbDatasource: (v: string | undefined) => void;
  setResponseType: (v: string | undefined) => void;
  setPublishStatus: (v: 0 | 1) => void;
  setActiveTab: (v: 'implementation' | 'req-schema' | 'res-schema' | 'basic-info' | 'runtime') => void;
  setQueryParams: (v: SchemaNode[]) => void;
  setPathParams: (v: SchemaNode[]) => void;
  setHeaders: (v: SchemaNode[]) => void;
  setBodyNodes: (v: SchemaNode[]) => void;
  setBodyType: (v: BodyType) => void;
  setRawBody: (v: string) => void;
  setResponseBody: (v: SchemaNode[]) => void;
  setResponseDesc: (v: string) => void;
  setStatusCode: (v: number) => void;
}

export function useControllerFormInit(options: UseControllerFormInitOptions) {
  const {
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
  } = options;

  useEffect(() => {
    if (!modalVisible) return;

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
    const privacyFields = parsePrivacyConfigToForm(processedValues.privacyConfig);

    form.setFieldsValue({
      ...processedValues,
      logMode: processedValues.logMode || 'SYSTEM_DEFAULT',
      isCustomSuccess: !!processedValues.customSuccessWrapper,
      isCustomPage: !!processedValues.customPageWrapper,
      isCustomFail: !!processedValues.customFailWrapper,
      cacheEnabled,
      cacheTtlSeconds,
      cacheIncludePageable,
      cacheKeyParams,
      ...secFields,
      ...privacyFields,
    });

    setMethod(processedValues.method || 'GET');
    setUrl(processedValues.url || '');
    setName(processedValues.name || '');
    setSubmitAttempted(false);
    setDslContent(processedValues.dslContent || processedValues.config || '');
    setSqlContent(processedValues.sqlContent || '');
    setJsonContent(processedValues.jsonContent || '');
    setTextContent(processedValues.textContent || '');

    const st = (processedValues.serviceType || 'FLOW') as EngineMode;
    const im = (processedValues.interceptMode === 'WRAP' || st === 'HOST')
      ? 'WRAP'
      : 'REPLACE';
    setInterceptMode(im);
    setEngineMode(im === 'WRAP' ? 'HOST' : (st === 'HOST' ? 'FLOW' : st));
    setHostBinding(parseHostBinding(processedValues.hostBinding));

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
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [processedValues, form, modalVisible, initialTab]);
}
