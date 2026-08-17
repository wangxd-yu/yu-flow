/**
 * useControllerFormSubmit
 * ─────────────────────────────────────────────────────────────────────────────
 * ControllerForm 的提交逻辑：校验、构建 payload、调用新增/更新接口。
 */
import { useCallback } from 'react';
import { message } from 'antd';
import type { FormInstance } from 'antd/es/form';
import { merge } from 'lodash';
import { addAutoApiConfig, updateAutoApiConfig } from '@/services/flow/flowController';
import { getStaticJsonError } from './panels/ImplementationPanel';
import { stringifyHostBinding } from './panels/HostWrapConfig';
import { buildSecurityConfigFromForm } from './securityConfig';
import { buildPrivacyConfigFromForm, stringifyPrivacyConfig } from './privacyConfig';
import type { EngineMode } from './panels/ImplementationPanel';
import type { SchemaNode, BodyType } from '@/components/flow/ApiContractDesigner/types';

export interface UseControllerFormSubmitOptions {
  form: FormInstance;
  name: string;
  url: string;
  method: string;
  publishStatus: 0 | 1;
  responseType?: string;
  engineMode: EngineMode;
  interceptMode: 'REPLACE' | 'WRAP';
  hostBinding: any;
  dbDatasource?: string;
  dslContent: string;
  sqlContent: string;
  jsonContent: string;
  textContent: string;
  urlConflictMsg?: string | null;
  isEdit: boolean;
  values: any;
  onSubmit: (success: boolean) => void;
  queryParams: SchemaNode[];
  pathParams: SchemaNode[];
  headers: SchemaNode[];
  bodyNodes: SchemaNode[];
  bodyType: BodyType;
  rawBody: string;
  responseBody: SchemaNode[];
  responseDesc: string;
  statusCode: number;
}

export function useControllerFormSubmit(options: UseControllerFormSubmitOptions) {
  const {
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
  } = options;

  const setSubmitAttempted = () => {};

  const handleSubmit = useCallback(async (
    externalScript?: any,
    submitOptions: { notify?: boolean; closeOnSuccess?: boolean } = {},
  ) => {
    const { notify = true, closeOnSuccess = true } = submitOptions;
    setSubmitAttempted();
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
    let hide: any = null;
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
      // directoryId 在「基本信息」Tab：未打开该 Tab 时 validateFields 不含此字段，需从初始 values 回补
      const directoryId = formValues.directoryId ?? values?.directoryId;
      const payload = {
        ...formValues,
        directoryId,
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
        // 日志保留天数：留空提交 -1，后端语义为清除 API 级配置（回退系统保留天数）
        logRetentionDays: formValues.logRetentionDays ?? -1,
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
        privacyConfig: stringifyPrivacyConfig(buildPrivacyConfigFromForm(formValues)),
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

  return handleSubmit;
}
