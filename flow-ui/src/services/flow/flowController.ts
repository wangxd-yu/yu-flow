import { request } from '@umijs/max';
import type { AssetVersionItem } from '@/components/flow/AssetVersionHistoryDrawer';

export type { AssetVersionItem };

export interface FlowController {
  id: string;
  name: string;
  description?: string;
  url: string;
  datasource?: string;
  directoryId?: string;
  directoryName?: string;
  module?: string;
  version?: string;
  /** @deprecated 使用 dslContent/sqlContent/jsonContent/textContent 替代 */
  config?: string;
  dslContent?: string;
  sqlContent?: string;
  jsonContent?: string;
  textContent?: string;
  method?: 'GET' | 'POST' | 'PUT' | 'DELETE';
  publishStatus?: 0 | 1;
  logEnabled?: boolean;
  /**
   * 查询响应 Redis 缓存配置 JSON 字符串
   * { enabled, ttlSeconds, keyParams:[{source,name}], includePageable }
   */
  cacheConfig?: string;
  level?: number;
  rule?: string;
  tags?: string[]; // 标签字段，支持数组格式
  /** 基座响应模板 ID */
  templateId?: string;
  /** 自定义成功返回包装（局部重载） */
  customSuccessWrapper?: string;
  /** 自定义分页返回包装（局部重载） */
  customPageWrapper?: string;
  /** 自定义失败返回包装（局部重载） */
  customFailWrapper?: string;
  /** 最近一次发布时间 */
  publishTime?: string;
  /** 是否存在未发布的草稿变更 */
  hasUnpublishedChanges?: boolean;
  /**
   * 入站防护 JSON：authMode/antiReplay/rateLimit/ipAllowlist/timeoutMs
   */
  securityConfig?: string;
  /** 数据查看 / Excel 导出配置 JSON */
  viewExportConfig?: string;
  responseType?: string;
  serviceType?: string;
  contract?: string;
  createTime?: string;
  updateTime?: string;
}

export interface ViewExportColumn {
  field: string;
  header?: string;
  width?: number;
  exportable?: boolean;
  visible?: boolean;
  /** 模板占位符 key，对应 {.key}，默认等于 field */
  templateKey?: string;
}

export interface ViewExportConfig {
  enabled?: boolean;
  sheetName?: string;
  maxExportRows?: number;
  itemsPath?: string;
  /** DYNAMIC | TEMPLATE */
  exportMode?: 'DYNAMIC' | 'TEMPLATE' | string;
  templateFileId?: string;
  templateSheetNo?: number;
  columns?: ViewExportColumn[];
}

export interface ApiExcelTemplateMeta {
  id?: string;
  apiId?: string;
  fileName?: string;
  contentType?: string;
  fileSize?: number;
  createTime?: string;
  updateTime?: string;
  present?: boolean;
  hasListPlaceholder?: boolean;
  warning?: string;
}

export interface ApiDataPreviewResult {
  apiId: string;
  apiName?: string;
  serviceType?: string;
  responseType?: string;
  useDraft?: boolean;
  columns?: ViewExportColumn[];
  rows?: Record<string, any>[];
  object?: Record<string, any>;
  total?: number;
  page?: number;
  size?: number;
  pages?: number;
}

export async function queryAutoApiConfigDetail(id: string): Promise<FlowController> {
  return request(`/flow-api/api/${id}`, {
    method: 'GET',
  });
}
export async function queryAutoApiConfigList(params: any) {
  return request<API.PageInfo<FlowController>>('/flow-api/api/page', {
    method: 'GET',
    params,
  });
}

export async function addAutoApiConfig(data: Partial<FlowController>) {
  return request<FlowController>('/flow-api/api', {
    method: 'POST',
    data,
  });
}

export async function updateAutoApiConfig(id: string, data: Partial<FlowController>) {
  return request<FlowController>(`/flow-api/api/${id}`, {
    method: 'PUT',
    data,
  });
}

export async function updateAutoApiLogEnabled(id: string, enabled: boolean) {
  return request<FlowController>(`/flow-api/api/${id}/log-enabled`, {
    method: 'PUT',
    params: { enabled },
  });
}

/** 更新查询响应缓存配置 */
export async function updateApiCacheConfig(id: string, cacheConfig: object | string | null) {
  return request<FlowController>(`/flow-api/api/${id}/cache-config`, {
    method: 'PUT',
    data: cacheConfig,
  });
}

export interface ApiCacheEntry {
  key: string;
  ttlSeconds: number;
  sizeBytes: number;
}

export interface ApiCacheContent {
  key: string;
  ttlSeconds: number;
  sizeBytes: number;
  content: string;
  truncated: boolean;
}

/** 查询接口当前生效的响应缓存条目 */
export async function listApiCacheEntries(id: string) {
  return request<ApiCacheEntry[]>(`/flow-api/api/${id}/cache/entries`, {
    method: 'GET',
  });
}

/** 按需查看单条响应缓存内容 */
export async function getApiCacheEntryContent(id: string, key: string) {
  return request<ApiCacheContent>(`/flow-api/api/${id}/cache/entries/content`, {
    method: 'GET',
    params: { key },
  });
}

/** 清除接口全部响应缓存 */
export async function clearApiCache(id: string) {
  return request<number>(`/flow-api/api/${id}/cache`, {
    method: 'DELETE',
  });
}

/** 清除单条响应缓存 */
export async function clearApiCacheEntry(id: string, key: string) {
  return request<boolean>(`/flow-api/api/${id}/cache/entries`, {
    method: 'DELETE',
    params: { key },
  });
}

export async function deleteAutoApiConfig(id: string) {
  return request(`/flow-api/api/${id}`, {
    method: 'DELETE',
  });
}

export async function batchDeleteAutoApiConfig(ids: string[]) {
  return request('/flow-api/api/batch/delete', {
    method: 'PUT',
    data: ids,
  });
}

export async function batchMoveAutoApiConfig(ids: string[], targetDirectoryId?: string) {
  return request('/flow-api/api/batch/moveToDir', {
    method: 'PUT',
    data: { ids, targetDirectoryId },
  });
}

/** 调试运行可能包含 HttpRequest 等长耗时节点，需高于全局 10s */
const DEBUG_REQUEST_TIMEOUT_MS = 5 * 60 * 1000;

export async function debugRunAutoApiConfig(data: any) {
  return request<any>('/flow-api/api/debug/run', {
    method: 'POST',
    data,
    timeout: DEBUG_REQUEST_TIMEOUT_MS,
  });
}

/** 数据库模式调试运行 */
export async function debugRunDbApiConfig(data: {
  sqlContent: string;
  datasource?: string;
  responseType?: string;
  headers?: Record<string, string>;
  queryParams?: Record<string, string>;
  body?: string;
  page?: number;
  size?: number;
  sourceRef?: string;
  sourceName?: string;
  /** 默认 true：调试写操作事务回退，不落库 */
  rollbackTransaction?: boolean;
  /** 有值时后端按契约校验 */
  contract?: string;
}) {
  return request<any>('/flow-api/api/debug/db/run', {
    method: 'POST',
    data,
    timeout: DEBUG_REQUEST_TIMEOUT_MS,
  });
}

export async function startDebugSession(data: any) {
  return request<any>('/flow-api/debug/session/start', {
    method: 'POST',
    data,
    timeout: DEBUG_REQUEST_TIMEOUT_MS,
  });
}

export async function getDebugSessionStatus(
  sessionId: string,
  opts?: { stepOffset?: number; stepLimit?: number },
) {
  const stepOffset = opts?.stepOffset ?? 0;
  const stepLimit = opts?.stepLimit ?? 100;
  return request<any>(`/flow-api/debug/session/${sessionId}/status`, {
    method: 'GET',
    params: { stepOffset, stepLimit },
  });
}

export async function getDebugSessionStepLogs(
  sessionId: string,
  opts?: { stepOffset?: number; stepLimit?: number },
) {
  const stepOffset = opts?.stepOffset ?? 0;
  const stepLimit = opts?.stepLimit ?? 100;
  return request<any>(`/flow-api/debug/session/${sessionId}/step-logs`, {
    method: 'GET',
    params: { stepOffset, stepLimit },
  });
}

export async function resumeDebugSession(sessionId: string, data: any) {
  return request<any>(`/flow-api/debug/session/${sessionId}/resume`, {
    method: 'POST',
    data,
    timeout: DEBUG_REQUEST_TIMEOUT_MS,
  });
}

export async function cancelDebugSession(sessionId: string) {
  return request<any>(`/flow-api/debug/session/${sessionId}`, {
    method: 'DELETE',
  });
}

// ============================= 版本快照 API =============================

/** 发布 API（冻结草稿为线上快照；envCode 默认 DEV） */
export async function publishApi(id: string, envCode = 'DEV') {
  return request<FlowController>(`/flow-api/api/${id}/publish`, {
    method: 'PUT',
    params: { envCode },
  });
}

/** 下线 API（清除快照，停止线上服务） */
export async function unpublishApi(id: string) {
  return request<FlowController>(`/flow-api/api/${id}/unpublish`, { method: 'PUT' });
}

/** 回滚草稿到发布版本 */
export async function rollbackApi(id: string) {
  return request<FlowController>(`/flow-api/api/${id}/rollback`, { method: 'PUT' });
}

/** 重新发布（将最新草稿冻结为快照并上线） */
export async function republishApi(id: string, envCode = 'DEV') {
  return request<FlowController>(`/flow-api/api/${id}/republish`, {
    method: 'PUT',
    params: { envCode },
  });
}

/** 历史版本列表 */
export async function listApiVersions(id: string) {
  return request<AssetVersionItem[]>(`/flow-api/api/${id}/versions`, { method: 'GET' });
}

/** 回退至指定历史版本（同步覆盖草稿与线上快照） */
export async function restoreApiVersion(id: string, versionId: string) {
  return request<FlowController>(`/flow-api/api/${id}/versions/${versionId}/restore`, {
    method: 'POST',
  });
}

/** 管理端数据预览 */
export async function previewApiData(
  id: string,
  data: {
    useDraft?: boolean;
    queryParams?: Record<string, string>;
    bodyParams?: Record<string, any>;
    pathParams?: Record<string, string>;
    page?: number;
    size?: number;
  },
): Promise<ApiDataPreviewResult> {
  const result = await request(`/flow-api/api/${id}/data/preview`, {
    method: 'POST',
    data,
  });
  return (result as any)?.data ?? result;
}

function apiAuthHeaders(): Record<string, string> {
  const token = localStorage.getItem('flow_token') || '';
  return { 'Flow-Authorization': token };
}

function apiContextPath(): string {
  return process.env.NODE_ENV === 'production' ? (window as any).__CONTEXT_PATH__ || '' : '';
}

function triggerBlobDownload(blob: Blob, filename: string) {
  const a = document.createElement('a');
  a.href = URL.createObjectURL(blob);
  a.download = filename;
  a.click();
  URL.revokeObjectURL(a.href);
}

function filenameFromContentDisposition(cd: string | null, fallback: string) {
  let filename = fallback;
  const m = /filename\*=UTF-8''([^;]+)|filename="?([^";]+)"?/i.exec(cd || '');
  if (m) {
    filename = decodeURIComponent(m[1] || m[2]);
  }
  return filename;
}

/** 管理端 Excel 导出（blob 下载） */
export async function exportApiDataExcel(
  id: string,
  data: {
    useDraft?: boolean;
    queryParams?: Record<string, string>;
    bodyParams?: Record<string, any>;
    pathParams?: Record<string, string>;
  },
): Promise<{
  exportMode?: string;
  fallback?: string;
  fallbackMessage?: string;
  rows?: number;
}> {
  const url = `${apiContextPath()}/flow-api/api/${id}/data/export`;
  const res = await fetch(url, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      ...apiAuthHeaders(),
    },
    credentials: 'include',
    body: JSON.stringify(data || {}),
  });
  if (!res.ok) {
    let msg = `导出失败 (${res.status})`;
    try {
      const j = await res.json();
      msg = j?.msg || j?.message || msg;
    } catch {
      /* ignore */
    }
    throw new Error(msg);
  }
  const exportMode = res.headers.get('X-Export-Mode') || undefined;
  const fallback = res.headers.get('X-Export-Fallback') || undefined;
  let fallbackMessage: string | undefined;
  const rawMsg = res.headers.get('X-Export-Fallback-Message');
  if (rawMsg) {
    try {
      fallbackMessage = decodeURIComponent(rawMsg);
    } catch {
      fallbackMessage = rawMsg;
    }
  }
  const rowsHeader = res.headers.get('X-Export-Rows');
  const rows = rowsHeader != null && rowsHeader !== '' ? Number(rowsHeader) : undefined;
  const blob = await res.blob();
  if (!blob || blob.size === 0) {
    throw new Error('导出文件为空，请检查 SQL 或列配置');
  }
  triggerBlobDownload(blob, filenameFromContentDisposition(res.headers.get('Content-Disposition'), 'export.xlsx'));
  return { exportMode, fallback, fallbackMessage, rows };
}

export async function getApiExcelTemplateMeta(id: string): Promise<ApiExcelTemplateMeta> {
  const result = await request(`/flow-api/api/${id}/data/export-template`, { method: 'GET' });
  return (result as any)?.data ?? result;
}

export async function uploadApiExcelTemplate(id: string, file: File): Promise<ApiExcelTemplateMeta> {
  const form = new FormData();
  form.append('file', file);
  const result = await request(`/flow-api/api/${id}/data/export-template`, {
    method: 'POST',
    data: form,
  });
  return (result as any)?.data ?? result;
}

export async function deleteApiExcelTemplate(id: string): Promise<void> {
  await request(`/flow-api/api/${id}/data/export-template`, { method: 'DELETE' });
}

export async function downloadApiExcelTemplateSample(id: string): Promise<void> {
  const url = `${apiContextPath()}/flow-api/api/${id}/data/export-template/sample`;
  const res = await fetch(url, {
    method: 'GET',
    headers: apiAuthHeaders(),
    credentials: 'include',
  });
  if (!res.ok) throw new Error(`下载示例模板失败 (${res.status})`);
  const blob = await res.blob();
  triggerBlobDownload(
    blob,
    filenameFromContentDisposition(res.headers.get('Content-Disposition'), 'export_template_sample.xlsx'),
  );
}

export async function downloadApiExcelTemplateFile(id: string): Promise<void> {
  const url = `${apiContextPath()}/flow-api/api/${id}/data/export-template/file`;
  const res = await fetch(url, {
    method: 'GET',
    headers: apiAuthHeaders(),
    credentials: 'include',
  });
  if (!res.ok) {
    let msg = `下载模板失败 (${res.status})`;
    try {
      const j = await res.json();
      msg = j?.msg || j?.message || msg;
    } catch {
      /* ignore */
    }
    throw new Error(msg);
  }
  const blob = await res.blob();
  triggerBlobDownload(
    blob,
    filenameFromContentDisposition(res.headers.get('Content-Disposition'), 'template.xlsx'),
  );
}