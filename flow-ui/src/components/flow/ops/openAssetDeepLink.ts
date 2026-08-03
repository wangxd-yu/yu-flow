import { history } from '@umijs/max';
import type { MetricsAssetType } from '@/services/flow/assetMetrics';

export type OpenAssetDeepLinkOptions = {
  assetType: MetricsAssetType | string;
  assetId: string;
  /** 默认 runtime */
  tab?: string;
};

/** 跳转到资产详情并可选打开指定 Tab（如 runtime） */
export function openAssetDeepLink({
  assetType,
  assetId,
  tab = 'runtime',
}: OpenAssetDeepLinkOptions) {
  if (!assetId) return;
  const t = tab ? `&tab=${encodeURIComponent(tab)}` : '';
  const type = String(assetType || '').toUpperCase();
  if (type === 'API') {
    history.push(`/flow/api?apiId=${encodeURIComponent(assetId)}${t}`);
  } else if (type === 'TASK') {
    history.push(`/flow/task?taskId=${encodeURIComponent(assetId)}${t}`);
  } else if (type === 'MQ_TASK') {
    history.push(`/flow/mq-task?mqTaskId=${encodeURIComponent(assetId)}${t}`);
  } else if (type === 'PLATFORM') {
    history.push(`/flow/open-platform?platformId=${encodeURIComponent(assetId)}${t}`);
  } else {
    history.push(`/flow/service?serviceId=${encodeURIComponent(assetId)}${t}`);
  }
}
