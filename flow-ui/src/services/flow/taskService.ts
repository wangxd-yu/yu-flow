import { request } from '@umijs/max';
import type { AssetVersionItem } from '@/components/flow/AssetVersionHistoryDrawer';

export type { AssetVersionItem };

// ── 数据类型 ──

export interface FlowTask {
  id: string;
  name: string;
  directoryId?: string;
  directoryName?: string;
  cron: string;
  enabled?: boolean;
  logEnabled?: boolean;
  /** 日志保留天数：null/undefined=跟随系统配置，0=永久保留，>0=自定义天数；提交 -1 表示清除任务级配置 */
  logRetentionDays?: number | null;
  dslContent?: string;
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

export interface FlowTaskLog {
  id: string;
  taskId: string;
  taskName?: string;
  triggerType?: string;
  status?: string;
  costTimeMs?: number;
  hasTrace?: boolean;
  errorMsg?: string;
  traceData?: string;
  createTime?: string;
}

// ── Task CRUD ──

export async function getTask(id: string): Promise<FlowTask> {
  return request(`/flow-api/task/${id}`, { method: 'GET' });
}

export async function queryTaskPage(params: any) {
  return request<API.PageInfo<FlowTask>>('/flow-api/task/page', {
    method: 'GET',
    params,
  });
}

export async function createTask(data: Partial<FlowTask>) {
  return request<FlowTask>('/flow-api/task', { method: 'POST', data });
}

export async function updateTask(id: string, data: Partial<FlowTask>) {
  return request<FlowTask>(`/flow-api/task/${id}`, { method: 'PUT', data });
}

export async function deleteTask(id: string) {
  return request(`/flow-api/task/${id}`, { method: 'DELETE' });
}

export async function batchDeleteTask(ids: string[]) {
  return request('/flow-api/task/batch/delete', { method: 'PUT', data: ids });
}

// ── 启用 / 停用 ──

export async function enableTask(id: string) {
  return request<FlowTask>(`/flow-api/task/${id}/enable`, { method: 'PUT' });
}

export async function disableTask(id: string) {
  return request<FlowTask>(`/flow-api/task/${id}/disable`, { method: 'PUT' });
}

export async function updateTaskLogEnabled(id: string, enabled: boolean) {
  return request<FlowTask>(`/flow-api/task/${id}/log-enabled`, {
    method: 'PUT',
    params: { enabled },
  });
}

// ── 发布 / 历史版本 ──

export async function publishTask(id: string, envCode = 'DEV') {
  return request<FlowTask>(`/flow-api/task/${id}/publish`, {
    method: 'PUT',
    params: { envCode },
  });
}

export async function unpublishTask(id: string) {
  return request<FlowTask>(`/flow-api/task/${id}/unpublish`, { method: 'PUT' });
}

export async function republishTask(id: string, envCode = 'DEV') {
  return request<FlowTask>(`/flow-api/task/${id}/republish`, {
    method: 'PUT',
    params: { envCode },
  });
}

export async function rollbackTask(id: string) {
  return request<FlowTask>(`/flow-api/task/${id}/rollback`, { method: 'PUT' });
}

export async function listTaskVersions(id: string) {
  return request<AssetVersionItem[]>(`/flow-api/task/${id}/versions`, { method: 'GET' });
}

/** 回退：同步覆盖草稿与线上快照 */
export async function restoreTaskVersion(id: string, versionId: string) {
  return request<FlowTask>(`/flow-api/task/${id}/versions/${versionId}/restore`, {
    method: 'POST',
  });
}

// ── 手动触发 / 调试 ──

export async function runTaskNow(id: string) {
  return request(`/flow-api/task/${id}/run`, { method: 'POST' });
}

/** 调试运行可能包含 HttpRequest 等长耗时节点，需高于全局 10s */
const DEBUG_REQUEST_TIMEOUT_MS = 5 * 60 * 1000;

export async function debugRunTask(
  dslContent: string,
  source?: { sourceRef?: string; sourceName?: string; cron?: string },
) {
  return request<any>('/flow-api/task/debug/run', {
    method: 'POST',
    data: {
      dslContent,
      sourceRef: source?.sourceRef,
      sourceName: source?.sourceName,
      cron: source?.cron,
    },
    timeout: DEBUG_REQUEST_TIMEOUT_MS,
  });
}

// ── Task Log ──

export async function queryTaskLogPage(params: any) {
  return request<API.PageInfo<FlowTaskLog>>('/flow-api/log/task/page', {
    method: 'GET',
    params,
  });
}

export async function getTaskLog(id: string): Promise<FlowTaskLog> {
  return request(`/flow-api/log/task/${id}`, { method: 'GET' });
}

export async function clearTaskLog(taskId: string) {
  return request(`/flow-api/log/task/clear/${taskId}`, { method: 'DELETE' });
}
