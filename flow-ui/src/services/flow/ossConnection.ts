import { request } from '@umijs/max';

export type OssPublicAccessMode = 'ANON' | 'NGINX_PROXY';
export type OssPrivateDownloadMode = 'STREAM' | 'PRESIGN';
export type OssHealthStatus = 'HEALTHY' | 'UNHEALTHY' | 'UNKNOWN';

export interface OssConnection {
  id: string;
  name: string;
  code: string;
  endpoint: string;
  accessKey?: string;
  /** 编辑时留空表示不修改 */
  secretKey?: string;
  hasSecretKey?: boolean;
  region?: string;
  pathStyle?: boolean;
  publicBucket?: string;
  privateBucket?: string;
  publicBaseUrl?: string;
  keyPrefix?: string;
  publicAccessMode?: OssPublicAccessMode;
  privateDownloadMode?: OssPrivateDownloadMode;
  presignExpireSeconds?: number;
  enabled?: boolean;
  healthStatus?: OssHealthStatus;
  lastErrorMsg?: string;
  lastTestTime?: string;
  info?: string;
  createTime?: string;
  updateTime?: string;
}

export interface OssConnectionTestResult {
  success?: boolean;
  message?: string;
  buckets?: Record<
    string,
    {
      bucket?: string;
      exists?: boolean;
      policySummary?: string;
    }
  >;
}

// ── CRUD ──

export async function queryOssConnectionPage(params: any) {
  return request<API.PageInfo<OssConnection>>('/flow-api/oss/connections/page', {
    method: 'GET',
    params,
  });
}

export async function getOssConnection(id: string): Promise<OssConnection> {
  const result = await request(`/flow-api/oss/connections/${id}`, { method: 'GET' });
  return (result as any)?.data ?? result;
}

export async function createOssConnection(data: Partial<OssConnection>) {
  return request<OssConnection>('/flow-api/oss/connections', {
    method: 'POST',
    data,
  });
}

export async function updateOssConnection(id: string, data: Partial<OssConnection>) {
  return request<OssConnection>(`/flow-api/oss/connections/${id}`, {
    method: 'PUT',
    data,
  });
}

export async function deleteOssConnection(id: string) {
  return request(`/flow-api/oss/connections/${id}`, { method: 'DELETE' });
}

export async function batchDeleteOssConnection(ids: string[]) {
  return request('/flow-api/oss/connections/batch/delete', {
    method: 'PUT',
    data: ids,
  });
}

// ── 启停 / 下拉 / 测试 ──

export async function enableOssConnection(id: string) {
  return request<OssConnection>(`/flow-api/oss/connections/${id}/enable`, { method: 'PUT' });
}

export async function disableOssConnection(id: string) {
  return request<OssConnection>(`/flow-api/oss/connections/${id}/disable`, { method: 'PUT' });
}

export async function queryOssConnectionOptions() {
  const result = await request<OssConnection[]>('/flow-api/oss/connections/options', {
    method: 'GET',
  });
  return (result as any)?.data ?? result;
}

export async function testOssConnection(data: Partial<OssConnection>) {
  const result = await request<OssConnectionTestResult>('/flow-api/oss/connections/test', {
    method: 'POST',
    data,
  });
  return (result as any)?.data ?? result;
}

export async function testOssConnectionById(id: string) {
  const result = await request<OssConnectionTestResult>(
    `/flow-api/oss/connections/${id}/test`,
    { method: 'POST' },
  );
  return (result as any)?.data ?? result;
}
