import { request } from '@umijs/max';

/**
 * 接口 / 内部服务 / 定时任务的跨环境批量导出导入。
 *
 * 资产包保留源环境 ID（DSL 里的 serviceId、directoryId 都是 ID 引用），
 * 导入按 ID upsert 且只写草稿列，目标环境的线上快照不受影响，需再发布才生效。
 */

export type AssetTransferType = 'API' | 'SERVICE' | 'TASK' | 'DIRECTORY' | 'REGRESSION_SUITE';

export type TransferAction = 'CREATE' | 'UPDATE' | 'SKIP' | 'CONFLICT';

export type RequirementKind = 'DATASOURCE' | 'MQ_CONNECTION' | 'OSS_CONNECTION' | 'RESPONSE_TEMPLATE';

export interface TransferRequirement {
  kind: RequirementKind;
  key: string;
  /** 目标环境是否已具备，仅预检结果里有值 */
  satisfied?: boolean;
  usedBy?: string[];
}

export interface TransferItem {
  assetType: AssetTransferType;
  id?: string;
  name?: string;
  action: TransferAction;
  message?: string;
}

export interface AssetBundle {
  schemaVersion?: number;
  kind?: string;
  exportedAt?: string;
  exportedBy?: string;
  sourceEnv?: string;
  contentSource?: string;
  directories?: any[];
  apis?: any[];
  services?: any[];
  tasks?: any[];
  regressionSuites?: any[];
  requirements?: TransferRequirement[];
  warnings?: string[];
}

export interface TransferReport {
  dryRun: boolean;
  blocked: boolean;
  createCount: number;
  updateCount: number;
  skipCount: number;
  conflictCount: number;
  exportedAt?: string;
  exportedBy?: string;
  sourceEnv?: string;
  contentSource?: string;
  items: TransferItem[];
  requirements: TransferRequirement[];
  warnings: string[];
}

export interface AssetExportPayload {
  apiIds?: string[];
  serviceIds?: string[];
  taskIds?: string[];
  includeDependencies?: boolean;
  includeRegression?: boolean;
  /** PUBLISHED_FIRST（默认）| DRAFT */
  contentSource?: string;
  sourceEnv?: string;
}

export async function exportAssetBundle(data: AssetExportPayload) {
  return request<AssetBundle>('/flow-api/asset-transfer/export', {
    method: 'POST',
    data,
  });
}

export async function preflightAssetBundle(bundle: AssetBundle, overwriteExisting: boolean) {
  return request<TransferReport>('/flow-api/asset-transfer/preflight', {
    method: 'POST',
    data: { bundle, overwriteExisting },
  });
}

export async function importAssetBundle(bundle: AssetBundle, overwriteExisting: boolean) {
  return request<TransferReport>('/flow-api/asset-transfer/import', {
    method: 'POST',
    data: { bundle, overwriteExisting },
  });
}

export const REQUIREMENT_LABELS: Record<RequirementKind, string> = {
  DATASOURCE: '数据源',
  MQ_CONNECTION: 'MQ 连接',
  OSS_CONNECTION: 'OSS 连接',
  RESPONSE_TEMPLATE: '响应模板',
};

export const ASSET_TYPE_LABELS: Record<AssetTransferType, string> = {
  API: '接口',
  SERVICE: '内部服务',
  TASK: '定时任务',
  DIRECTORY: '目录',
  REGRESSION_SUITE: '回归套件',
};

export const ACTION_LABELS: Record<TransferAction, string> = {
  CREATE: '新增',
  UPDATE: '更新',
  SKIP: '跳过',
  CONFLICT: '冲突',
};

/** 触发浏览器另存为，文件名带上时间戳便于区分批次 */
export function downloadBundle(bundle: AssetBundle, filePrefix = 'yu-flow-assets') {
  const stamp = (bundle.exportedAt || '').replace(/[^0-9]/g, '').slice(0, 14)
    || new Date().toISOString().replace(/[^0-9]/g, '').slice(0, 14);
  const blob = new Blob([JSON.stringify(bundle, null, 2)], {
    type: 'application/json;charset=utf-8',
  });
  const url = URL.createObjectURL(blob);
  const link = document.createElement('a');
  link.href = url;
  link.download = `${filePrefix}-${stamp}.json`;
  document.body.appendChild(link);
  link.click();
  document.body.removeChild(link);
  URL.revokeObjectURL(url);
}
