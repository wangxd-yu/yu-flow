import { request } from '@umijs/max';

export interface OpenPlatform {
  id?: string;
  name: string;
  code: string;
  status?: number;
  contact?: string;
  remark?: string;
  ipAllowlist?: string;
  /** 0关 1开 */
  openCallLogEnabled?: number;
  rateLimitQps?: number | null;
  expireAt?: string;
  createTime?: string;
  updateTime?: string;
  credentialCount?: number;
  grantCount?: number;
}

export interface OpenCredential {
  id: string;
  platformId: string;
  appKey: string;
  appSecret?: string;
  secretHint?: string;
  status: number;
  expireAt?: string;
  createTime?: string;
}

function unwrap<T>(res: any): T {
  return (res?.data !== undefined ? res.data : res) as T;
}

export async function pageOpenPlatforms(params: {
  name?: string;
  code?: string;
  status?: number;
  page?: number;
  size?: number;
}) {
  const res = await request('/flow-api/open-platforms/page', { method: 'GET', params });
  return unwrap<{ items: OpenPlatform[]; total: number }>(res);
}

export async function getOpenPlatform(id: string) {
  const res = await request(`/flow-api/open-platforms/${id}`, { method: 'GET' });
  return unwrap<OpenPlatform>(res);
}

export async function createOpenPlatform(body: Partial<OpenPlatform>) {
  const res = await request('/flow-api/open-platforms', { method: 'POST', data: body });
  return unwrap<OpenPlatform>(res);
}

export async function updateOpenPlatform(id: string, body: Partial<OpenPlatform>) {
  const res = await request(`/flow-api/open-platforms/${id}`, { method: 'PUT', data: body });
  return unwrap<OpenPlatform>(res);
}

export async function deleteOpenPlatform(id: string) {
  await request(`/flow-api/open-platforms/${id}`, { method: 'DELETE' });
}

export async function listCredentials(platformId: string) {
  const res = await request(`/flow-api/open-platforms/${platformId}/credentials`, { method: 'GET' });
  return unwrap<OpenCredential[]>(res) || [];
}

export async function createCredential(platformId: string) {
  const res = await request(`/flow-api/open-platforms/${platformId}/credentials`, { method: 'POST' });
  return unwrap<OpenCredential>(res);
}

export async function rotateCredential(platformId: string, credentialId: string) {
  const res = await request(
    `/flow-api/open-platforms/${platformId}/credentials/${credentialId}/rotate`,
    { method: 'POST' },
  );
  return unwrap<OpenCredential>(res);
}

export async function disableCredential(platformId: string, credentialId: string) {
  await request(`/flow-api/open-platforms/${platformId}/credentials/${credentialId}/disable`, {
    method: 'PUT',
  });
}

export interface OpenGrantItem {
  apiId: string;
  apiName?: string;
  method?: string;
  url?: string;
  /** 空=跟随接口 method；否则如 GET,POST */
  allowMethods?: string;
  publishStatus?: number;
  valid: boolean;
}

export interface OpenCallLogItem {
  id: string;
  platformId?: string;
  appKey?: string;
  apiId?: string;
  method?: string;
  path?: string;
  status?: number;
  costMs?: number;
  errorCode?: string;
  requestId?: string;
  createTime?: string;
}

export async function listGrants(platformId: string) {
  const res = await request(`/flow-api/open-platforms/${platformId}/grants`, { method: 'GET' });
  return unwrap<string[]>(res) || [];
}

export async function listGrantDetails(platformId: string) {
  const res = await request(`/flow-api/open-platforms/${platformId}/grants/detail`, {
    method: 'GET',
  });
  return unwrap<OpenGrantItem[]>(res) || [];
}

export async function purgeInvalidGrants(platformId: string) {
  const res = await request(`/flow-api/open-platforms/${platformId}/grants/purge-invalid`, {
    method: 'POST',
  });
  return unwrap<{ removed: number }>(res);
}

export async function replaceGrants(
  platformId: string,
  apiIds: string[],
  allowMethodsByApiId?: Record<string, string>,
) {
  await request(`/flow-api/open-platforms/${platformId}/grants`, {
    method: 'PUT',
    data: { apiIds, allowMethodsByApiId },
  });
}

export async function pageOpenCallLogs(
  platformId: string,
  params: { page?: number; size?: number; path?: string; status?: number; errorCode?: string },
) {
  const res = await request(`/flow-api/open-platforms/${platformId}/call-logs/page`, {
    method: 'GET',
    params,
  });
  return unwrap<{ items: OpenCallLogItem[]; total: number }>(res);
}

export async function getPlatformOpenApi(platformId: string) {
  const res = await request(`/flow-api/open-platforms/${platformId}/openapi`, { method: 'GET' });
  return unwrap<any>(res);
}

export async function getPlatformOpenApiYaml(platformId: string) {
  const res = await request(`/flow-api/open-platforms/${platformId}/export/openapi.yaml`, {
    method: 'GET',
  });
  return unwrap<string>(res);
}

export async function getPlatformPostman(platformId: string) {
  const res = await request(`/flow-api/open-platforms/${platformId}/export/postman`, {
    method: 'GET',
  });
  return unwrap<any>(res);
}

export async function getPlatformMarkdown(platformId: string) {
  const res = await request(`/flow-api/open-platforms/${platformId}/export/markdown`, {
    method: 'GET',
  });
  return unwrap<string>(res);
}

export async function getPlatformGuide(platformId: string) {
  const res = await request(`/flow-api/open-platforms/${platformId}/export/guide`, {
    method: 'GET',
  });
  return unwrap<string>(res);
}

export async function getOpenEntryMeta() {
  const res = await request('/flow-api/open-platforms/meta/entry', { method: 'GET' });
  return unwrap<{
    enabled: boolean;
    entryPrefix: string;
    allowPlainSecret: boolean;
    skewSeconds?: number;
    callLogEnabled?: boolean;
    includeBodyHash?: boolean;
    rotateGraceHours?: number;
  }>(res);
}
