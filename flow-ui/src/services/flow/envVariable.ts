import { request } from '@umijs/max';

const BASE = '/flow-api/sys-env-variables';

export interface EnvVariable {
  id: string;
  code: string;
  /** 敏感变量恒为空，只能通过 valueSet 判断是否已填 */
  value?: string | null;
  secret: boolean;
  valueSet: boolean;
  remark?: string;
  updateBy?: string;
  updateTime?: string;
}

export interface SaveEnvVariable {
  code?: string;
  /** 修改敏感变量时不传表示保留原值 */
  value?: string;
  secret?: boolean;
  remark?: string;
}

export async function listEnvVariables(keyword?: string): Promise<EnvVariable[]> {
  return (await request(BASE, { method: 'GET', params: { keyword } })) || [];
}

export async function createEnvVariable(data: SaveEnvVariable) {
  return request<EnvVariable>(BASE, { method: 'POST', data });
}

export async function updateEnvVariable(id: string, data: SaveEnvVariable) {
  return request<EnvVariable>(`${BASE}/${id}`, { method: 'PUT', data });
}

export async function deleteEnvVariable(id: string) {
  return request(`${BASE}/${id}`, { method: 'DELETE' });
}

export interface ExtractUsage {
  assetType: string;
  assetId: string;
  assetName?: string;
  url: string;
}

export interface ExtractCandidate {
  baseUrl: string;
  suggestedCode: string;
  codeExists: boolean;
  usages: ExtractUsage[];
}

export interface ExtractResult {
  code: string;
  variableCreated: boolean;
  updatedAssets: number;
  updatedNodes: number;
}

export async function listExtractCandidates(): Promise<ExtractCandidate[]> {
  return (await request(`${BASE}/extract-candidates`, { method: 'GET' })) || [];
}

export async function extractEnvVariable(data: { baseUrl: string; code: string; remark?: string }): Promise<ExtractResult> {
  return request(`${BASE}/extract`, { method: 'POST', data });
}
