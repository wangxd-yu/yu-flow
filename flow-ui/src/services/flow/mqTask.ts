import { request } from '@umijs/max';

// ── 数据类型 ──

export interface FlowMqTask {
  id: string;
  name: string;
  directoryId?: string;
  directoryName?: string;
  /** 绑定 MQ 连接编码（flow_mq_connection.code） */
  connectionCode: string;
  /** 订阅 topic / 队列名 */
  topic: string;
  /** 消费组（Kafka group.id；Rabbit 忽略） */
  consumerGroup?: string;
  /** 消费并发数（≥1） */
  concurrency?: number;
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

export interface FlowMqTaskLog {
  id: string;
  taskId: string;
  taskName?: string;
  topic?: string;
  messageId?: string;
  /** MQ=消息触发 MANUAL=手动 */
  triggerType?: string;
  status?: string;
  costTimeMs?: number;
  hasTrace?: boolean;
  errorMsg?: string;
  traceData?: string;
  createTime?: string;
}

// ── Task CRUD ──

export async function getMqTask(id: string): Promise<FlowMqTask> {
  return request(`/flow-api/mq-task/${id}`, { method: 'GET' });
}

export async function queryMqTaskPage(params: any) {
  return request<API.PageInfo<FlowMqTask>>('/flow-api/mq-task/page', {
    method: 'GET',
    params,
  });
}

export async function createMqTask(data: Partial<FlowMqTask>) {
  return request<FlowMqTask>('/flow-api/mq-task', { method: 'POST', data });
}

export async function updateMqTask(id: string, data: Partial<FlowMqTask>) {
  return request<FlowMqTask>(`/flow-api/mq-task/${id}`, { method: 'PUT', data });
}

export async function deleteMqTask(id: string) {
  return request(`/flow-api/mq-task/${id}`, { method: 'DELETE' });
}

export async function batchDeleteMqTask(ids: string[]) {
  return request('/flow-api/mq-task/batch/delete', { method: 'PUT', data: ids });
}

// ── 启用 / 停用 ──

export async function enableMqTask(id: string) {
  return request<FlowMqTask>(`/flow-api/mq-task/${id}/enable`, { method: 'PUT' });
}

export async function disableMqTask(id: string) {
  return request<FlowMqTask>(`/flow-api/mq-task/${id}/disable`, { method: 'PUT' });
}

export async function updateMqTaskLogEnabled(id: string, enabled: boolean) {
  return request<FlowMqTask>(`/flow-api/mq-task/${id}/log-enabled`, {
    method: 'PUT',
    params: { enabled },
  });
}

/** 消费订阅运行状态（管理页展示） */
export async function getMqTaskConsumerStatus(id: string) {
  return request<boolean>(`/flow-api/mq-task/${id}/consumer-status`, { method: 'GET' });
}

/** 存活订阅的任务 ID 集合（列表页批量展示，避免逐行请求） */
export async function getRunningMqTaskIds() {
  return request<string[]>('/flow-api/mq-task/consumer-status/running', { method: 'GET' });
}

// ── 发布 ──

export async function publishMqTask(id: string) {
  return request<FlowMqTask>(`/flow-api/mq-task/${id}/publish`, { method: 'PUT' });
}

export async function unpublishMqTask(id: string) {
  return request<FlowMqTask>(`/flow-api/mq-task/${id}/unpublish`, { method: 'PUT' });
}

export async function republishMqTask(id: string) {
  return request<FlowMqTask>(`/flow-api/mq-task/${id}/republish`, { method: 'PUT' });
}

export async function rollbackMqTask(id: string) {
  return request<FlowMqTask>(`/flow-api/mq-task/${id}/rollback`, { method: 'PUT' });
}

// ── 模拟触发 / 调试 ──

/** 手动模拟一条消息触发（异步，结果在任务日志查看，仅已发布任务可用） */
export async function simulateMqTask(id: string, message?: string) {
  return request(`/flow-api/mq-task/${id}/simulate`, {
    method: 'POST',
    data: { message },
  });
}

/** 调试运行可能包含 HttpRequest 等长耗时节点，需高于全局 10s */
const DEBUG_REQUEST_TIMEOUT_MS = 5 * 60 * 1000;

/** 调试运行草稿 DSL：body=模拟消息体，headers=消息头，queryParams.topic=来源 topic */
export async function debugRunMqTask(
  dslContent: string,
  source?: {
    sourceRef?: string;
    sourceName?: string;
    topic?: string;
    body?: string;
    headers?: Record<string, string>;
  },
) {
  return request<any>('/flow-api/mq-task/debug/run', {
    method: 'POST',
    data: {
      dslContent,
      sourceRef: source?.sourceRef,
      sourceName: source?.sourceName,
      queryParams: source?.topic ? { topic: source.topic } : undefined,
      body: source?.body,
      headers: source?.headers,
    },
    timeout: DEBUG_REQUEST_TIMEOUT_MS,
  });
}

// ── Task Log ──

export async function queryMqTaskLogPage(params: any) {
  return request<API.PageInfo<FlowMqTaskLog>>('/flow-api/mq-task/log/page', {
    method: 'GET',
    params,
  });
}

export async function getMqTaskLog(id: string): Promise<FlowMqTaskLog> {
  return request(`/flow-api/mq-task/log/${id}`, { method: 'GET' });
}

export async function clearMqTaskLog(taskId: string) {
  return request(`/flow-api/mq-task/log/clear/${taskId}`, { method: 'DELETE' });
}
