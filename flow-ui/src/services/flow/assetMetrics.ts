import { request } from '@umijs/max';

export type MetricsAssetType = 'API' | 'TASK' | 'SERVICE' | 'MQ_TASK' | 'PLATFORM';
export type MetricsWindow = '15m' | '1h' | '24h' | '7d' | '30d';

export interface AssetMetricsSummary {
  assetType: string;
  assetId: string;
  window: string;
  totalCalls: number;
  successCount: number;
  failCount: number;
  skippedCount: number;
  authFailCount?: number;
  successRate?: number | null;
  errorRate?: number | null;
  avgCostMs?: number | null;
  p50Ms?: number | null;
  p95Ms?: number | null;
  p99Ms?: number | null;
  lastSuccessAt?: number | null;
  lastFailAt?: number | null;
  consecutiveFail: number;
}

export interface AssetMetricsSeries {
  assetType: string;
  assetId: string;
  window: string;
  granularity: string;
  points: Array<{
    time: string;
    success: number;
    fail: number;
    skipped: number;
    p95Ms?: number | null;
  }>;
}

export interface AssetHealth {
  assetType: string;
  assetId: string;
  health: 'ok' | 'warn' | 'error' | 'empty' | string;
  successRate?: number | null;
  consecutiveFail: number;
  totalCalls: number;
  window: string;
}

export interface AssetMetricsRankItem {
  assetType: string;
  assetId: string;
  assetName?: string;
  totalCalls: number;
  successCount: number;
  failCount: number;
  successRate?: number | null;
  errorRate?: number | null;
  p95Ms?: number | null;
  /** 当前连续失败（现值，任何一次成功即清零） */
  consecutiveFail: number;
  /** 窗口内最大连续失败（分钟桶推导近似） */
  maxConsecutiveFail?: number;
  health: string;
}

function unwrap<T>(res: any): T {
  return (res?.data !== undefined ? res.data : res) as T;
}

export async function getAssetMetricsSummary(
  assetType: MetricsAssetType,
  assetId: string,
  window: MetricsWindow = '24h',
) {
  const res = await request(`/flow-api/metrics/${assetType}/${assetId}/summary`, {
    method: 'GET',
    params: { window },
  });
  return unwrap<AssetMetricsSummary>(res);
}

export async function getAssetMetricsSeries(
  assetType: MetricsAssetType,
  assetId: string,
  window: MetricsWindow = '24h',
) {
  const res = await request(`/flow-api/metrics/${assetType}/${assetId}/series`, {
    method: 'GET',
    params: { window },
  });
  return unwrap<AssetMetricsSeries>(res);
}

export async function batchAssetHealth(
  items: Array<{ assetType: MetricsAssetType; assetId: string }>,
) {
  if (!items?.length) {
    return [];
  }
  const res = await request('/flow-api/metrics/health', {
    method: 'POST',
    data: { items },
    // 避免部分环境把 charset 拼进 Content-Type 后后端拒收
    headers: { 'Content-Type': 'application/json' },
  });
  return unwrap<AssetHealth[]>(res) || [];
}

export async function getMetricsRank(params: {
  assetType: MetricsAssetType;
  window?: MetricsWindow;
  orderBy?: 'errorRate' | 'p95' | 'calls';
  limit?: number;
}) {
  const res = await request('/flow-api/metrics/rank', {
    method: 'GET',
    params,
  });
  return unwrap<AssetMetricsRankItem[]>(res) || [];
}

export async function getMetricsAnomalies(params?: {
  window?: MetricsWindow;
  limit?: number;
}) {
  const res = await request('/flow-api/metrics/anomalies', {
    method: 'GET',
    params,
  });
  return unwrap<AssetMetricsRankItem[]>(res) || [];
}
