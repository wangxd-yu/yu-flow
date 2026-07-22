import React from 'react';
import { Tag, Tooltip } from 'antd';
import type { AssetHealth } from '../services/assetMetrics';

const COLOR: Record<string, string> = {
  ok: 'success',
  warn: 'warning',
  error: 'error',
  empty: 'default',
};

const LABEL: Record<string, string> = {
  ok: '健康',
  warn: '告警',
  error: '异常',
  empty: '无数据',
};

export function renderHealthTag(h?: AssetHealth | null) {
  if (!h) {
    return <Tag>—</Tag>;
  }
  const key = h.health || 'empty';
  const rate =
    h.successRate == null ? '—' : `${(h.successRate * 100).toFixed(0)}%`;
  return (
    <Tooltip
      title={`近 ${h.window || '24h'}：成功率 ${rate}，调用 ${h.totalCalls}，连续失败 ${h.consecutiveFail}`}
    >
      <Tag color={COLOR[key] || 'default'}>{LABEL[key] || key}</Tag>
    </Tooltip>
  );
}
