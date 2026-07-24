import { request } from '@umijs/max';

const BASE = '/flow-api/release';

export interface FlowEnv {
  id: string;
  code: string;
  name: string;
  requireSuitePass?: number;
  passTtlHours?: number;
  enabled?: number;
  sortOrder?: number;
  remark?: string;
}

export interface PublishGateCheckItem {
  code: string;
  name: string;
  status: 'PASS' | 'FAIL' | 'SKIP' | string;
  message?: string;
}

export interface PublishGateResult {
  assetType: string;
  assetId: string;
  envCode: string;
  passed: boolean;
  message?: string;
  checks?: PublishGateCheckItem[];
}

export interface RegressionCase {
  id?: string;
  suiteId?: string;
  name: string;
  sortOrder?: number;
  enabled?: number;
  headersJson?: string;
  queryJson?: string;
  body?: string;
  expectTraceStatus?: string;
  expectJsonPath?: string;
  expectValue?: string;
  timeoutMs?: number;
}

export interface RegressionSuite {
  id: string;
  name: string;
  assetType: string;
  assetId: string;
  enabled?: number;
  caseCount?: number;
  cases?: RegressionCase[];
  createTime?: string;
  updateTime?: string;
}

export interface RegressionRunCase {
  id: string;
  caseId: string;
  caseName?: string;
  status: string;
  durationMs?: number;
  message?: string;
  detailJson?: string;
}

export interface RegressionRun {
  id: string;
  suiteId: string;
  assetType: string;
  assetId: string;
  envCode: string;
  status: string;
  totalCases?: number;
  passedCases?: number;
  failedCases?: number;
  startedAt?: string;
  finishedAt?: string;
  triggeredBy?: string;
  summary?: string;
  cases?: RegressionRunCase[];
}

export async function listReleaseEnvs(): Promise<FlowEnv[]> {
  return (await request(`${BASE}/envs`, { method: 'GET' })) || [];
}

export async function checkPublishGate(params: {
  assetType: string;
  assetId: string;
  envCode?: string;
}): Promise<PublishGateResult> {
  return request(`${BASE}/gate/check`, {
    method: 'GET',
    params: {
      assetType: params.assetType,
      assetId: params.assetId,
      envCode: params.envCode || 'DEV',
    },
  });
}

export async function pageRegressionSuites(params: {
  assetType?: string;
  assetId?: string;
  current?: number;
  pageSize?: number;
}) {
  const result = await request(`${BASE}/suites/page`, {
    method: 'GET',
    params: {
      assetType: params.assetType,
      assetId: params.assetId,
      page: params.current || 1,
      size: params.pageSize || 20,
    },
  });
  return {
    data: result?.items || result?.data?.items || [],
    success: true,
    total: result?.total || result?.data?.total || 0,
  };
}

export async function getRegressionSuite(id: string, withCases = true): Promise<RegressionSuite> {
  return request(`${BASE}/suites/${id}`, {
    method: 'GET',
    params: { withCases },
  });
}

export async function createRegressionSuite(data: Partial<RegressionSuite>) {
  return request(`${BASE}/suites`, { method: 'POST', data });
}

export async function updateRegressionSuite(id: string, data: Partial<RegressionSuite>) {
  return request(`${BASE}/suites/${id}`, { method: 'PUT', data });
}

export async function deleteRegressionSuite(id: string) {
  return request(`${BASE}/suites/${id}`, { method: 'DELETE' });
}

export async function createRegressionCase(suiteId: string, data: Partial<RegressionCase>) {
  return request(`${BASE}/suites/${suiteId}/cases`, { method: 'POST', data });
}

export async function updateRegressionCase(caseId: string, data: Partial<RegressionCase>) {
  return request(`${BASE}/cases/${caseId}`, { method: 'PUT', data });
}

export async function deleteRegressionCase(caseId: string) {
  return request(`${BASE}/cases/${caseId}`, { method: 'DELETE' });
}

export async function runRegressionSuite(suiteId: string, envCode = 'DEV'): Promise<RegressionRun> {
  return request(`${BASE}/suites/${suiteId}/run`, {
    method: 'POST',
    data: { envCode },
  });
}

export interface BatchRunRegressionItem {
  assetId: string;
  assetName?: string;
  suiteId?: string;
  runId?: string;
  status: string;
  message?: string;
}

export interface BatchRunRegressionResult {
  assetType: string;
  envCode: string;
  total: number;
  passed: number;
  failed: number;
  skipped: number;
  error: number;
  items?: BatchRunRegressionItem[];
}

/** 列表批量回归（无套件默认跳过） */
export async function batchRunRegression(data: {
  assetType: string;
  assetIds: string[];
  envCode?: string;
  missingSuitePolicy?: 'SKIP' | 'FAIL';
}): Promise<BatchRunRegressionResult> {
  return request(`${BASE}/batch-run`, {
    method: 'POST',
    data: {
      assetType: data.assetType,
      assetIds: data.assetIds,
      envCode: data.envCode || 'DEV',
      missingSuitePolicy: data.missingSuitePolicy || 'SKIP',
    },
  });
}

export async function getRegressionRun(runId: string): Promise<RegressionRun> {
  return request(`${BASE}/runs/${runId}`, { method: 'GET' });
}
