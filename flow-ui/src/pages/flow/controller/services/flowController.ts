import { request } from '@umijs/max';

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
  createTime?: string;
  updateTime?: string;
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

export async function getDebugSessionStatus(sessionId: string) {
  return request<any>(`/flow-api/debug/session/${sessionId}/status`, {
    method: 'GET',
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

/** 发布 API（冻结草稿为线上快照） */
export async function publishApi(id: string) {
  return request<FlowController>(`/flow-api/api/${id}/publish`, { method: 'PUT' });
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
export async function republishApi(id: string) {
  return request<FlowController>(`/flow-api/api/${id}/republish`, { method: 'PUT' });
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