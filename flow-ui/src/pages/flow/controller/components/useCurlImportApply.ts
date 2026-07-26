/**
 * useCurlImportApply
 * ─────────────────────────────────────────────────────────────────────────────
 * 将 cURL 解析结果覆盖到当前 API 请求契约。
 */
import { useCallback } from 'react';
import { Modal, message } from 'antd';
import type { CurlImportApplyPayload } from './CurlImportModal';
import type { SchemaNode, BodyType } from '@/components/flow/ApiContractDesigner/types';

export interface UseCurlImportApplyOptions {
  url: string;
  queryParams: SchemaNode[];
  headers: SchemaNode[];
  bodyNodes: SchemaNode[];
  bodyType: BodyType;
  rawBody: string;
  setMethod: (v: string) => void;
  setUrl: (v: string) => void;
  setQueryParams: (v: SchemaNode[]) => void;
  setHeaders: (v: SchemaNode[]) => void;
  setBodyType: (v: BodyType) => void;
  setBodyNodes: (v: SchemaNode[]) => void;
  setRawBody: (v: string) => void;
  setActiveTab: (v: 'implementation' | 'req-schema' | 'res-schema' | 'basic-info' | 'runtime') => void;
  setCurlImportOpen: (v: boolean) => void;
}

export function useCurlImportApply(options: UseCurlImportApplyOptions) {
  const {
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
  } = options;

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

  return handleCurlImportApply;
}
