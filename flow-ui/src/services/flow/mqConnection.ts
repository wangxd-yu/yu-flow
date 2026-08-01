import { request } from '@umijs/max';

/** MQ 类型 */
export type MqType = 'RABBITMQ' | 'KAFKA';

/** 连接健康度 */
export type MqHealthStatus = 'HEALTHY' | 'UNHEALTHY' | 'UNKNOWN';

export interface MqConnection {
  id: string;
  name: string;
  /** 连接编码（流程 DSL / MQ 任务通过 code 引用） */
  code: string;
  mqType: MqType;
  /** host:port，多个逗号分隔（Kafka 即 bootstrap.servers） */
  servers: string;
  /** 虚拟主机（仅 RabbitMQ） */
  virtualHost?: string;
  username?: string;
  /** 编辑时留空表示不修改 */
  password?: string;
  /** 0=停用 1=启用 */
  enabled?: number | boolean;
  healthStatus?: MqHealthStatus;
  lastErrorMsg?: string;
  lastTestTime?: string;
  info?: string;
  createTime?: string;
  updateTime?: string;
}

// ── CRUD ──

export async function queryMqConnectionPage(params: any) {
  return request<API.PageInfo<MqConnection>>('/flow-api/mq-connection/page', {
    method: 'GET',
    params,
  });
}

export async function getMqConnection(id: string): Promise<MqConnection> {
  return request(`/flow-api/mq-connection/${id}`, { method: 'GET' });
}

export async function createMqConnection(data: Partial<MqConnection>) {
  return request<MqConnection>('/flow-api/mq-connection', {
    method: 'POST',
    data,
  });
}

export async function updateMqConnection(id: string, data: Partial<MqConnection>) {
  return request<MqConnection>(`/flow-api/mq-connection/${id}`, {
    method: 'PUT',
    data,
  });
}

export async function deleteMqConnection(id: string) {
  return request(`/flow-api/mq-connection/${id}`, { method: 'DELETE' });
}

export async function batchDeleteMqConnection(ids: string[]) {
  return request('/flow-api/mq-connection/batch/delete', {
    method: 'PUT',
    data: ids,
  });
}

// ── 启停 / 下拉 / 测试 ──

export async function enableMqConnection(id: string) {
  return request<MqConnection>(`/flow-api/mq-connection/${id}/enable`, { method: 'PUT' });
}

export async function disableMqConnection(id: string) {
  return request<MqConnection>(`/flow-api/mq-connection/${id}/disable`, { method: 'PUT' });
}

/** 全部启用的连接（流程节点 / MQ 任务下拉选项） */
export async function queryMqConnectionOptions() {
  return request<MqConnection[]>('/flow-api/mq-connection/options', { method: 'GET' });
}

/**
 * topic 候选（Topic 输入框的提示项）。
 *
 * 后端拉取失败会降级为空数组，调用方保持手工输入即可。
 * RabbitMQ 不支持枚举，同样返回空数组。
 */
export async function queryMqTopics(
  code: string,
  keyword?: string,
  limit = 50,
) {
  return request<string[]>(
    `/flow-api/mq-connection/${encodeURIComponent(code)}/topics`,
    { method: 'GET', params: { keyword, limit } },
  );
}

/** 按表单参数测试连接（密码留空且 id 有值时回填库中密码） */
export async function testMqConnection(data: Partial<MqConnection>) {
  return request<boolean>('/flow-api/mq-connection/test', {
    method: 'POST',
    data,
  });
}

/** 按 ID 测试已保存的连接 */
export async function testMqConnectionById(id: string) {
  return request<boolean>(`/flow-api/mq-connection/${id}/test`, { method: 'POST' });
}
