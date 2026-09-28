import { request } from '@umijs/max';
import type { TransferReport } from './assetTransfer';

const BASE = '/flow-api/release-imports';

export interface ReleaseGateItem {
  assetType: string;
  assetId: string;
  assetName?: string;
  passed: boolean;
  message?: string;
}

export type SignatureStatus = 'VERIFIED' | 'UNSIGNED' | 'INVALID' | 'UNVERIFIABLE' | 'DISABLED';

export const SIGNATURE_LABELS: Record<SignatureStatus, { text: string; color: string }> = {
  VERIFIED: { text: '签名已校验', color: 'success' },
  UNSIGNED: { text: '未签名（本环境要求签名）', color: 'error' },
  INVALID: { text: '签名无效', color: 'error' },
  UNVERIFIABLE: { text: '已签名，本环境无法校验', color: 'warning' },
  DISABLED: { text: '未启用签名', color: 'default' },
};

export interface ModelTableCheck {
  modelId: string;
  modelName?: string;
  datasource?: string;
  tableName?: string;
  /** null 表示无法检查 */
  exists?: boolean | null;
  ddl?: string;
  message?: string;
}

export interface FieldDiff {
  field: string;
  before?: string;
  after?: string;
}

/** 需要运维逐项核对的高权限变更：全局宏表达式、开放平台授权增减 */
export interface PrivilegedChange {
  assetType: string;
  key: string;
  name?: string;
  description: string;
}

export interface ReleaseInspectResult {
  releaseCode: string;
  releaseName?: string;
  releaseRemark?: string;
  sourceEnv?: string;
  targetEnv?: string;
  exportedBy?: string;
  exportedAt?: string;
  packageDigest: string;
  changelog?: string;
  itemCount: number;
  report: TransferReport;
  gates: ReleaseGateItem[];
  signatureStatus?: SignatureStatus;
  signatureRequired?: boolean;
  privilegedChanges?: PrivilegedChange[];
  modelTables?: ModelTableCheck[];
  blocked: boolean;
  blockReasons: string[];
  warnings: string[];
}

export type ReleaseImportStatus = 'SUCCESS' | 'FAILED' | 'ROLLED_BACK';

export interface ReleaseImportLog {
  id: string;
  releaseCode?: string;
  releaseName?: string;
  packageDigest?: string;
  sourceEnv?: string;
  targetEnv?: string;
  status: ReleaseImportStatus;
  summary?: string;
  errorMessage?: string;
  importedBy?: string;
  importedTime?: string;
  rolledBackBy?: string;
  rolledBackTime?: string;
  report?: TransferReport;
  rollbackable: boolean;
  /** 导入提交后的运行时自检问题；为空表示自检通过 */
  runtimeIssues?: string[];
}

export const IMPORT_STATUS: Record<ReleaseImportStatus, { text: string; color: string }> = {
  SUCCESS: { text: '成功', color: 'success' },
  FAILED: { text: '失败', color: 'error' },
  ROLLED_BACK: { text: '已回滚', color: 'default' },
};

function form(file: File, extra?: Record<string, string>) {
  const data = new FormData();
  data.append('file', file);
  Object.entries(extra || {}).forEach(([k, v]) => data.append(k, v));
  return data;
}

export async function inspectReleasePackage(file: File): Promise<ReleaseInspectResult> {
  return request(`${BASE}/inspect`, { method: 'POST', data: form(file) });
}

export async function executeReleaseImport(
  file: File,
  confirmCode: string,
  privilegedConfirmed = false,
): Promise<ReleaseImportLog> {
  return request(`${BASE}/execute`, {
    method: 'POST',
    data: form(file, { confirmCode, privilegedConfirmed: String(privilegedConfirmed) }),
    timeout: 300000,
  });
}

export async function pageReleaseImports(params: { keyword?: string; status?: string; current?: number; pageSize?: number }) {
  const r = await request(`${BASE}/page`, {
    method: 'GET',
    params: { keyword: params.keyword, status: params.status, page: params.current || 1, size: params.pageSize || 20 },
  });
  return { data: (r?.items || []) as ReleaseImportLog[], total: r?.total || 0, success: true };
}

export async function getReleaseImport(id: string): Promise<ReleaseImportLog> {
  return request(`${BASE}/${id}`, { method: 'GET' });
}

export async function rollbackReleaseImport(id: string): Promise<ReleaseImportLog> {
  return request(`${BASE}/${id}/rollback`, { method: 'POST', timeout: 300000 });
}

/** 为缺失依赖创建停用状态的占位，返回是否实际新建 */
export async function createReleasePlaceholder(data: {
  kind: string;
  key: string;
  attributes?: Record<string, string>;
  remark?: string;
}): Promise<boolean> {
  return request(`${BASE}/placeholders`, { method: 'POST', data });
}

export async function getReleaseItemDiff(digest: string, assetType: string, key: string): Promise<FieldDiff[]> {
  return (await request(`${BASE}/diff`, { method: 'GET', params: { digest, assetType, key } })) || [];
}
