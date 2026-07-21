import { request } from '@umijs/max';

export interface FlowServiceFlow {
  id: string;
  name: string;
  directoryId?: string;
  directoryName?: string;
  enabled?: boolean;
  logEnabled?: boolean;
  dslContent?: string;
  /** 服务契约 JSON：{ inputs, outputs, outputDescription } */
  contract?: string;
  /** 0=未发布 1=已发布 */
  publishStatus?: number;
  publishedSnapshot?: string;
  hasUnpublishedChanges?: boolean;
  publishTime?: string;
  info?: string;
  tags?: string;
  createTime?: string;
  updateTime?: string;
}

/** 从发布快照解析契约（供调用方同步入参） */
export function resolveRuntimeContract(svc: FlowServiceFlow | null | undefined): string | undefined {
  if (!svc) return undefined;
  if (svc.publishStatus === 1 && svc.publishedSnapshot) {
    try {
      const snap = JSON.parse(svc.publishedSnapshot);
      if (typeof snap?.contract === 'string') return snap.contract;
    } catch {
      /* fall through */
    }
  }
  return svc.contract;
}

export interface FlowServiceLog {
  id: string;
  serviceId: string;
  serviceName?: string;
  triggerType?: string;
  status?: string;
  costTimeMs?: number;
  hasTrace?: boolean;
  errorMsg?: string;
  traceData?: string;
  createTime?: string;
}

export async function getServiceFlow(id: string): Promise<FlowServiceFlow> {
  return request(`/flow-api/service-flow/${id}`, { method: 'GET' });
}

export async function queryServiceFlowPage(params: any) {
  return request<API.PageInfo<FlowServiceFlow>>('/flow-api/service-flow/page', {
    method: 'GET',
    params,
  });
}

export async function createServiceFlow(data: Partial<FlowServiceFlow>) {
  return request<FlowServiceFlow>('/flow-api/service-flow', { method: 'POST', data });
}

export async function updateServiceFlow(id: string, data: Partial<FlowServiceFlow>) {
  return request<FlowServiceFlow>(`/flow-api/service-flow/${id}`, { method: 'PUT', data });
}

export async function batchDeleteServiceFlow(ids: string[]) {
  return request('/flow-api/service-flow/batch/delete', { method: 'PUT', data: ids });
}

export async function enableServiceFlow(id: string) {
  return request<FlowServiceFlow>(`/flow-api/service-flow/${id}/enable`, { method: 'PUT' });
}

export async function disableServiceFlow(id: string) {
  return request<FlowServiceFlow>(`/flow-api/service-flow/${id}/disable`, { method: 'PUT' });
}

export async function updateServiceFlowLogEnabled(id: string, enabled: boolean) {
  return request<FlowServiceFlow>(`/flow-api/service-flow/${id}/log-enabled`, {
    method: 'PUT',
    params: { enabled },
  });
}

export async function publishServiceFlow(id: string) {
  return request<FlowServiceFlow>(`/flow-api/service-flow/${id}/publish`, { method: 'PUT' });
}

export async function unpublishServiceFlow(id: string) {
  return request<FlowServiceFlow>(`/flow-api/service-flow/${id}/unpublish`, { method: 'PUT' });
}

/** 仍引用该服务的可读标签（下线确认） */
export async function listServiceFlowReferences(id: string): Promise<string[]> {
  const res = await request<any>(`/flow-api/service-flow/${id}/references`, { method: 'GET' });
  const data = res?.data ?? res;
  return Array.isArray(data) ? data : [];
}

export async function republishServiceFlow(id: string) {
  return request<FlowServiceFlow>(`/flow-api/service-flow/${id}/republish`, { method: 'PUT' });
}

export async function rollbackServiceFlow(id: string) {
  return request<FlowServiceFlow>(`/flow-api/service-flow/${id}/rollback`, { method: 'PUT' });
}

export interface AssetVersionItem {
  id: string;
  versionNo: number;
  source?: string;
  remark?: string;
  publisher?: string;
  publishTime?: string;
  current?: boolean;
}

export async function listServiceFlowVersions(id: string) {
  return request<AssetVersionItem[]>(`/flow-api/service-flow/${id}/versions`, { method: 'GET' });
}

/** 回退：同步覆盖草稿与线上快照 */
export async function restoreServiceFlowVersion(id: string, versionId: string) {
  return request<FlowServiceFlow>(
    `/flow-api/service-flow/${id}/versions/${versionId}/restore`,
    { method: 'PUT' },
  );
}

const DEBUG_REQUEST_TIMEOUT_MS = 5 * 60 * 1000;
const RUN_REQUEST_TIMEOUT_MS = 5 * 60 * 1000;

/**
 * 手动同步调用（返回业务输出，非 FlowTrace）。
 * 后端 triggerType=MANUAL：走已保存草稿 DSL + 草稿契约校验。
 */
export async function runServiceFlow(id: string, input?: Record<string, unknown>) {
  return request<any>(`/flow-api/service-flow/${id}/run`, {
    method: 'POST',
    data: input ?? {},
    timeout: RUN_REQUEST_TIMEOUT_MS,
  });
}

export async function debugRunServiceFlow(
  dslContent: string,
  source?: { sourceRef?: string; sourceName?: string },
  body?: string,
  contract?: string,
) {
  return request<any>('/flow-api/service-flow/debug/run', {
    method: 'POST',
    data: {
      dslContent,
      sourceRef: source?.sourceRef,
      sourceName: source?.sourceName,
      body,
      contract,
    },
    timeout: DEBUG_REQUEST_TIMEOUT_MS,
  });
}

export async function queryServiceLogPage(params: any) {
  return request<API.PageInfo<FlowServiceLog>>('/flow-api/log/service/page', {
    method: 'GET',
    params,
  });
}

export async function getServiceLog(id: string): Promise<FlowServiceLog> {
  return request(`/flow-api/log/service/${id}`, { method: 'GET' });
}

export async function clearServiceLog(serviceId: string) {
  return request(`/flow-api/log/service/clear/${serviceId}`, { method: 'DELETE' });
}
