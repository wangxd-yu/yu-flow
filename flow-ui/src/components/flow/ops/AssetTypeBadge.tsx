import React from 'react';
import type { MetricsAssetType } from '@/services/flow/assetMetrics';
import './AssetTypeBadge.less';

const LABEL: Record<MetricsAssetType, string> = {
  API: '接口',
  TASK: '任务',
  SERVICE: '服务',
  PLATFORM: '开放平台',
};

export type AssetTypeBadgeProps = {
  type: MetricsAssetType | string;
  label?: string;
};

/** 资产类型轻量色标 */
const AssetTypeBadge: React.FC<AssetTypeBadgeProps> = ({ type, label }) => {
  const key = String(type || '').toUpperCase() as MetricsAssetType;
  const text = label || LABEL[key] || type;
  const cls = ['API', 'TASK', 'SERVICE', 'PLATFORM'].includes(key)
    ? `yf-type yf-type-${key.toLowerCase()}`
    : 'yf-type';
  return <span className={cls}>{text}</span>;
};

export default AssetTypeBadge;
export { LABEL as ASSET_TYPE_LABEL };
