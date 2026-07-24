import { request } from '@umijs/max';

const BASE = '/flow-api/alerts';

export interface AlertChannel {
  id: string;
  name: string;
  type: 'WEBHOOK' | 'EMAIL' | string;
  configJson?: string;
  enabled: number;
  createTime?: string;
  updateTime?: string;
}

export interface AlertRule {
  id: string;
  name: string;
  enabled: number;
  scopeAssetTypes?: string;
  window: string;
  minHealth: string;
  topN: number;
  channelIds?: string;
  intervalMinutes: number;
  dedupMinutes: number;
  createTime?: string;
  updateTime?: string;
}

export interface AlertEvent {
  id: string;
  ruleId?: string;
  ruleName?: string;
  fingerprint?: string;
  assetType?: string;
  assetId?: string;
  assetName?: string;
  health?: string;
  errorRate?: number;
  failCount?: number;
  window?: string;
  channelType?: string;
  channelId?: string;
  status: string;
  payloadJson?: string;
  errorMsg?: string;
  firedAt?: string;
}

const unwrapPage = (result: any) => ({
  data: result?.items || result?.data?.items || [],
  success: true,
  total: result?.total || result?.data?.total || 0,
});

export async function pageAlertChannels(params: Record<string, any>) {
  const result = await request(`${BASE}/channels/page`, {
    method: 'GET',
    params: {
      name: params.name,
      type: params.type,
      enabled: params.enabled,
      page: params.current || 1,
      size: params.pageSize || 50,
    },
  });
  return unwrapPage(result);
}

export async function listAlertChannels(): Promise<AlertChannel[]> {
  const result = await request(`${BASE}/channels/list`, { method: 'GET' });
  return result || [];
}

export async function createAlertChannel(data: Partial<AlertChannel>) {
  return request(`${BASE}/channels`, { method: 'POST', data });
}

export async function updateAlertChannel(id: string, data: Partial<AlertChannel>) {
  return request(`${BASE}/channels/${id}`, { method: 'PUT', data });
}

export async function deleteAlertChannel(id: string) {
  return request(`${BASE}/channels/${id}`, { method: 'DELETE' });
}

export async function testAlertChannel(id: string) {
  return request(`${BASE}/channels/${id}/test`, { method: 'POST' });
}

export async function pageAlertRules(params: Record<string, any>) {
  const result = await request(`${BASE}/rules/page`, {
    method: 'GET',
    params: {
      name: params.name,
      enabled: params.enabled,
      page: params.current || 1,
      size: params.pageSize || 20,
    },
  });
  return unwrapPage(result);
}

export async function createAlertRule(data: Partial<AlertRule>) {
  return request(`${BASE}/rules`, { method: 'POST', data });
}

export async function updateAlertRule(id: string, data: Partial<AlertRule>) {
  return request(`${BASE}/rules/${id}`, { method: 'PUT', data });
}

export async function deleteAlertRule(id: string) {
  return request(`${BASE}/rules/${id}`, { method: 'DELETE' });
}

export async function runAlertRuleOnce(id: string) {
  return request(`${BASE}/rules/${id}/run-once`, { method: 'POST' });
}

export async function pageAlertEvents(params: Record<string, any>) {
  const { current, pageSize, firedAt, ...rest } = params;
  let from: string | undefined;
  let to: string | undefined;
  if (firedAt && Array.isArray(firedAt)) {
    from = firedAt[0];
    to = firedAt[1];
  }
  const result = await request(`${BASE}/events/page`, {
    method: 'GET',
    params: {
      ...rest,
      from,
      to,
      page: current || 1,
      size: pageSize || 20,
    },
  });
  return unwrapPage(result);
}
