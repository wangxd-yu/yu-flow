import { request } from '@umijs/max';
import { csrfHeaders } from '@/utils/session';

const BASE = '/flow-api/releases';

export type ReleaseStatus = 'DRAFT' | 'FROZEN' | 'EXPORTED';
export type ReleaseAssetType =
  | 'API'
  | 'SERVICE'
  | 'TASK'
  | 'MQ_TASK'
  | 'RESPONSE_TEMPLATE'
  | 'PAGE'
  | 'MODEL'
  | 'SYS_MACRO'
  | 'SYS_CONFIG'
  | 'OPEN_PLATFORM'
  | 'ALERT_RULE';

export type ReleaseItemAction = 'UPSERT' | 'OFFLINE';

/** 可放进下线清单的类型 */
export const OFFLINE_TYPES: ReleaseAssetType[] = ['API', 'SERVICE', 'TASK', 'MQ_TASK', 'PAGE', 'OPEN_PLATFORM', 'ALERT_RULE'];

export interface ReleaseItem {
  id: string;
  assetType: ReleaseAssetType;
  assetId: string;
  assetName?: string;
  /** 全局宏 / 系统配置 / 开放平台在目标环境按此编码匹配 */
  assetKey?: string;
  detail?: string;
  action: ReleaseItemAction;
  origin: 'MANUAL' | 'DEPENDENCY' | 'SCAN';
  contentHash?: string;
  exists: boolean;
  publishable: boolean;
  published: boolean;
  unpublishedChanges: boolean;
  drifted: boolean;
}

export interface Release {
  id: string;
  code: string;
  name?: string;
  status: ReleaseStatus;
  remark?: string;
  sourceEnv?: string;
  frozenBy?: string;
  frozenTime?: string;
  exportedBy?: string;
  exportedTime?: string;
  packageDigest?: string;
  createBy?: string;
  createTime?: string;
  updateTime?: string;
  itemCount: number;
  items?: ReleaseItem[];
}

export interface ReleaseCheckIssue {
  level: 'ERROR' | 'WARN';
  assetType?: ReleaseAssetType;
  assetId?: string;
  assetName?: string;
  message: string;
}

export interface ReleaseCheckResult {
  passed: boolean;
  frozen: boolean;
  issues: ReleaseCheckIssue[];
}

export interface ReleaseAssetOption {
  assetType: ReleaseAssetType;
  assetId: string;
  name: string;
  detail?: string;
  publishable: boolean;
  published: boolean;
  unpublishedChanges: boolean;
}

export const RELEASE_ASSET_LABELS: Record<ReleaseAssetType, string> = {
  API: '接口',
  SERVICE: '内部服务',
  TASK: '定时任务',
  MQ_TASK: 'MQ 任务',
  RESPONSE_TEMPLATE: '响应模板',
  PAGE: '页面',
  MODEL: '数据模型',
  SYS_MACRO: '全局宏',
  SYS_CONFIG: '系统配置',
  OPEN_PLATFORM: '开放平台',
  ALERT_RULE: '告警规则',
};

export interface ReleaseScanCandidate {
  assetType: ReleaseAssetType;
  assetId: string;
  assetKey?: string;
  name?: string;
  detail?: string;
  action: ReleaseItemAction;
  reason?: string;
  selectable: boolean;
}

export interface ReleaseScanResult {
  since: string;
  baselineCode?: string;
  candidates: ReleaseScanCandidate[];
}

export interface ReleaseCompareEntry {
  assetType: ReleaseAssetType;
  assetId: string;
  assetName?: string;
  baseAction?: ReleaseItemAction;
  targetAction?: ReleaseItemAction;
}

export interface ReleaseCompareResult {
  baseCode: string;
  targetCode: string;
  contentComparable: boolean;
  onlyInBase: ReleaseCompareEntry[];
  onlyInTarget: ReleaseCompareEntry[];
  changed: ReleaseCompareEntry[];
  unchangedCount: number;
}

export const RELEASE_STATUS: Record<ReleaseStatus, { text: string; color: string }> = {
  DRAFT: { text: '编辑中', color: 'processing' },
  FROZEN: { text: '已冻结', color: 'warning' },
  EXPORTED: { text: '已导出', color: 'success' },
};

export async function pageReleases(params: { keyword?: string; status?: string; current?: number; pageSize?: number }) {
  const r = await request(`${BASE}/page`, {
    method: 'GET',
    params: { keyword: params.keyword, status: params.status, page: params.current || 1, size: params.pageSize || 20 },
  });
  return { data: (r?.items || []) as Release[], total: r?.total || 0, success: true };
}

export async function listDraftReleases(): Promise<Release[]> {
  return (await request(`${BASE}/drafts`, { method: 'GET' })) || [];
}

export async function getRelease(id: string): Promise<Release> {
  return request(`${BASE}/${id}`, { method: 'GET' });
}

export async function createRelease(data: { code: string; name?: string; remark?: string }): Promise<Release> {
  return request(BASE, { method: 'POST', data });
}

export async function updateRelease(id: string, data: { name?: string; remark?: string }): Promise<Release> {
  return request(`${BASE}/${id}`, { method: 'PUT', data });
}

export async function deleteRelease(id: string) {
  return request(`${BASE}/${id}`, { method: 'DELETE' });
}

export async function addReleaseItems(
  id: string,
  items: { assetType: ReleaseAssetType; assetId: string }[],
  includeDependencies = true,
  origin: 'MANUAL' | 'SCAN' = 'MANUAL',
): Promise<Release> {
  return request(`${BASE}/${id}/items`, { method: 'POST', data: { items, includeDependencies, origin } });
}

export async function addOfflineItems(
  id: string,
  items: { assetType: ReleaseAssetType; assetId: string; assetName?: string; assetKey?: string }[],
): Promise<Release> {
  return request(`${BASE}/${id}/offline-items`, { method: 'POST', data: { items } });
}

export async function scanReleaseChanges(id: string, since?: string): Promise<ReleaseScanResult> {
  return request(`${BASE}/${id}/scan`, { method: 'GET', params: { since } });
}

export async function compareReleases(baseId: string, targetId: string): Promise<ReleaseCompareResult> {
  return request(`${BASE}/compare`, { method: 'GET', params: { baseId, targetId } });
}

export async function removeReleaseItem(id: string, itemId: string): Promise<Release> {
  return request(`${BASE}/${id}/items/${itemId}`, { method: 'DELETE' });
}

export async function searchReleaseAssets(assetType: ReleaseAssetType, keyword?: string): Promise<ReleaseAssetOption[]> {
  return (await request(`${BASE}/assets`, { method: 'GET', params: { assetType, keyword } })) || [];
}

export async function checkRelease(id: string): Promise<ReleaseCheckResult> {
  return request(`${BASE}/${id}/check`, { method: 'GET' });
}

export async function freezeRelease(id: string): Promise<ReleaseCheckResult> {
  return request(`${BASE}/${id}/freeze`, { method: 'POST' });
}

export async function unfreezeRelease(id: string): Promise<Release> {
  return request(`${BASE}/${id}/unfreeze`, { method: 'POST' });
}

function contextPath(): string {
  return process.env.NODE_ENV === 'production' ? (window as any).__CONTEXT_PATH__ || '' : '';
}

/** 生成并下载发布包（.yfpkg） */
export async function exportReleasePackage(id: string): Promise<void> {
  const res = await fetch(`${contextPath()}${BASE}/${id}/export`, {
    method: 'POST',
    headers: { ...csrfHeaders() },
    credentials: 'include',
  });
  if (!res.ok) {
    let msg = `导出失败 (${res.status})`;
    try {
      const j = await res.json();
      msg = j?.msg || j?.message || msg;
    } catch {
      /* 非 JSON 错误体 */
    }
    throw new Error(msg);
  }
  const cd = res.headers.get('Content-Disposition') || '';
  const m = /filename\*=UTF-8''([^;]+)|filename="?([^";]+)"?/i.exec(cd);
  const fileName = m ? decodeURIComponent(m[1] || m[2]) : `release-${id}.yfpkg`;
  const blob = await res.blob();
  const a = document.createElement('a');
  a.href = URL.createObjectURL(blob);
  a.download = fileName;
  a.click();
  URL.revokeObjectURL(a.href);
}
